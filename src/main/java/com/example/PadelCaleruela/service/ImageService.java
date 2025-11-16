package com.example.PadelCaleruela.service;

import com.example.PadelCaleruela.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final AppProperties props;

    public String buildUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank())
            return null;

        // Garantiza que nunca se devuelva 192.168.x.x
        return props.getBaseUrl() + relativePath;
    }
}