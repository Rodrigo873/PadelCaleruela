package com.example.PadelCaleruela.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    public String save(MultipartFile file, String folder) throws IOException {

        String uploadDir = "uploads/" + folder + "/";
        Files.createDirectories(Paths.get(uploadDir));

        String filename = UUID.randomUUID() + "-" + file.getOriginalFilename();
        Path path = Paths.get(uploadDir + filename);

        Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

        // Devuelve solo la ruta relativa
        return "/uploads/" + folder + "/" + filename;
    }
}

