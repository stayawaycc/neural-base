package com.neuralbase.service;

import com.neuralbase.dto.SearchResult;
import com.neuralbase.entity.Document;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.mapper.KbMapper;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentMapper documentMapper;
    private final FileStorageService fileStorageService;
    private final TextExtractorService textExtractorService;
    private final TextChunker textChunker;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final KbMapper kbMapper;

    public DocumentService(DocumentMapper documentMapper, FileStorageService fileStorageService,
                           TextExtractorService textExtractorService, TextChunker textChunker,
                           VectorStore vectorStore, JdbcTemplate jdbcTemplate, KbMapper kbMapper) {
        this.documentMapper = documentMapper;
        this.fileStorageService = fileStorageService;
        this.textExtractorService = textExtractorService;
        this.textChunker = textChunker;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.kbMapper = kbMapper;
    }

    public Document upload(MultipartFile file, Long kbId) throws IOException {
        if (kbId != null && kbMapper.selectById(kbId) == null) {
            throw new IllegalArgumentException("知识库不存在: " + kbId);
        }
        String path = fileStorageService.store(file);
        String original = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setTitle(original);
        doc.setFileType(extension(original));
        doc.setFilePath(path);
        doc.setStatus("PENDING");
        documentMapper.insert(doc);
        return doc;
    }

    @Async
    public void processAsync(Long documentId) {
        try {
            process(documentId);
        } catch (Exception e) {
            Document doc = documentMapper.selectById(documentId);
            if (doc != null) {
                doc.setStatus("FAILED");
                doc.setError(e.getMessage());
                documentMapper.updateById(doc);
            }
        }
    }

    public void process(Long documentId) throws IOException, TikaException {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        doc.setStatus("PROCESSING");
        documentMapper.updateById(doc);
        try (var in = Files.newInputStream(Path.of(doc.getFilePath()))) {
            String text = textExtractorService.extract(in, doc.getTitle());
            List<String> chunks = textChunker.chunk(text);
            List<org.springframework.ai.document.Document> docs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("docId", doc.getId());
                metadata.put("kbId", doc.getKbId());
                metadata.put("chunkIndex", i);
                docs.add(new org.springframework.ai.document.Document(chunks.get(i), metadata));
            }
            if (!docs.isEmpty()) {
                jdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'docId' = ?",
                    String.valueOf(doc.getId()));
                vectorStore.add(docs);
            }
            doc.setStatus("READY");
            doc.setChunkCount(chunks.size());
            documentMapper.updateById(doc);
        }
    }

    public void delete(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        jdbcTemplate.update(
            "DELETE FROM vector_store WHERE metadata->>'docId' = ?",
            String.valueOf(documentId));
        if (doc.getFilePath() != null && !doc.getFilePath().isBlank()) {
            try {
                Files.deleteIfExists(Path.of(doc.getFilePath()));
            } catch (IOException e) {
                log.warn("删除文档文件失败 {}: {}", doc.getFilePath(), e.getMessage());
            }
        }
        documentMapper.deleteById(documentId);
    }

    public List<SearchResult> search(String query, Long kbId, Integer topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        int k = topK == null ? 5 : topK;
        if (k < 1 || k > 20) {
            throw new IllegalArgumentException("topK 必须在 1~20 之间");
        }
        org.springframework.ai.vectorstore.SearchRequest.Builder builder =
            org.springframework.ai.vectorstore.SearchRequest.builder().query(query).topK(k);
        if (kbId != null) {
            builder.filterExpression("kbId == " + kbId);
        }
        var matches = vectorStore.similaritySearch(builder.build());
        return matches.stream().map(d -> {
            Object rawDocId = d.getMetadata().get("docId");
            Long docId = rawDocId instanceof Number n ? n.longValue() : null;
            Document doc = docId == null ? null : documentMapper.selectById(docId);
            return new SearchResult(docId, doc == null ? null : doc.getTitle(), d.getText(), d.getScore());
        }).toList();
    }

    public List<Document> list(Long kbId) {
        return documentMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
            .eq(kbId != null, Document::getKbId, kbId)
            .orderByDesc(Document::getCreatedAt));
    }

    private String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }
}
