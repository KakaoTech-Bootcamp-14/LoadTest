package com.ktb.chatapp.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileImageResponse {
    private boolean success;
    private String message;
    private String imageUrl;
    private String uploadUrl;
    private Map<String, String> uploadHeaders;
    private boolean requiresUpload;
    private String filename;
    private String fileId;
}
