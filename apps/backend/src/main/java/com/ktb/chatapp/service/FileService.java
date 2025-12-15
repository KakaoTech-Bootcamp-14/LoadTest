package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.FileUploadInitRequest;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    FileUploadResult initiateUpload(FileUploadInitRequest request, String uploaderId);

    FileUploadResult initiateUpload(FileUploadInitRequest request, String uploaderId, String subDirectory);

    FileUploadResult uploadFile(MultipartFile file, String uploaderId);

    FileUploadResult uploadFile(MultipartFile file, String uploaderId, String subDirectory);

    PresignedUrlResult generatePresignedGetUrl(String filename, String requesterId, boolean inline);

    Resource loadFileAsResource(String filename, String requesterId);

    boolean deleteFile(String fileId, String requesterId);

    boolean deleteFileByFilename(String filename, String requesterId);
}
