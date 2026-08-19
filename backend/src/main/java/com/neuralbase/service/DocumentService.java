package com.neuralbase.service;

import com.neuralbase.dto.DocumentContentView;
import com.neuralbase.dto.SearchResult;
import com.neuralbase.entity.Document;
import com.neuralbase.entity.Kb;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.mapper.KbMapper;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final double minScore;

    public DocumentService(DocumentMapper documentMapper, FileStorageService fileStorageService,
                           TextExtractorService textExtractorService, TextChunker textChunker,
                           VectorStore vectorStore, JdbcTemplate jdbcTemplate, KbMapper kbMapper,
                           @Value("${neuralbase.ai.search-min-score:0.5}") double minScore) {
        this.documentMapper = documentMapper;
        this.fileStorageService = fileStorageService;
        this.textExtractorService = textExtractorService;
        this.textChunker = textChunker;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.kbMapper = kbMapper;
        this.minScore = minScore;
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
        log.info("上传文档 id={} title={} size={} kbId={}", doc.getId(), doc.getTitle(), file.getSize(), kbId);
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
            return; // 文档已被删除(或不存在),放弃入库
        }
        doc.setStatus("PROCESSING");
        documentMapper.updateById(doc);
        try (var in = Files.newInputStream(Path.of(doc.getFilePath()))) {
            String text = textExtractorService.extract(in, doc.getTitle());
            // 竞态防护:处理期间文档被删除,复查存在性,避免幽灵向量重新入库(不可删且 RAG 命中 title 为 null)
            if (documentMapper.selectById(documentId) == null) {
                return; // 文档已被删除,放弃入库
            }
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
            doc.setContent(text);
            documentMapper.updateById(doc);
            log.info("文档处理完成 id={} title={} chunks={}", doc.getId(), doc.getTitle(), chunks.size());
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
        log.info("删除文档 id={} title={} kbId={}", documentId, doc.getTitle(), doc.getKbId());
    }

    public DocumentContentView content(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        if (!"READY".equals(doc.getStatus())) {
            throw new IllegalArgumentException("FAILED".equals(doc.getStatus())
                ? "文档处理失败: " + (doc.getError() == null ? "未知原因" : doc.getError())
                : "文档尚未处理完成");
        }
        if (doc.getContent() == null || doc.getContent().isBlank()) {
            // V5 之前的存量文档:READY 但 content 为空 → 从原文件惰性回填(自愈,免用户重传)
            doc = backfillLegacyContent(doc);
        }
        if (doc.getContent() == null || doc.getContent().isBlank()) {
            throw new IllegalArgumentException("文档内容缺失,请重新上传");
        }
        return new DocumentContentView(doc.getId(), doc.getTitle(), doc.getFileType(), doc.getContent());
    }

    /** 文本直入知识库的通用管线(八股收藏/新闻收藏共用):切片 → 建 READY Document → 幂等替换向量。返回切片数。 */
    public int createEmbeddedDocument(Long kbId, String title, String fileType, String sourceUrl, String text) {
        List<String> chunks = textChunker.chunk(text);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("文本切片为空");
        }
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setTitle(title);
        doc.setFileType(fileType);
        doc.setSourceUrl(sourceUrl);
        doc.setStatus("READY");
        doc.setChunkCount(chunks.size());
        doc.setContent(text);
        documentMapper.insert(doc);
        jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'docId' = ?",
            String.valueOf(doc.getId()));
        List<org.springframework.ai.document.Document> aiDocs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("docId", doc.getId());
            metadata.put("kbId", kbId);
            metadata.put("chunkIndex", i);
            aiDocs.add(new org.springframework.ai.document.Document(chunks.get(i), metadata));
        }
        if (!aiDocs.isEmpty()) {
            vectorStore.add(aiDocs);
        }
        return chunks.size();
    }

    /** 存量文档回填:原文件仍在磁盘则重新提取全文并落库,否则原样返回。 */
    private Document backfillLegacyContent(Document doc) {
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            return doc; // 无原文件(如 V5 之前的收藏文档),无法回填
        }
        Path path = Path.of(doc.getFilePath());
        if (!Files.exists(path)) {
            return doc;
        }
        try (var in = Files.newInputStream(path)) {
            String text = textExtractorService.extract(in, doc.getTitle());
            if (!text.isBlank()) {
                doc.setContent(text);
                documentMapper.updateById(doc);
            }
        } catch (Exception e) {
            log.warn("存量文档内容回填失败 {}: {}", doc.getId(), e.getMessage());
        }
        return doc;
    }

    public Document fileDocument(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            throw new IllegalArgumentException("该文档无原文件");
        }
        return doc;
    }

    public List<SearchResult> search(String query, Long kbId, Integer topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        int k = topK == null ? 5 : topK;
        if (k < 1 || k > 20) {
            throw new IllegalArgumentException("topK 必须在 1~20 之间");
        }
        long start = System.currentTimeMillis();
        org.springframework.ai.vectorstore.SearchRequest.Builder builder =
            org.springframework.ai.vectorstore.SearchRequest.builder().query(query).topK(k * 4);
        if (kbId != null) {
            builder.filterExpression("kbId == " + kbId);
        }
        var matches = vectorStore.similaritySearch(builder.build());

        // 同一文档只保留相似度最高的片段(去重)
        Map<Long, org.springframework.ai.document.Document> bestByDoc = new LinkedHashMap<>();
        for (var d : matches) {
            Object rawDocId = d.getMetadata().get("docId");
            if (!(rawDocId instanceof Number n)) {
                continue;
            }
            Long docId = n.longValue();
            org.springframework.ai.document.Document prev = bestByDoc.get(docId);
            if (prev == null || d.getScore() > prev.getScore()) {
                bestByDoc.put(docId, d);
            }
        }
        List<Long> docIds = new ArrayList<>(bestByDoc.keySet());
        Map<Long, Document> docs = docIds.isEmpty() ? Map.of()
            : documentMapper.selectBatchIds(docIds).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        Map<Long, Kb> kbs = docs.isEmpty() ? Map.of()
            : kbMapper.selectBatchIds(docs.values().stream().map(Document::getKbId).filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Kb::getId, Function.identity()));

        List<SearchResult> results = bestByDoc.values().stream()
            .filter(d -> d.getScore() != null && d.getScore() >= minScore)
            .sorted((a, b) -> Double.compare(b.getScore() == null ? -1 : b.getScore(),
                a.getScore() == null ? -1 : a.getScore()))
            .limit(k)
            .map(d -> {
                Long docId = ((Number) d.getMetadata().get("docId")).longValue();
                Document doc = docs.get(docId);
                Kb kb = doc != null && doc.getKbId() != null ? kbs.get(doc.getKbId()) : null;
                return new SearchResult(docId, doc == null ? null : doc.getTitle(), d.getText(), d.getScore(),
                    kb == null ? null : kb.getName());
            })
            .toList();
        log.info("搜索完成 query=[{}] kbId={} 候选={} 命中={} 耗时={}ms", query, kbId, matches.size(), results.size(),
            System.currentTimeMillis() - start);
        return results;
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
