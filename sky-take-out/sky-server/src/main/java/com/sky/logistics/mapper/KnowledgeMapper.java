package com.sky.logistics.mapper;

import com.sky.logistics.entity.KnowledgeChunk;
import com.sky.logistics.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeMapper {

    int insertDocument(KnowledgeDocument document);

    List<KnowledgeDocument> findDocuments(@Param("title") String title,
                                          @Param("category") String category,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    Long countDocuments(@Param("title") String title,
                        @Param("category") String category);

    KnowledgeDocument findDocumentById(@Param("documentId") String documentId);

    int updateDocumentStatus(@Param("documentId") String documentId,
                             @Param("status") String status,
                             @Param("chunkCount") Integer chunkCount);

    int deleteDocument(@Param("documentId") String documentId);

    int insertChunk(KnowledgeChunk chunk);

    int deleteChunksByDocumentId(@Param("documentId") String documentId);

    List<KnowledgeChunk> findChunksByDocumentId(@Param("documentId") String documentId);

    List<KnowledgeChunk> findSimilarChunksByVector(@Param("embedding") String embedding,
                                                     @Param("limit") int limit);

    List<KnowledgeChunk> findChunksByKeyword(@Param("keywords") List<String> keywords,
                                              @Param("limit") int limit);
}
