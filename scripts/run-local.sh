#!/usr/bin/env bash
# Runs the API on http://localhost:8080 for flyte-frontend's `yarn dev`.
#
#   scripts/run-local.sh            real sign-in, the real secretsanta-runs table and real SES emails: the same
#                                   settings as the deployed Lambda, read from it (needs `aws login --profile grovelight`)
#   scripts/run-local.sh --dry-run  runs in memory, logs emails, accepts the page's mock sign-in (no AWS needed)
set -euo pipefail
cd "$(dirname "$0")/.."

export ALLOWED_ORIGINS="${ALLOWED_ORIGINS:-http://localhost:5173}"

if [[ "${1:-}" == "--dry-run" ]]; then
  export SANTA_DRY_RUN=true
else
  export AWS_PROFILE="${AWS_PROFILE:-grovelight}" AWS_REGION="${AWS_REGION:-us-east-1}"
  fn=$(aws lambda list-functions --query "Functions[?starts_with(FunctionName,'production-secretsanta-deployment-Api')].FunctionName | [0]" --output text)
  [[ -n "${fn}" && "${fn}" != "None" ]] || { echo "No deployed secret santa Lambda found (is AWS_PROFILE=${AWS_PROFILE} logged in?)" >&2; exit 1; }
  while IFS=$'\t' read -r key value; do
    case "${key}" in COGNITO_USER_POOL_ID|COGNITO_CLIENT_ID|ADMIN_EMAIL|TABLE_NAME|FROM_EMAIL) export "${key}=${value}" ;; esac
  done < <(aws lambda get-function-configuration --function-name "${fn}" \
    --query "Environment.Variables" --output json | python3 -c 'import json,sys; [print(f"{k}\t{v}") for k,v in json.load(sys.stdin).items()]')
  # The SDK can't read an SSO login on its own, so hand it the CLI's temporary credentials.
  eval "$(aws configure export-credentials --format env)"
  echo "Using ${fn}'s settings: table ${TABLE_NAME}, user pool ${COGNITO_USER_POOL_ID}, emails from ${FROM_EMAIL}"
fi

exec ./gradlew runFunction --console=plain
