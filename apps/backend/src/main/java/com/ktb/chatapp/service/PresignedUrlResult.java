package com.ktb.chatapp.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PresignedUrlResult {
    private String url;
    private String contentType;
    private long contentLength;
}
