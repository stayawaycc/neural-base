package com.neuralbase.controller;

import com.neuralbase.common.Result;
import com.neuralbase.dto.SearchQuery;
import com.neuralbase.dto.SearchResult;
import com.neuralbase.entity.Document;
import com.neuralbase.service.DocumentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        Document doc = documentService.upload(file, kbId);
        documentService.processAsync(doc.getId());
        return Result.ok(doc);
    }

    @PostMapping("/search")
    public Result<List<SearchResult>> search(@RequestBody SearchQuery query) {
        return Result.ok(documentService.search(query.query(), query.kbId(), query.topK()));
    }

    @GetMapping
    public Result<List<Document>> list(@RequestParam(value = "kbId", required = false) Long kbId) {
        return Result.ok(documentService.list(kbId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.ok(null);
    }
}
