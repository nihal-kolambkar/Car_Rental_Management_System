package com.veloce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private final Path fileStorageLocation;

    public LocalStorageService(@Value("${file.upload-dir:uploads/licenses/}") String uploadDir) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @Override
    public String storeFile(MultipartFile file) {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document");
        
        // Prevent path traversal
        if (originalFileName.contains("..")) {
            throw new RuntimeException("Filename contains invalid path sequence: " + originalFileName);
        }

        String fileExtension = "";
        if (originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }

        // Clean base name to alphanumeric + safe chars
        String baseName = originalFileName.replace(fileExtension, "").replaceAll("[^a-zA-Z0-9_-]", "_");
        if (baseName.length() > 30) {
            baseName = baseName.substring(0, 30);
        }

        // Secure unique filename: UUID + sanitized original basename + extension
        String storedFileName = UUID.randomUUID().toString() + "-" + baseName + fileExtension.toLowerCase();

        try {
            Path targetLocation = this.fileStorageLocation.resolve(storedFileName).normalize();
            
            // Security verification: ensure target stays inside designated storage dir
            if (!targetLocation.startsWith(this.fileStorageLocation)) {
                throw new RuntimeException("Cannot store file outside current directory.");
            }

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return storedFileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + storedFileName + ". Please try again.", ex);
        }
    }

    @Override
    public Resource loadFileAsResource(String storedFileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(storedFileName).normalize();

            // Security check: ensure path is within the designated upload directory
            if (!filePath.startsWith(this.fileStorageLocation)) {
                throw new RuntimeException("Access to path outside upload directory is strictly prohibited.");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable: " + storedFileName);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("File not found: " + storedFileName, ex);
        }
    }

    @Override
    public void deleteFile(String storedFileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(storedFileName).normalize();
            if (filePath.startsWith(this.fileStorageLocation)) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException ignored) {
        }
    }
}
