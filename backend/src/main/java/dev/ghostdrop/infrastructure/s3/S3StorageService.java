package dev.ghostdrop.infrastructure.s3;

import dev.ghostdrop.domain.StorageService;
import java.time.Duration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

public final class S3StorageService implements StorageService {
    private final String bucket;
    private final S3Presigner presigner;
    private final Duration uploadLifetime;

    public S3StorageService(String bucket, S3Presigner presigner, Duration uploadLifetime) {
        this.bucket = bucket;
        this.presigner = presigner;
        this.uploadLifetime = uploadLifetime;
    }

    @Override
    public String createUploadUrl(String storageKey, String contentType, long fileSize) {
        PresignedPutObjectRequest request = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(uploadLifetime)
                .putObjectRequest(PutObjectRequest.builder().bucket(bucket).key(storageKey).contentType(contentType).contentLength(fileSize).build())
                .build());
        return request.url().toString();
    }

    public String createDownloadUrl(String storageKey, Duration lifetime) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(lifetime).getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(storageKey).build()).build()).url().toString();
    }
}
