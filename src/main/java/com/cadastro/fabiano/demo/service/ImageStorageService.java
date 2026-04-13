package com.cadastro.fabiano.demo.service;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {
    String store(MultipartFile file) throws Exception;
    void delete(String fileUrl);
}