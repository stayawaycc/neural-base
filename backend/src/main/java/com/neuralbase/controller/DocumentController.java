package com.neuralbase.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neuralbase.common.Result;
import com.neuralbase.entity.Document;
import com.neuralbase.mapper.DocumentMapper;
import com.neuralbase.service.FileStorageService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final DocumentMapper documentMapper;
    private final FileStorageService fileStorageService;

    public DocumentController(DocumentMapper documentMapper, FileStorageService fileStorageService) {
        this.documentMapper = documentMapper;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Document> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "kbId", required = false) Long kbId) throws IOException {
        String path = fileStorageService.store(file);
        String original = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setTitle(original);
        doc.setFileType(extension(original));
        doc.setFilePath(path);
        doc.setStatus("PENDING");
        documentMapper.insert(doc);
        return Result.ok(doc);
    }

    @GetMapping
    public Result<List<Document>> list(@RequestParam(value = "kbId", required = false) Long kbId) {
        LambdaQueryWrapper<Document> w = new LambdaQueryWrapper<>();
        if (kbId != null) {
            w.eq(Document::getKbId, kbId);
        }
        w.orderByDesc(Document::getCreatedAt);
        return Result.ok(documentMapper.selectList(w));
    }

    private String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }
}
