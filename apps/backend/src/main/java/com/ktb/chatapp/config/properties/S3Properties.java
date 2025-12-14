package com.ktb.chatapp.config.properties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "storage.s3")
public class S3Properties {

    /**
     * S3 bucket name.
     */
    private String bucket;

    /**
     * AWS access key (optional if using instance/profile credentials).
     */
    private String accessKey;

    /**
     * AWS secret key (optional if using instance/profile credentials).
     */
    private String secretKey;

    /**
     * AWS region (e.g., ap-northeast-2).
     */
    private String region = "ap-northeast-2";

    /**
     * Optional endpoint override (LocalStack/MinIO).
     */
    private String endpointOverride;

    /**
     * Base directory prefix for stored objects.
     */
    private String basePath = "chat";

    /**
     * Default directory for chat message attachments.
     */
    private String defaultFolder = "files";

    /**
     * Presigned URL validity.
     */
    private Duration presignDuration = Duration.ofMinutes(15);
}
