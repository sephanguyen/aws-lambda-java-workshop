package com.unicorn.location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.jr.ob.JSON;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public class UnicornPostLocationHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final Logger logger = LoggerFactory.getLogger(UnicornPostLocationHandler.class);

    private final DynamoDbAsyncClient  dynamoDbClient;

    public UnicornPostLocationHandler() {
            dynamoDbClient = DynamoDbAsyncClient
            .builder()
            .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
            .region(Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable())))
            .httpClientBuilder(AwsCrtAsyncHttpClient.builder())
            .build();
    }

    private void createLocationItem(UnicornLocation unicornLocation) {
        var putItemRequest = PutItemRequest.builder().item(
        Map.of( "id", AttributeValue.fromS(UUID.randomUUID().toString()),
                "unicornName", AttributeValue.fromS(unicornLocation.getUnicornName()),
                "latitude", AttributeValue.fromS(unicornLocation.getLatitude()),
                "longitude", AttributeValue.fromS(unicornLocation.getLongitude())
        ))
        .tableName("unicorn-locations")
        .build();

        try {
            dynamoDbClient.putItem(putItemRequest).get(); //<-- Add .get() call and catch the checked exceptions
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error creating Put Item request", e);
        }
    }

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        try {
            UnicornLocation unicornLocation = JSON.std.beanFrom(UnicornLocation.class, input.getBody());
            createLocationItem(unicornLocation); //<-- Add this
    
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("Received this unicorn:" + JSON.std.asString(unicornLocation));
        } catch (Exception e) {
            logger.error("Error while processing the request", e);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("Error processing the event");
        }
    }
}
