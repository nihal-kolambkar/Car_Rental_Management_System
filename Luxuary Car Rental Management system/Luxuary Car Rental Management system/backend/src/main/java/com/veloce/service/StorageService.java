package com.veloce.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /**
     * Stores a file safely and returns the unique stored file name.
     */
    String storeFile(MultipartFile file);

    /**
     * Loads a file as a Spring Resource for secure streaming.
     */
    Resource loadFileAsResource(String storedFileName);

    /**
     * Deletes a stored file.
     */
    void deleteFile(String storedFileName);
}
