package com.dragonresort;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.io.IOException;
import com.fasterxml.jackson.jr.ob.JSON;

public class DragonResort implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

   private final String tableName = System.getenv("DYNAMODB_TABLE_NAME");
   private final String endpoint = System.getenv("DYNAMODB_ENDPOINT");
   private final String region = System.getenv("AWS_REGION");

   private static DynamoDbClient createDynamoDbClient(String endpoint, String region) {
      if (endpoint != null && !endpoint.isEmpty()) {
         return DynamoDbClient.builder()
                 .endpointOverride(URI.create(endpoint))
                 .region(Region.of(region))
                 .build();
      }
      return DynamoDbClient.create();
   }

   private final DynamoDbClient dynamoDb = createDynamoDbClient(endpoint, region);

   private String toJson(Dragon dragon) throws IOException {
      return JSON.std.asString(dragon);
   }
   private Dragon fromJson(String jsonString) throws IOException {
      return JSON.std.beanFrom(Dragon.class, jsonString);
   }
   private String toJsonArray(List<Dragon> dragons) throws IOException {
      return JSON.std.asString(dragons);
   }

   private APIGatewayProxyResponseEvent validateDragon(Dragon dragon) {
      record ValidationField(String value, String fieldName) {}

      List<ValidationField> fields = List.of(
              new ValidationField(dragon.name(), "name"),
              new ValidationField(dragon.type(), "type"),
              new ValidationField(dragon.age(), "age"),
              new ValidationField(dragon.color(), "color")
      );

      return fields.stream()
              .filter(field -> field.value() == null || field.value().trim().isEmpty())
              .findFirst()
              .map(field -> new APIGatewayProxyResponseEvent()
                      .withStatusCode(400)
                      .withBody(String.format("{\"error\": \"Dragon %s is required\"}", field.fieldName())))
              .orElse(null);
   }

   private APIGatewayProxyResponseEvent validateDragonId(String id) {
      if (id == null || id.trim().isEmpty()) {
         return new APIGatewayProxyResponseEvent()
                 .withStatusCode(400)
                 .withBody("{\"error\": \"Id is required\"}");
      }
      return null;
   }

   private AttributeValue stringAttribute(String value) {
      return AttributeValue.builder().s(value.trim()).build();
   }

   private Map<String, AttributeValue> createAttributeMap(Dragon dragon, String id) {
      return Map.of(
              "id", stringAttribute(id != null ? id : dragon.id()),
              "name", stringAttribute(dragon.name()),
              "type", stringAttribute(dragon.type()),
              "age", stringAttribute(dragon.age()),
              "color", stringAttribute(dragon.color())
      );
   }

   @Override
   public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
      return switch (input.getHttpMethod()) {
         case "GET" -> handleGetRequest(input);
         case "POST" -> handlePostRequest(input);
         case "PUT" -> handlePutRequest(input);
         case "DELETE" -> handleDeleteRequest(input);
         default -> new APIGatewayProxyResponseEvent()
                 .withStatusCode(400)
                 .withBody("{\"error\": \"Unsupported HTTP method\"}");
      };
   }


   private APIGatewayProxyResponseEvent handlePostRequest(APIGatewayProxyRequestEvent input) {
      try {

         Dragon dragon = fromJson(input.getBody());

         APIGatewayProxyResponseEvent validationResponse = validateDragon(dragon);
         if (validationResponse != null) {
            return validationResponse;
         }

         Map<String, AttributeValue> item = createAttributeMap(dragon, null);

         PutItemRequest putItemRequest = PutItemRequest.builder()
                 .tableName(tableName)
                 .item(item)
                 .build();

         dynamoDb.putItem(putItemRequest);

         return new APIGatewayProxyResponseEvent()
                 .withStatusCode(201)
                 .withBody(toJson(dragon));

      } catch (Exception e) {
         return new APIGatewayProxyResponseEvent()
                 .withStatusCode(500)
                 .withBody("{\"error\": \"" + e.getMessage() + "\"}");
      }
   }

   private APIGatewayProxyResponseEvent handleGetRequest(APIGatewayProxyRequestEvent input) {
      try {
         Map<String, String> pathParameters = input.getPathParameters();
         if (pathParameters != null && pathParameters.containsKey("id")) {
            // Get single dragon
            String id = pathParameters.get("id");
            GetItemRequest getItemRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("id", AttributeValue.builder().s(id).build()))
                    .build();

            GetItemResponse getItemResponse = dynamoDb.getItem(getItemRequest);
            if (!getItemResponse.hasItem()) {
               return new APIGatewayProxyResponseEvent()
                       .withStatusCode(404)
                       .withBody("{\"error\": \"Dragon not found\"}");
            }

            Map<String, AttributeValue> item = getItemResponse.item();
            Dragon dragon = new Dragon(
                    item.get("id").s(),
                    item.get("name").s(),
                    item.containsKey("age") ? item.get("age").s() : null,
                    item.containsKey("color") ? item.get("color").s() : null,
                    item.get("type").s());

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(toJson(dragon));
         } else {
            // Get all dragons
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(tableName)
                    .build();

            ScanResponse scanResponse = dynamoDb.scan(scanRequest);
            List<Dragon> dragons = new ArrayList<>();

            for (Map<String, AttributeValue> item : scanResponse.items()) {
               Dragon dragon = new Dragon(
                       item.get("id").s(),
                       item.get("name").s(),
                       item.containsKey("age") ? item.get("age").s() : null,
                       item.containsKey("color") ? item.get("color").s() : null,
                       item.get("type").s());
               dragons.add(dragon);
            }

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(toJsonArray(dragons));
         }
      } catch (Exception e) {
         return new APIGatewayProxyResponseEvent()
                 .withStatusCode(500)
                 .withBody("{\"error\": \"" + e.getMessage() + "\"}");
      }
   }

   private APIGatewayProxyResponseEvent handlePutRequest(APIGatewayProxyRequestEvent input) {
      try {

         Map<String, String> pathParameters = input.getPathParameters();

         if (pathParameters == null || !pathParameters.containsKey("id")) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\": \"Path parameters are missing\"}");
         }

         String id = pathParameters.get("id");

         APIGatewayProxyResponseEvent idValidationResponse = validateDragonId(id);
         if (idValidationResponse != null) {
            return idValidationResponse;
         }

         Dragon dragon = fromJson(input.getBody());

         APIGatewayProxyResponseEvent dragonValidationResponse = validateDragon(dragon);
         if (dragonValidationResponse != null) {
            return dragonValidationResponse;
         }

         Map<String, AttributeValue> item = createAttributeMap(dragon, id);

         PutItemRequest putItemRequest = PutItemRequest.builder()
                 .tableName(tableName)
                 .item(item)
                 .build();

         try {
            dynamoDb.putItem(putItemRequest);
         } catch (ConditionalCheckFailedException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(404)
                    .withBody("{\"error\": \"Dragon not found\"}");
         }

         return new APIGatewayProxyResponseEvent()
                 .withStatusCode(200)
                 .withBody(toJson(dragon));

      } catch (Exception e) {
         return new APIGatewayProxyResponseEvent()
                 .withStatusCode(500)
                 .withBody("{\"error\": \"" + e.getMessage() + "\"}");
      }
   }

   private APIGatewayProxyResponseEvent handleDeleteRequest(APIGatewayProxyRequestEvent input) {
      try {
         Map<String, String> pathParameters = input.getPathParameters();
         if (pathParameters == null || !pathParameters.containsKey("id")) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\": \"Path parameters are missing\"}");
         }

         String id = pathParameters.get("id");

         APIGatewayProxyResponseEvent validationResponse = validateDragonId(id);
         if (validationResponse != null) {
            return validationResponse;
         }

         DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder()
                 .tableName(tableName)
                 .key(Map.of("id", AttributeValue.builder().s(id).build()))
                 .conditionExpression("attribute_exists(id)")
                 .build();

         try {
            dynamoDb.deleteItem(deleteItemRequest);
         } catch (ConditionalCheckFailedException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(404)
                    .withBody("{\"error\": \"Dragon not found\"}");
         }

         return new APIGatewayProxyResponseEvent()
                 .withStatusCode(204)
                 .withBody("");

      } catch (Exception e) {
         return new APIGatewayProxyResponseEvent()
                 .withStatusCode(500)
                 .withBody("{\"error\": \"" + e.getMessage() + "\"}");
      }
   }
}