package com.ktb.chatapp.service;

import com.ktb.chatapp.config.properties.S3Properties;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.util.FileUtil;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.UrlResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
@Service
public class S3FileService implements FileService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final FileRepository fileRepository;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final S3Properties properties;
    private final Path localStorageLocation;
    private final boolean useS3;

    @Autowired
    public S3FileService(S3Client s3Client,
                         S3Presigner s3Presigner,
                         FileRepository fileRepository,
                         MessageRepository messageRepository,
                         RoomRepository roomRepository,
                         S3Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.fileRepository = fileRepository;
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.properties = properties;
        this.useS3 = StringUtils.hasText(properties.getBucket());
        this.localStorageLocation = Paths.get(properties.getLocalDir()).toAbsolutePath().normalize();

        if (!useS3) {
            try {
                Files.createDirectories(localStorageLocation);
            } catch (IOException e) {
                throw new RuntimeException("로컬 업로드 디렉터리를 생성하지 못했습니다: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public FileUploadResult uploadFile(MultipartFile file, String uploaderId, String subDirectory) {
        try {
            FileUtil.validateFile(file);

            String originalFilename = Optional.ofNullable(file.getOriginalFilename())
                    .map(StringUtils::cleanPath)
                    .orElse("file");
            String mimeType = StringUtils.hasText(file.getContentType())
                    ? file.getContentType()
                    : "application/octet-stream";

            String safeFileName = FileUtil.generateSafeFileName(originalFilename);
            String objectKey;

            if (useS3) {
                objectKey = buildObjectKey(subDirectory, safeFileName);

                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .contentType(mimeType)
                        .contentLength(file.getSize())
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            } else {
                Path targetLocation = resolveLocalPath(subDirectory, safeFileName);
                Files.createDirectories(targetLocation.getParent());
                Files.copy(file.getInputStream(), targetLocation, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                objectKey = targetLocation.toString();
            }

            File fileEntity = File.builder()
                    .filename(safeFileName)
                    .originalname(FileUtil.normalizeOriginalFilename(originalFilename))
                    .mimetype(mimeType)
                    .size(file.getSize())
                    .path(objectKey)
                    .user(uploaderId)
                    .uploadDate(LocalDateTime.now())
                    .build();

            File savedFile = fileRepository.save(fileEntity);

            return FileUploadResult.builder()
                    .success(true)
                    .file(savedFile)
                    .build();
        } catch (Exception e) {
            log.error("파일 업로드 처리 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 업로드에 실패했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public FileUploadResult uploadFile(MultipartFile file, String uploaderId) {
        return uploadFile(file, uploaderId, properties.getDefaultFolder());
    }

    @Override
    public PresignedUrlResult generatePresignedGetUrl(String filename, String requesterId, boolean inline) {
        try {
            File fileEntity = fileRepository.findByFilename(filename)
                    .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다: " + filename));

            if (!useS3) {
                return createLocalPresignedResult(fileEntity, inline);
            }

            // 메시지 기반 접근 제어 (채팅방 파일)
            var messageOpt = messageRepository.findByFileId(fileEntity.getId());
            if (messageOpt.isPresent()) {
                Message message = messageOpt.get();
                Room room = roomRepository.findById(message.getRoomId())
                        .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다"));

                if (!room.getParticipantIds().contains(requesterId)) {
                    log.warn("파일 접근 권한 없음: {} (사용자: {})", filename, requesterId);
                    throw new RuntimeException("파일에 접근할 권한이 없습니다");
                }
            }

            String disposition = inline ? "inline" : "attachment";
            String original = StringUtils.hasText(fileEntity.getOriginalname())
                    ? fileEntity.getOriginalname()
                    : fileEntity.getFilename();
            String contentType = StringUtils.hasText(fileEntity.getMimetype())
                    ? fileEntity.getMimetype()
                    : "application/octet-stream";
            String encodedOriginal = URLEncoder.encode(original, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            String contentDisposition = String.format("%s; filename*=UTF-8''%s", disposition, encodedOriginal);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(fileEntity.getPath())
                    .responseContentType(contentType)
                    .responseContentDisposition(contentDisposition)
                    .build();

            var presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(properties.getPresignDuration())
                    .getObjectRequest(getObjectRequest)
                    .build();

            var presigned = s3Presigner.presignGetObject(presignRequest);

            return PresignedUrlResult.builder()
                    .url(presigned.url().toString())
                    .contentType(contentType)
                    .contentLength(fileEntity.getSize())
                    .build();
        } catch (Exception e) {
            log.error("Presigned URL 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일을 찾을 수 없거나 접근 권한이 없습니다.");
        }
    }

    @Override
    public boolean deleteFile(String fileId, String requesterId) {
        try {
            File fileEntity = fileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다."));

            if (!fileEntity.getUser().equals(requesterId)) {
                throw new RuntimeException("파일을 삭제할 권한이 없습니다.");
            }

            if (useS3) {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(fileEntity.getPath())
                        .build());
            } else {
                deleteLocalFile(fileEntity.getPath());
            }

            fileRepository.delete(fileEntity);
            log.info("파일 삭제 완료: {} (사용자: {})", fileId, requesterId);
            return true;

        } catch (Exception e) {
            log.error("파일 삭제 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 삭제 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public boolean deleteFileByFilename(String filename, String requesterId) {
        return fileRepository.findByFilename(filename)
                .map(file -> deleteFile(file.getId(), requesterId))
                .orElse(false);
    }

    @Override
    public Resource loadFileAsResource(String filename, String requesterId) {
        if (useS3) {
            throw new UnsupportedOperationException("S3 모드에서는 로컬 리소스를 직접 로드하지 않습니다.");
        }

        try {
            File fileEntity = fileRepository.findByFilename(filename)
                    .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다: " + filename));

            // 메시지 기반 접근 제어 (채팅방 파일)
            var messageOpt = messageRepository.findByFileId(fileEntity.getId());
            if (messageOpt.isPresent()) {
                Message message = messageOpt.get();
                Room room = roomRepository.findById(message.getRoomId())
                        .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다"));

                if (!room.getParticipantIds().contains(requesterId)) {
                    log.warn("파일 접근 권한 없음: {} (사용자: {})", filename, requesterId);
                    throw new RuntimeException("파일에 접근할 권한이 없습니다");
                }
            }

            Path filePath = Paths.get(fileEntity.getPath());
            FileUtil.validatePath(filePath, localStorageLocation);
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            }
            throw new RuntimeException("파일을 찾을 수 없습니다.");
        } catch (MalformedURLException e) {
            throw new RuntimeException("파일을 찾을 수 없습니다.", e);
        }
    }

    private String buildObjectKey(String subDirectory, String safeFileName) {
        String basePath = sanitizePath(properties.getBasePath());
        String subDir = sanitizePath(subDirectory);

        if (StringUtils.hasText(basePath) && StringUtils.hasText(subDir)) {
            return basePath + "/" + subDir + "/" + safeFileName;
        }
        if (StringUtils.hasText(basePath)) {
            return basePath + "/" + safeFileName;
        }
        if (StringUtils.hasText(subDir)) {
            return subDir + "/" + safeFileName;
        }
        return safeFileName;
    }

    private String sanitizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        return path.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private PresignedUrlResult createLocalPresignedResult(File fileEntity, boolean inline) {
        String disposition = inline ? "inline" : "attachment";
        String original = StringUtils.hasText(fileEntity.getOriginalname())
                ? fileEntity.getOriginalname()
                : fileEntity.getFilename();
        String encodedOriginal = URLEncoder.encode(original, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        String url = "/api/files/raw/" + fileEntity.getFilename();
        String contentType = StringUtils.hasText(fileEntity.getMimetype())
                ? fileEntity.getMimetype()
                : "application/octet-stream";

        return PresignedUrlResult.builder()
                .url(url + "?disposition=" + disposition + "&filename=" + encodedOriginal)
                .contentType(contentType)
                .contentLength(fileEntity.getSize())
                .build();
    }

    private Path resolveLocalPath(String subDirectory, String safeFileName) {
        if (StringUtils.hasText(subDirectory)) {
            return localStorageLocation.resolve(subDirectory).resolve(safeFileName).normalize();
        }
        return localStorageLocation.resolve(safeFileName).normalize();
    }

    private void deleteLocalFile(String path) {
        try {
            if (!StringUtils.hasText(path)) {
                return;
            }
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.warn("로컬 파일 삭제 실패: {}", e.getMessage());
        }
    }

    /**
     * S3 public URL 생성
     */
    public String getS3PublicUrl(String filename) {
        File fileEntity = fileRepository.findByFilename(filename)
                .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다: " + filename));

        if (!useS3) {
            // 로컬 모드일 경우 기존 방식 사용
            return "/api/files/view/" + filename;
        }

        // S3 public URL 생성
        String objectKey = fileEntity.getPath();
        String endpointUrl = properties.getEndpointOverride();

        if (StringUtils.hasText(endpointUrl)) {
            // Custom endpoint (LocalStack/MinIO)
            return String.format("%s/%s/%s", endpointUrl, properties.getBucket(), objectKey);
        } else {
            // AWS S3 standard URL
            return String.format("https://%s.s3.%s.amazonaws.com/%s",
                    properties.getBucket(),
                    properties.getRegion(),
                    objectKey);
        }
    }
}
