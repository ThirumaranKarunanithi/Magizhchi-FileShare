package com.magizhchi.share.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    @Value("${aws.region}")                         private String region;
    @Value("${aws.access-key-id}")                  private String accessKey;
    @Value("${aws.secret-access-key}")              private String secretKey;
    @Value("${aws.s3.endpoint:#{null}}")            private String endpoint;   // null = real AWS

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials());
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint))
                   .serviceConfiguration(S3Configuration.builder()
                           .pathStyleAccessEnabled(true)   // required by LocalStack
                           .build());
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials());
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint))
                   .serviceConfiguration(S3Configuration.builder()
                           .pathStyleAccessEnabled(true)
                           .build());
        }
        return builder.build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));
    }
}
