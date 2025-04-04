package com.dragonresort;

import software.constructs.Construct;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.CorsOptions;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.dynamodb.Attribute;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.apigateway.StageOptions;


import java.util.Map;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;

public class InfrastructureStack extends Stack {
    public InfrastructureStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public InfrastructureStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here

        // example resource
        // final Queue queue = Queue.Builder.create(this, "InfrastructureQueue")
        //         .visibilityTimeout(Duration.seconds(300))
        //         .build();
        // Create DynamoDB Table
        var dragonTable = Table.Builder.create(this, "DragonTable")
            .tableName("Dragons")
            .partitionKey(Attribute.builder()
                    .name("id")
                    .type(AttributeType.STRING)
                    .build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .removalPolicy(RemovalPolicy.DESTROY)
            .build();
        
        Map<String, String> environmentVariables = Map.of(
                "DYNAMODB_TABLE_NAME", dragonTable.getTableName(),
                "DYNAMODB_ENDPOINT", "");
         var dragonFunction = Function.Builder.create(this, "DragonResortApp")
            .runtime(Runtime.JAVA_21)
            .functionName("DragonResort")
            .code(Code.fromAsset("../application/assets/DragonResort.jar"))
            .handler("com.dragonresort.DragonResort::handleRequest")
            .description("Dragon Resort Lambda Function")
            .timeout(Duration.seconds(30))
            .memorySize(128)
            .tracing(Tracing.ACTIVE)
            .environment(environmentVariables)
            .build();

        dragonTable.grantReadWriteData(dragonFunction);

        var api = RestApi.Builder.create(this, "DragonApi")
        .restApiName("Dragon Resort API")
        .description("Serverless Dragon Resort API")
        .deployOptions(StageOptions.builder()
                .tracingEnabled(true)
                .dataTraceEnabled(true)
                .build())
        .defaultCorsPreflightOptions(CorsOptions.builder()
                .allowOrigins(java.util.List.of("*"))
                .allowMethods(java.util.List.of("GET", "POST", "PUT", "DELETE"))
                .build())
        .build();
        var lambdaIntegration = new LambdaIntegration(dragonFunction);

        // Add resources and methods for CRUD operations
        var dragons = api.getRoot().addResource("dragons");
        dragons.addMethod("POST", lambdaIntegration);
        dragons.addMethod("GET", lambdaIntegration);

        var dragon = dragons.addResource("{id}");
        dragon.addMethod("GET", lambdaIntegration);
        dragon.addMethod("PUT", lambdaIntegration);
        dragon.addMethod("DELETE", lambdaIntegration);
    }
}
