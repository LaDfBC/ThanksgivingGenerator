# ThanksgivingGenerator

The secret santa API behind [grovelight.net/secretsanta](https://grovelight.net/secretsanta). The page lives in
flyte-frontend (`src/secretsanta/`); this repo is the API it calls: a Java Lambda behind an API Gateway HTTP API,
with DynamoDB for storage and SES for email. It's deployed with the CDK into the **grovelight** AWS account
(`737892386269`), `us-east-1`, by a CodePipeline set up like agent-study-frontend's: **every push to `main`
deploys**.

## What it does

Everyone signs in on the page through flyte-frontend's `/secretsanta` Cognito pool, and the API scopes everything
to the caller's verified email (`SantaApi.java` lists the routes):

1. An organizer posts people (name, email, optional group) and one or more exchange names. Forced santa→recipient
   pairs are allowed only for `adminEmail`.
2. `AssignmentGenerator` draws each exchange: nobody gets themselves or their own group, and nobody draws the same
   person twice across exchanges. Forced pairs skip those rules. Impossible setups return a 422.
3. The run is saved to DynamoDB, then each email address gets one email listing every santa who uses it (kids
   under a parent's address get combined), sent through SES from `fromEmail`.
4. Participants see only their own matches (`GET /draws`). The organizer sees their exchanges and, only when they
   ask for it, the whole draw (`GET /exchanges/{id}/draw`, the spoiler log).

## Run locally

```sh
./gradlew test                              # Java tests
SANTA_DRY_RUN=true ./gradlew runFunction    # http://localhost:8080: runs in memory, emails logged, mock sign-in
./gradlew buildZip && npm ci && npm test    # CDK template tests (they need the Lambda zip)
npx cdk synth
```

`yarn dev` in flyte-frontend points `/secretsanta` at `localhost:8080`, and with its Cognito ids empty, signs in
with the in-browser mock that dry runs accept.

## Layout

| Path | What it is |
| --- | --- |
| `src/main/java/.../SantaApi.java` | The API: routes, validation, the draw, saving and emailing. |
| `src/main/java/.../LambdaHandler.java` | Adapts API Gateway (HTTP API, payload v2) events to `SantaApi`. |
| `src/main/java/.../LocalServer.java` | Serves `SantaApi` locally, with CORS for the dev page. |
| `src/main/java/.../util/` | The draw (`AssignmentGenerator`), request checks (`SantaRequest`), runs (`SantaRun`), storage (`DynamoStore`, `RunStore.InMemory`), email (`EmailService`) and token checks (`CognitoVerifier`). |
| `aws/bin/cdk.js` | The CDK entry point and the one place config lives: the repo, the Cognito ids, the admin and sender emails, CORS origins. |
| `aws/lib/codepipeline-stack.js` | `secretsanta-cdk`: a self-mutating CodePipeline. On push to `main` it runs `npm ci`, `./gradlew test buildZip`, `npm test` and `npx cdk synth`, through the account's `Grovelight` GitHub connection (imported from grovelight-general-infrastructure). |
| `aws/lib/app-stack.js` | `production-secretsanta-deployment`: the `secretsanta-runs` table, the Lambda and the HTTP API. Its `api-gateway-url` output is flyte-frontend's `functionUrl`. |

## First-time setup

1. **GitHub access.** The pipeline pulls this repo through the `Grovelight` GitHub connection, which only sees the
   repos its GitHub App was given. Either move the repo into the Grovelight org (then update `repo` in
   `aws/bin/cdk.js`), or give the app access to it in GitHub (Settings → Applications → AWS Connector for GitHub).
2. **Deploy the pipeline once by hand.** After that it updates itself from `main`:
   ```sh
   ./gradlew buildZip && npm ci
   AWS_PROFILE=grovelight npx cdk deploy secretsanta-cdk
   ```
3. **Point the page at the API.** Copy the `production-secretsanta-deployment` stack's `api-gateway-url` output
   into flyte-frontend's `src/secretsanta/config.ts` (`functionUrl`).
4. **SES production access.** While the account's SES is in the sandbox, it only sends to verified addresses. Ask
   for production access in the SES console (Account dashboard → Request production access). Until then, people
   can still sign in and see their match on the site.

Changing the Cognito pool means updating both `aws/bin/cdk.js` here and flyte-frontend's `config.ts`.
