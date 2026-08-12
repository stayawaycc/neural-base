package com.neuralbase.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    private final int maxChunkChars;
    private final int overlapChars;

    public TextChunker(@Value("${neuralbase.chunker.max-chars:2000}") int maxChunkChars,
                       @Value("${neuralbase.chunker.overlap-chars:300}") int overlapChars) {
        // seed 会追加 overlapChars 字符 + 1 换行，必须留至少 1 字符余量，否则死循环/越界
        if (maxChunkChars <= 0 || overlapChars <= 0 || overlapChars >= maxChunkChars - 1) {
            throw new IllegalArgumentException(
                "参数非法: 需 maxChunkChars > 0 且 0 < overlapChars < maxChunkChars - 1");
        }
        this.maxChunkChars = maxChunkChars;
        this.overlapChars = overlapChars;
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String raw : text.split("\\n{2,}")) {
            String p = raw.strip();
            if (p.isEmpty()) {
                continue;
            }
            List<String> pieces = splitLong(p);
            int i = 0;
            while (i < pieces.size()) {
                String piece = pieces.get(i);
                if (!current.isEmpty() && current.length() + piece.length() > maxChunkChars) {
                    String prev = current.toString().strip();
                    chunks.add(prev);
                    current.setLength(0);
                    if (prev.length() >= overlapChars) {
                        current.append(prev, prev.length() - overlapChars, prev.length()).append('\n');
                    }
                }
                int room = maxChunkChars - current.length();
                if (piece.length() <= room) {
                    current.append(piece).append('\n');
                    i++;
                } else {
                    current.append(piece, 0, room).append('\n');
                    pieces.set(i, piece.substring(room));
                }
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }

    private List<String> splitLong(String paragraph) {
        if (paragraph.length() <= maxChunkChars) {
            return List.of(paragraph);
        }
        List<String> pieces = new ArrayList<>();
        int i = 0;
        while (i < paragraph.length()) {
            pieces.add(paragraph.substring(i, Math.min(i + maxChunkChars, paragraph.length())));
            i += maxChunkChars;
        }
        return pieces;
    }
}
