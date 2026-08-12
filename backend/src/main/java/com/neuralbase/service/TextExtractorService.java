package com.neuralbase.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class TextExtractorService {

    private final Tika tika = new Tika();

    public String extract(InputStream in, String filename) throws IOException, TikaException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        return tika.parseToString(in, metadata);
    }
}
