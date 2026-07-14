package com.sky.logistics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Markdown 语义分割器：保留标题上下文，并根据相邻内容的向量相似度选择断点。
 */
@Component
@Slf4j
public class SemanticMarkdownChunker {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^(?:[-*+]\\s+|\\d+[.)]\\s+).+");

    private final EmbeddingService embeddingService;
    private final int minChunkSize;
    private final int maxChunkSize;
    private final double breakpointPercentile;

    public SemanticMarkdownChunker(
            EmbeddingService embeddingService,
            @Value("${knowledge.chunking.min-size:280}") int minChunkSize,
            @Value("${knowledge.chunking.max-size:1000}") int maxChunkSize,
            @Value("${knowledge.chunking.breakpoint-percentile:30}") double breakpointPercentile) {
        this.embeddingService = embeddingService;
        this.minChunkSize = Math.max(1, minChunkSize);
        this.maxChunkSize = Math.max(this.minChunkSize, maxChunkSize);
        this.breakpointPercentile = Math.max(0, Math.min(100, breakpointPercentile));
    }

    public List<String> split(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<SemanticUnit> units = parseUnits(normalize(markdown));
        if (units.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> embeddingInputs = units.stream()
                .map(SemanticUnit::embeddingText)
                .collect(Collectors.toList());
        List<List<Double>> embeddings = embeddingService.embedBatch(embeddingInputs);
        double[] similarities = adjacentSimilarities(units, embeddings);
        double breakpoint = calculateBreakpoint(similarities);

        List<String> chunks = mergeUnits(units, similarities, breakpoint);
        log.info("Markdown 语义分割完成, 语义单元数={}, chunk数={}, 相似度断点={}",
                units.size(), chunks.size(), Double.isNaN(breakpoint) ? "不可用（按结构与长度分割）" : String.format("%.4f", breakpoint));
        return chunks;
    }

    private String normalize(String markdown) {
        return markdown.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    private List<SemanticUnit> parseUnits(String markdown) {
        List<SemanticUnit> units = new ArrayList<>();
        String[] headings = new String[6];
        StringBuilder paragraph = new StringBuilder();
        boolean[] hardBoundaryPending = {false};

        for (String rawLine : markdown.split("\n")) {
            String line = rawLine.trim();
            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                flushParagraph(units, headings, paragraph, hardBoundaryPending);
                int level = headingMatcher.group(1).length();
                headings[level - 1] = headingMatcher.group(2).trim();
                Arrays.fill(headings, level, headings.length, null);
                if (level <= 2) {
                    hardBoundaryPending[0] = true;
                }
                continue;
            }

            if (line.isEmpty() || "---".equals(line)) {
                flushParagraph(units, headings, paragraph, hardBoundaryPending);
                continue;
            }

            if (LIST_ITEM_PATTERN.matcher(line).matches()) {
                flushParagraph(units, headings, paragraph, hardBoundaryPending);
                addUnit(units, headings, line, hardBoundaryPending);
                continue;
            }

            if (paragraph.length() > 0) {
                paragraph.append('\n');
            }
            paragraph.append(line);
        }
        flushParagraph(units, headings, paragraph, hardBoundaryPending);
        return units;
    }

    private void flushParagraph(List<SemanticUnit> units, String[] headings, StringBuilder paragraph,
                                boolean[] hardBoundaryPending) {
        if (paragraph.length() == 0) {
            return;
        }
        addUnit(units, headings, paragraph.toString(), hardBoundaryPending);
        paragraph.setLength(0);
    }

    private void addUnit(List<SemanticUnit> units, String[] headings, String body,
                         boolean[] hardBoundaryPending) {
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        List<String> parts = splitOversizedBody(trimmed, headingLength(headings));
        for (int i = 0; i < parts.size(); i++) {
            units.add(new SemanticUnit(Arrays.copyOf(headings, headings.length), parts.get(i),
                    hardBoundaryPending[0] && i == 0));
        }
        hardBoundaryPending[0] = false;
    }

    private List<String> splitOversizedBody(String body, int headingLength) {
        int available = Math.max(100, maxChunkSize - headingLength - 2);
        if (body.length() <= available) {
            return Collections.singletonList(body);
        }

        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < body.length()) {
            int end = Math.min(start + available, body.length());
            if (end < body.length()) {
                int sentenceEnd = lastSentenceBoundary(body, start, end);
                if (sentenceEnd > start + Math.min(100, available / 2)) {
                    end = sentenceEnd;
                }
            }
            parts.add(body.substring(start, end).trim());
            start = end;
        }
        return parts;
    }

    private int lastSentenceBoundary(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == ';' || c == '；' || c == '\n') {
                return i + 1;
            }
        }
        return end;
    }

    private int headingLength(String[] headings) {
        int length = 0;
        for (int i = 0; i < headings.length; i++) {
            if (headings[i] != null) {
                length += i + 1 + 1 + headings[i].length() + 1;
            }
        }
        return length;
    }

    private double[] adjacentSimilarities(List<SemanticUnit> units, List<List<Double>> embeddings) {
        double[] similarities = new double[units.size()];
        Arrays.fill(similarities, Double.NaN);
        if (embeddings == null || embeddings.size() != units.size()) {
            return similarities;
        }

        for (int i = 1; i < units.size(); i++) {
            if (!units.get(i).hardBoundaryBefore) {
                similarities[i] = cosineSimilarity(embeddings.get(i - 1), embeddings.get(i));
            }
        }
        return similarities;
    }

    private double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || left.size() != right.size()) {
            return Double.NaN;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return Double.NaN;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double calculateBreakpoint(double[] similarities) {
        List<Double> valid = Arrays.stream(similarities)
                .filter(value -> !Double.isNaN(value))
                .sorted()
                .boxed()
                .collect(Collectors.toList());
        if (valid.isEmpty()) {
            return Double.NaN;
        }
        int index = (int) Math.floor((valid.size() - 1) * breakpointPercentile / 100.0);
        return valid.get(index);
    }

    private List<String> mergeUnits(List<SemanticUnit> units, double[] similarities, double breakpoint) {
        List<String> result = new ArrayList<>();
        List<SemanticUnit> current = new ArrayList<>();
        boolean currentStartsAtSemanticBoundary = false;

        for (int i = 0; i < units.size(); i++) {
            SemanticUnit unit = units.get(i);
            if (current.isEmpty()) {
                current.add(unit);
                continue;
            }

            String currentText = render(current);
            List<SemanticUnit> candidate = new ArrayList<>(current);
            candidate.add(unit);
            boolean hardBoundary = unit.hardBoundaryBefore;
            boolean exceedsMaximum = render(candidate).length() > maxChunkSize;
            boolean semanticBoundary = currentText.length() >= minChunkSize
                    && !Double.isNaN(breakpoint)
                    && !Double.isNaN(similarities[i])
                    && similarities[i] <= breakpoint;

            if (hardBoundary || exceedsMaximum || semanticBoundary) {
                result.add(currentText);
                current = new ArrayList<>();
                currentStartsAtSemanticBoundary = semanticBoundary;
            }
            current.add(unit);
        }

        if (!current.isEmpty()) {
            String tail = render(current);
            if (!result.isEmpty() && tail.length() < minChunkSize
                    && result.get(result.size() - 1).length() + tail.length() + 2 <= maxChunkSize
                    && !current.get(0).hardBoundaryBefore
                    && !currentStartsAtSemanticBoundary) {
                int last = result.size() - 1;
                result.set(last, result.get(last) + "\n\n" + tail);
            } else {
                result.add(tail);
            }
        }
        return result;
    }

    private String render(List<SemanticUnit> units) {
        StringBuilder out = new StringBuilder();
        String[] previousHeadings = new String[6];

        for (SemanticUnit unit : units) {
            int firstChangedLevel = firstChangedLevel(previousHeadings, unit.headings);
            for (int level = firstChangedLevel; level < unit.headings.length; level++) {
                String heading = unit.headings[level];
                if (heading != null) {
                    appendBlock(out, repeat('#', level + 1) + " " + heading);
                }
            }
            appendBlock(out, unit.body);
            previousHeadings = Arrays.copyOf(unit.headings, unit.headings.length);
        }
        return out.toString().trim();
    }

    private int firstChangedLevel(String[] previous, String[] current) {
        for (int i = 0; i < current.length; i++) {
            if (!java.util.Objects.equals(previous[i], current[i])) {
                return i;
            }
        }
        return current.length;
    }

    private void appendBlock(StringBuilder out, String block) {
        if (out.length() > 0) {
            out.append("\n\n");
        }
        out.append(block);
    }

    private String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private static class SemanticUnit {
        private final String[] headings;
        private final String body;
        private final boolean hardBoundaryBefore;

        private SemanticUnit(String[] headings, String body, boolean hardBoundaryBefore) {
            this.headings = headings;
            this.body = body;
            this.hardBoundaryBefore = hardBoundaryBefore;
        }

        private String embeddingText() {
            StringBuilder text = new StringBuilder();
            for (String heading : headings) {
                if (heading != null) {
                    if (text.length() > 0) text.append(" > ");
                    text.append(heading);
                }
            }
            if (text.length() > 0) text.append("\n");
            text.append(body);
            return text.toString();
        }
    }
}
