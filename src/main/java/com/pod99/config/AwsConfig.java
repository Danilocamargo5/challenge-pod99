package com.pod99.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
public class AwsConfig {
    
    @Value("${aws.dynamodb.endpoint:}")
    private String dynamoDbEndpoint;
    
    @Value("${aws.eventbridge.endpoint:}")
    private String eventBridgeEndpoint;
    
    @Value("${aws.sqs.endpoint:}")
    private String sqsEndpoint;
    
    @Value("${aws.region:us-east-1}")
    private String region;
    
    @Bean
    public software.amazon.awssdk.auth.credentials.AwsCredentialsProvider awsCredentialsProvider() {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create("test", "test"));
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
    
    @Bean
    public EventBridgeClient eventBridgeClient(software.amazon.awssdk.auth.credentials.AwsCredentialsProvider credentialsProvider) {
        var builder = EventBridgeClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider);
        
        if (!eventBridgeEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(eventBridgeEndpoint));
        }
        
        return builder.build();
    }
    
    @Bean
    public SqsClient sqsClient(software.amazon.awssdk.auth.credentials.AwsCredentialsProvider credentialsProvider) {
        var builder = SqsClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider);
        
        if (!sqsEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(sqsEndpoint));
        }
        
        return builder.build();
    }
}
