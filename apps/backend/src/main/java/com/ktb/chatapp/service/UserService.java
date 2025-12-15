package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.FileUploadInitRequest;
import com.ktb.chatapp.dto.ProfileImageResponse;
import com.ktb.chatapp.dto.UpdateProfileRequest;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final FileService fileService;

    @Value("${app.profile.image.max-size:5242880}") // 5MB
    private long maxProfileImageSize;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp"
    );

    /**
     * 현재 사용자 프로필 조회
     * @param email 사용자 이메일
     */
    public UserResponse getCurrentUserProfile(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }

    /**
     * 사용자 프로필 업데이트
     * @param email 사용자 이메일
     */
    public UserResponse updateUserProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        // 프로필 정보 업데이트
        user.setName(request.getName());
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        log.info("사용자 프로필 업데이트 완료 - ID: {}, Name: {}", user.getId(), request.getName());

        return UserResponse.from(updatedUser);
    }

    /**
     * 프로필 이미지 업로드
     * @param email 사용자 이메일
     */
    public ProfileImageResponse uploadProfileImage(String email, MultipartFile file) {
        // 사용자 조회
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        // 파일 유효성 검증
        validateProfileImageFile(file);

        // 기존 프로필 이미지 삭제
        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            deleteOldProfileImage(user.getProfileImage(), user.getId());
        }

        var uploadResult = fileService.uploadFile(file, user.getId(), "profiles");
        return applyProfileImageUpdate(user, uploadResult);
    }

    /**
     * 프로필 이미지 업로드 (presigned URL 기반)
     */
    public ProfileImageResponse uploadProfileImage(String email, FileUploadInitRequest request) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        validateProfileImageMetadata(request);

        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            deleteOldProfileImage(user.getProfileImage(), user.getId());
        }

        var uploadResult = fileService.initiateUpload(request, user.getId(), "profiles");
        return applyProfileImageUpdate(user, uploadResult);
    }

    /**
     * 특정 사용자 프로필 조회
     */
    public UserResponse getUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        return UserResponse.from(user);
    }

    /**
     * 프로필 이미지 파일 유효성 검증
     */
    private void validateProfileImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("이미지가 제공되지 않았습니다.");
        }

        // 파일 크기 검증
        if (file.getSize() > maxProfileImageSize) {
            throw new IllegalArgumentException("파일 크기는 5MB를 초과할 수 없습니다.");
        }

        // Content-Type 검증
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        // 파일 확장자 검증 (보안을 위해 화이트리스트 유지)
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        // FileSecurityUtil의 static 메서드 호출
        String extension = FileUtil.getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }
    }

    /**
     * 프로필 이미지 메타데이터 유효성 검증 (presigned URL용)
     */
    private void validateProfileImageMetadata(FileUploadInitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("이미지 정보가 제공되지 않았습니다.");
        }

        if (request.getSize() == null || request.getSize() <= 0) {
            throw new IllegalArgumentException("이미지 크기가 올바르지 않습니다.");
        }

        if (request.getSize() > maxProfileImageSize) {
            throw new IllegalArgumentException("파일 크기는 5MB를 초과할 수 없습니다.");
        }

        if (!StringUtils.hasText(request.getMimetype()) || !request.getMimetype().startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        if (!StringUtils.hasText(request.getFilename())) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        String extension = FileUtil.getFileExtension(request.getFilename()).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }
    }

    private ProfileImageResponse applyProfileImageUpdate(User user, FileUploadResult uploadResult) {
        if (uploadResult == null || uploadResult.getFile() == null) {
            throw new RuntimeException("업로드된 파일 정보를 찾을 수 없습니다.");
        }

        String safeFilename = uploadResult.getFile().getFilename();
        String profileImageUrl = "/api/files/view/" + safeFilename;

        user.setProfileImage(profileImageUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("프로필 이미지 업로드 완료 - User ID: {}, File: {}", user.getId(), profileImageUrl);

        return ProfileImageResponse.builder()
                .success(true)
                .message("프로필 이미지가 업데이트되었습니다.")
                .imageUrl(profileImageUrl)
                .uploadUrl(uploadResult.getUploadUrl())
                .uploadHeaders(uploadResult.getUploadHeaders())
                .requiresUpload(uploadResult.isRequiresUpload())
                .filename(safeFilename)
                .fileId(uploadResult.getFile().getId())
                .build();
    }

    /**
     * 기존 프로필 이미지 삭제
     */
    private void deleteOldProfileImage(String profileImageUrl, String userId) {
        if (!StringUtils.hasText(profileImageUrl)) {
            return;
        }

        String filename = extractFilename(profileImageUrl);
        if (!StringUtils.hasText(filename)) {
            return;
        }

        try {
            boolean deleted = fileService.deleteFileByFilename(filename, userId);
            if (deleted) {
                log.info("기존 프로필 이미지 삭제 완료: {}", filename);
            }
        } catch (Exception e) {
            log.warn("기존 프로필 이미지 삭제 실패: {}", e.getMessage());
        }
    }

    /**
     * 프로필 이미지 삭제
     * @param email 사용자 이메일
     */
    public void deleteProfileImage(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            deleteOldProfileImage(user.getProfileImage(), user.getId());
            user.setProfileImage("");
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("프로필 이미지 삭제 완료 - User ID: {}", user.getId());
        }
    }

    /**
     * 회원 탈퇴 처리
     * @param email 사용자 이메일
     */
    public void deleteUserAccount(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            deleteOldProfileImage(user.getProfileImage(), user.getId());
        }

        userRepository.delete(user);
        log.info("회원 탈퇴 완료 - User ID: {}", user.getId());
    }

    private String extractFilename(String profileImageUrl) {
        if (!StringUtils.hasText(profileImageUrl)) {
            return "";
        }

        String trimmed = profileImageUrl.trim();
        if (trimmed.contains("/")) {
            return trimmed.substring(trimmed.lastIndexOf('/') + 1);
        }
        return trimmed;
    }
}
