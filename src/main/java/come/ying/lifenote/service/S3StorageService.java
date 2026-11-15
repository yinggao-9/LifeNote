package come.ying.lifenote.service;

import come.ying.lifenote.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * All S3 access lives here; other services never touch S3Client directly,
 * so storage problems have exactly one place to be fixed.
 */
@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    @Value("${app.s3.bucket}")
    private String bucket;

    private final S3Client s3Client;
    private final S3Presigner presigner;

    public S3StorageService(S3Client s3Client, S3Presigner presigner) {
        this.s3Client = s3Client;
        this.presigner = presigner;
    }

    /** Key layout: users/{userId}/documents/{documentId}/{sanitized filename}. */
    public String buildKey(UUID userId, UUID documentId, String filename) {
        return "users/" + userId + "/documents/" + documentId + "/" + sanitize(filename);
    }

    private String sanitize(String filename) {
        String safe = filename.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return safe.isBlank() ? "file.bin" : safe;
    }

    public void upload(String key, byte[] content, String contentType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (Exception e) {
            throw new BusinessException(1103, "Upload to S3 failed: " + e.getMessage());
        }
    }

    public byte[] download(String key) {
        try (InputStream in = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            return in.readAllBytes();
        } catch (IOException | S3Exception e) {
            throw new BusinessException(1103, "Download from S3 failed: " + e.getMessage());
        }
    }

    /**
     * Signs a URL valid for 10 minutes. Generated on demand when the user opens
     * the original - storing it would only create an expired, leakable copy.
     */
    public String presignGetUrl(String key) {
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        return presigner.presignGetObject(req).url().toString();
    }

    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            // a failed delete only leaves an orphan object behind; never fail the main flow
            log.warn("S3 delete failed for key {}: {}", key, e.getMessage());
        }
    }

    /** Account deletion: wipe everything under the user's prefix, page by page. */
    public void deleteAllForUser(UUID userId) {
        String prefix = "users/" + userId + "/";
        String continuation = null;
        do {
            ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                    .bucket(bucket).prefix(prefix)
                    .continuationToken(continuation).build();
            ListObjectsV2Response res = s3Client.listObjectsV2(listReq);
            if (res.hasContents() && !res.contents().isEmpty()) {
                List<ObjectIdentifier> objects = res.contents().stream()
                        .map(s -> ObjectIdentifier.builder().key(s.key()).build())
                        .toList();
                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(objects).build())
                        .build());
            }
            continuation = res.nextContinuationToken();
        } while (continuation != null);
    }
}
