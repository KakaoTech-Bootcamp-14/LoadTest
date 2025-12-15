package com.ktb.chatapp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FileUploadInitRequest {

    @NotBlank
    private String filename;

    @NotBlank
    private String mimetype;

    @NotNull
    @Min(1)
    private Long size;
}
