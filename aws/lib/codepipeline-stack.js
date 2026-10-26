import {
    Fn,
    Stack,
    pipelines,
    aws_iam as iam,
    aws_codebuild as codebuild,
    aws_codepipeline as codepipeline,
} from 'aws-cdk-lib'

const { CodePipeline, CodePipelineSource, ShellStep } = pipelines
import { SecretSantaStage } from './app-stage.js'

export class CodepipelineStack extends Stack {
    constructor(scope, id, props) {
        super(scope, id, props)

        const { repo, branch } = props

        // The account's `Grovelight` GitHub connection, owned and exported by
        // grovelight-general-infrastructure. Never create one here.
        const connectionArn = Fn.importValue('grovelight-general-infrastructure-cdk:github-grovelight')

        const pipeline = new CodePipeline(this, 'Pipeline', {
            pipelineType: codepipeline.PipelineType.V2,
            synth: new ShellStep('Synth', {
                // Every push to `branch` runs the pipeline: test and build the
                // Lambda's zip (Gradle), test and synth the CDK app, update the
                // pipeline itself, then deploy the app stack.
                input: CodePipelineSource.connection(repo, branch, { connectionArn, triggerOnPush: true }),
                commands: ['npm ci', './gradlew test buildZip --no-daemon', 'npm test', 'npx cdk synth'],
            }),
            synthCodeBuildDefaults: {
                // Java 21 for Gradle (the Lambda runs java21), Node for the CDK.
                partialBuildSpec: codebuild.BuildSpec.fromObject({
                    phases: { install: { 'runtime-versions': { java: 'corretto21', nodejs: '20' } } },
                }),
                rolePolicy: [
                    new iam.PolicyStatement({
                        effect: 'Allow',
                        actions: ['sts:AssumeRole', 'iam:PassRole'],
                        resources: ['arn:aws:iam::*:role/cdk-*'],
                    }),
                ],
            },
        })

        pipeline.addStage(new SecretSantaStage(this, 'production', props))

        // CDK grants the source action only `codestar-connections:UseConnection`,
        // which is refused for connections with a newer `arn:aws:codeconnections:`
        // ARN. Grant the new-prefix action too, on the source action's own role
        // (same fix as grovelight-general-infrastructure).
        pipeline.buildPipeline()
        pipeline.pipeline.node
            .findChild('Source')
            .node.findAll()
            .filter((construct) => construct instanceof iam.Role)
            .forEach((role) =>
                role.addToPrincipalPolicy(
                    new iam.PolicyStatement({
                        actions: ['codeconnections:UseConnection'],
                        resources: [connectionArn],
                    }),
                ),
            )
    }
}
