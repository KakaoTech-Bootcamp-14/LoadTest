package com.ktb.chatapp.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    FileUploadResult uploadFile(MultipartFile file, String uploaderId);

    FileUploadResult uploadFile(MultipartFile file, String uploaderId, String subDirectory);

    PresignedUrlResult generatePresignedGetUrl(String filename, String requesterId, boolean inline);

    boolean deleteFile(String fileId, String requesterId);

    boolean deleteFileByFilename(String filename, String requesterId);
}
