package com.pod99.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

@Configuration
public class AwsConfig {
    
    @Value("${aws.dynamodb.endpoint:}")
    private String dynamoDbEndpoint;
    
    @Value("${aws.region:us-east-1}")
    private String region;
    
    @Value("${aws.credentials.access-key-id:test}")
    private String accessKeyId;
    
    @Value("${aws.credentials.secret-access-key:test}")
    private String secretAccessKey;
    
    @Bean
    public software.amazon.awssdk.auth.credentials.AwsCredentialsProvider awsCredentialsProvider() {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    }
    
    @Bean
    public DynamoDbClient dynamoDbClient(software.amazon.awssdk.auth.credentials.AwsCredentialsProvider credentialsProvider) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider);
        
        if (!dynamoDbEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(dynamoDbEndpoint));
        }
        
        return builder.build();
    }
    
    // TODO: Restaurar EventBridgeClient bean quando implementar de verdade
}

