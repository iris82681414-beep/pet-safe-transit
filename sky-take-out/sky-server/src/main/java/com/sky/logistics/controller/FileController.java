package com.sky.logistics.controller;

import com.sky.logistics.common.ApiResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@Api(tags = "智慧物流-文件上传")
public class FileController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private final Path uploadRoot = Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();

    @PostMapping("/upload")
    @ApiOperation("上传图片文件")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            throw new IllegalArgumentException("当前接口只允许上传图片文件");
        }

        String dateDir = LocalDate.now().format(DATE_FORMATTER);
        Path targetDir = uploadRoot.resolve(dateDir).normalize();
        Files.createDirectories(targetDir);

        String originalName = file.getOriginalFilename() == null ? "image" : Paths.get(file.getOriginalFilename()).getFileName().toString();
        String extension = extensionOf(originalName, contentType);
        String storedName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = targetDir.resolve(storedName).normalize();
        if (!target.startsWith(targetDir)) {
            throw new IllegalArgumentException("非法文件名");
        }
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", "/api/v1/files/" + dateDir + "/" + storedName);
        data.put("fileName", storedName);
        data.put("originalName", originalName);
        data.put("size", file.getSize());
        data.put("contentType", contentType);
        return ApiResponse.success(data);
    }

    @GetMapping("/{dateDir}/{fileName:.+}")
    @ApiOperation("访问已上传图片")
    public ResponseEntity<Resource> read(@PathVariable String dateDir, @PathVariable String fileName) throws MalformedURLException {
        Path file = uploadRoot.resolve(dateDir).resolve(fileName).normalize();
        if (!file.startsWith(uploadRoot) || !Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(file.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentTypeOf(fileName)))
                .body(resource);
    }

    private String extensionOf(String originalName, String contentType) {
        int index = originalName.lastIndexOf('.');
        if (index >= 0 && index < originalName.length() - 1) {
            String extension = originalName.substring(index).toLowerCase(Locale.ROOT);
            if (extension.matches("\\.(jpg|jpeg|png|gif|webp|bmp)")) return extension;
        }
        if ("image/png".equals(contentType)) return ".png";
        if ("image/gif".equals(contentType)) return ".gif";
        if ("image/webp".equals(contentType)) return ".webp";
        return ".jpg";
    }

    private String contentTypeOf(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (name.endsWith(".gif")) return MediaType.IMAGE_GIF_VALUE;
        if (name.endsWith(".webp")) return "image/webp";
        return MediaType.IMAGE_JPEG_VALUE;
    }
}
