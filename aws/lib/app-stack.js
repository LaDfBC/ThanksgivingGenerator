import {
    Aws,
    Stack,
    Duration,
    CfnOutput,
    RemovalPolicy,
    aws_iam as iam,
    aws_lambda as lambda,
    aws_logs as logs,
    aws_dynamodb as dynamodb,
    aws_apigatewayv2 as apigatewayv2,
    aws_apigatewayv2_integrations as integrations,
} from 'aws-cdk-lib'

import path from 'path'
const __dirname = path.dirname(new URL(import.meta.url).pathname)

/** Built by `./gradlew buildZip` before synth (the pipeline's Synth step does both). */
const LAMBDA_ZIP = `${__dirname}/../../build/distributions/secretsanta-lambda.zip`

/**
 * The secret santa API behind grovelight.net/secretsanta: an HTTP API sending every route to one Java Lambda
 * (SantaApi), which keeps runs in DynamoDB and emails the draws through SES.
 */
export class AppStack extends Stack {
    constructor(scope, id, props) {
        super(scope, id, props)

        const { cognito, adminEmail, fromEmail, sesIdentity, allowedOrigins } = props

        // ---- Runs ----
        // Layout in DynamoStore.java: a "RUN" item per run plus a "MEMBER#<email>"
        // item per participant email, each with an index to find them by.
        const table = new dynamodb.TableV2(this, 'Runs', {
            tableName: 'secretsanta-runs',
            partitionKey: { name: 'pk', type: dynamodb.AttributeType.STRING },
            sortKey: { name: 'sk', type: dynamodb.AttributeType.STRING },
            billing: dynamodb.Billing.onDemand(),
            pointInTimeRecoverySpecification: { pointInTimeRecoveryEnabled: true },
            // People's draws live here; never lose them to a stack change.
            removalPolicy: RemovalPolicy.RETAIN,
            globalSecondaryIndexes: [
                {
                    indexName: 'byOwner',
                    partitionKey: { name: 'ownerEmail', type: dynamodb.AttributeType.STRING },
                    sortKey: { name: 'createdAt', type: dynamodb.AttributeType.STRING },
                    projectionType: dynamodb.ProjectionType.ALL,
                },
                {
                    indexName: 'byMember',
                    partitionKey: { name: 'member', type: dynamodb.AttributeType.STRING },
                    sortKey: { name: 'createdAt', type: dynamodb.AttributeType.STRING },
                    projectionType: dynamodb.ProjectionType.KEYS_ONLY,
                },
            ],
        })

        // ---- Lambda ----
        const apiLambda = new lambda.Function(this, 'Api', {
            runtime: lambda.Runtime.JAVA_21,
            handler: 'jaerapps.functions.generateNames.LambdaHandler::handleRequest',
            code: lambda.Code.fromAsset(LAMBDA_ZIP),
            // Java starts faster with more memory (Lambda scales CPU with it).
            memorySize: 1024,
            // Creating a run emails every address in it, one SES call each.
            timeout: Duration.seconds(60),
            logGroup: new logs.LogGroup(this, 'Api-Logs', {
                retention: logs.RetentionDays.THREE_MONTHS,
            }),
            environment: {
                COGNITO_USER_POOL_ID: cognito.userPoolId,
                COGNITO_CLIENT_ID: cognito.clientId,
                ADMIN_EMAIL: adminEmail,
                TABLE_NAME: table.tableName,
                FROM_EMAIL: fromEmail,
                JAVA_TOOL_OPTIONS: '-XX:+TieredCompilation -XX:TieredStopAtLevel=1',
            },
        })
        table.grantReadWriteData(apiLambda)
        apiLambda.addToRolePolicy(
            new iam.PolicyStatement({
                actions: ['ses:SendEmail'],
                resources: [`arn:aws:ses:${Aws.REGION}:${Aws.ACCOUNT_ID}:identity/${sesIdentity}`],
            }),
        )

        // ---- HTTP API ----
        // API Gateway answers CORS preflights itself, so the Lambda never sees them.
        const httpApi = new apigatewayv2.HttpApi(this, 'HttpApi', {
            apiName: 'secretsanta-api',
            corsPreflight: {
                allowOrigins: allowedOrigins,
                allowMethods: [
                    apigatewayv2.CorsHttpMethod.GET,
                    apigatewayv2.CorsHttpMethod.POST,
                    apigatewayv2.CorsHttpMethod.DELETE,
                ],
                allowHeaders: ['authorization', 'content-type'],
                maxAge: Duration.hours(1),
            },
        })
        httpApi.addRoutes({
            path: '/{proxy+}',
            methods: [apigatewayv2.HttpMethod.GET, apigatewayv2.HttpMethod.POST, apigatewayv2.HttpMethod.DELETE],
            integration: new integrations.HttpLambdaIntegration('api-integration', apiLambda),
        })
        // A family gift exchange needs a handful of requests a second at most;
        // this caps what a runaway client (or anyone else) can cost.
        httpApi.defaultStage.node.defaultChild.defaultRouteSettings = {
            throttlingRateLimit: 10,
            throttlingBurstLimit: 20,
        }

        // flyte-frontend's src/secretsanta/config.ts `functionUrl` is this.
        new CfnOutput(this, 'api-gateway-url', {
            value: httpApi.apiEndpoint,
            exportName: `${Aws.STACK_NAME}:api-gateway-url`,
        })
    }
}
