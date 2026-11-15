package come.ying.lifenote.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Credentials come from local-secrets.yml (gitignored) or the AWS_* environment
 * variables; application.yml only holds empty placeholders.
 */
@Configuration
public class S3Config {

    @Value("${app.s3.region}") private String region;
    @Value("${app.s3.access-key}") private String accessKey;
    @Value("${app.s3.secret-key}") private String secretKey;

    /** Used for upload / delete / list calls. */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    /** Kept separate from S3Client because presigning is a different SDK component. */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }
}
