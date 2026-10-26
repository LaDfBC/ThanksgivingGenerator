#!/usr/bin/env node
import { App } from 'aws-cdk-lib'
import { CodepipelineStack } from '../lib/codepipeline-stack.js'

const app = new App()

new CodepipelineStack(app, 'secretsanta-cdk', {
    // The `grovelight` account, home of flyte-frontend (grovelight.net and the
    // /secretsanta Cognito pool) and grovelight-general-infrastructure.
    env: {
        account: '737892386269',
        region: 'us-east-1',
    },
    // Pulled through the account's `Grovelight` GitHub connection, which has to
    // be able to see this repo (README: "First-time setup").
    repo: 'LaDfBC/ThanksgivingGenerator',
    branch: 'main',

    // API config (not secrets):
    //   - cognito: flyte-frontend's /secretsanta user pool, the same ids as its
    //     src/secretsanta/config.ts (stack outputs SecretSantaUserPoolId and
    //     SecretSantaUserPoolClientId). The API only accepts that pool's tokens.
    //   - adminEmail: the one account allowed to force pairs.
    //   - fromEmail: where the draw emails come from. flyte-frontend's stack
    //     verifies the grovelight.net domain with SES (sesIdentity).
    //   - allowedOrigins: CORS origins for the HTTP API.
    cognito: {
        userPoolId: 'us-east-1_nppy6F7As',
        clientId: '3jphbe7m9827vdecduqsh6mtat',
    },
    adminEmail: 'george.mausshardt@gmail.com',
    fromEmail: 'secretsanta@grovelight.net',
    sesIdentity: 'grovelight.net',
    allowedOrigins: ['https://grovelight.net', 'https://www.grovelight.net', 'http://localhost:5173'],
})

app.synth()
