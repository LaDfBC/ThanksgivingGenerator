import { App } from 'aws-cdk-lib'
import { Match, Template } from 'aws-cdk-lib/assertions'
import { CodepipelineStack } from '../lib/codepipeline-stack.js'
import { AppStack } from '../lib/app-stack.js'

// Needs build/distributions/secretsanta-lambda.zip: run `./gradlew buildZip` first.
const props = {
  env: { account: '737892386269', region: 'us-east-1' },
  repo: 'LaDfBC/ThanksgivingGenerator',
  branch: 'main',
  cognito: { userPoolId: 'us-east-1_test', clientId: 'client' },
  adminEmail: 'admin@example.com',
  fromEmail: 'secretsanta@grovelight.net',
  sesIdentity: 'grovelight.net',
  allowedOrigins: ['https://grovelight.net'],
}

const pipeline = Template.fromStack(new CodepipelineStack(new App(), 'test-pipeline', props))
const app = Template.fromStack(new AppStack(new App(), 'test-app', props))

test('the pipeline imports the account GitHub connection and deploys on push to main', () => {
  pipeline.resourceCountIs('AWS::CodeStarConnections::Connection', 0)
  const [resource] = Object.values(pipeline.findResources('AWS::CodePipeline::Pipeline'))
  const source = resource.Properties.Stages[0].Actions[0]
  expect(source.Configuration).toMatchObject({
    ConnectionArn: { 'Fn::ImportValue': 'grovelight-general-infrastructure-cdk:github-grovelight' },
    FullRepositoryId: 'LaDfBC/ThanksgivingGenerator',
    BranchName: 'main',
    DetectChanges: true,
  })
  expect(resource.Properties.Triggers).toBeUndefined()
  expect(resource.Properties.Stages.map((s) => s.Name)).toEqual(['Source', 'Build', 'UpdatePipeline', 'Assets', 'production'])
})

test('the synth step builds the Java Lambda before synthesizing', () => {
  const builds = Object.values(pipeline.findResources('AWS::CodeBuild::Project')).map((p) => p.Properties.Source.BuildSpec)
  const synth = builds.find((spec) => spec.includes('cdk synth'))
  expect(synth).toContain('./gradlew test buildZip')
  expect(synth).toContain('corretto21')
})

test('runs are kept, with an index for organizers and one for participants', () => {
  app.hasResource('AWS::DynamoDB::GlobalTable', {
    DeletionPolicy: 'Retain',
    Properties: Match.objectLike({
      GlobalSecondaryIndexes: [
        Match.objectLike({ IndexName: 'byOwner' }),
        Match.objectLike({ IndexName: 'byMember', Projection: { ProjectionType: 'KEYS_ONLY' } }),
      ],
    }),
  })
})

test('the Lambda gets the Cognito pool and may only send from the verified domain', () => {
  app.hasResourceProperties('AWS::Lambda::Function', {
    Runtime: 'java21',
    Handler: 'jaerapps.functions.generateNames.LambdaHandler::handleRequest',
    Environment: {
      Variables: Match.objectLike({
        COGNITO_USER_POOL_ID: 'us-east-1_test',
        COGNITO_CLIENT_ID: 'client',
        FROM_EMAIL: 'secretsanta@grovelight.net',
      }),
    },
  })
  app.hasResourceProperties('AWS::IAM::Policy', {
    PolicyDocument: {
      Statement: Match.arrayWith([
        Match.objectLike({
          Action: 'ses:SendEmail',
          Resource: { 'Fn::Join': ['', Match.arrayWith([Match.stringLikeRegexp(':identity/grovelight.net$')])] },
        }),
      ]),
    },
  })
})

test('the HTTP API answers CORS for the site and is throttled', () => {
  app.hasResourceProperties('AWS::ApiGatewayV2::Api', {
    CorsConfiguration: Match.objectLike({ AllowOrigins: ['https://grovelight.net'] }),
  })
  app.hasResourceProperties('AWS::ApiGatewayV2::Stage', {
    DefaultRouteSettings: { ThrottlingRateLimit: 10, ThrottlingBurstLimit: 20 },
  })
})
