package dev.ghostdrop.configuration;

import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public final class AwsConfiguration {
    private static final Region REGION = Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1"));

    private AwsConfiguration() {}

    public static DynamoDbClient dynamoDb() { return configure(DynamoDbClient.builder()).build(); }
    public static S3Client s3() { return configure(S3Client.builder()).build(); }
    public static S3Presigner presigner() {
        var builder = S3Presigner.builder().region(REGION);
        var endpoint = endpoint();
        if (endpoint != null && !endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint)).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        return builder.build();
    }

    private static <B extends software.amazon.awssdk.awscore.client.builder.AwsClientBuilder<B, ?>> B configure(B builder) {
        var endpoint = endpoint();
        builder.region(REGION);
        if (endpoint != null && !endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint)).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        return builder;
    }

    private static String endpoint() { return System.getenv().getOrDefault("GHOSTDROP_AWS_ENDPOINT_URL", System.getenv("AWS_ENDPOINT_URL")); }
}
