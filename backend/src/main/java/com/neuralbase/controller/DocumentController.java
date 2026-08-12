package com.neuralbase.controller;

import com.neuralbase.common.Result;
import com.neuralbase.entity.Document;
import com.neuralbase.service.DocumentService;
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

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Document> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "kbId", required = false) Long kbId) throws IOException {
        return Result.ok(documentService.upload(file, kbId));
    }

    @GetMapping
    public Result<List<Document>> list(@RequestParam(value = "kbId", required = false) Long kbId) {
        return Result.ok(documentService.list(kbId));
    }
}
