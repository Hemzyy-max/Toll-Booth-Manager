#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  deploy-full.sh   -   OPTION A : the REAL application in public
# ---------------------------------------------------------------------------
#  Step 1 : the Java web application is packaged into a container image and
#           published on Cloud Run (Google builds the image, so Docker does NOT
#           have to be installed on your computer).
#  Step 2 : Firebase Hosting serves the web page and forwards every /api/**
#           request to that container, so everything is under one public URL :
#
#               https://YOUR-PROJECT.web.app
#
#  Requirements (see docs/DEPLOY_PUBLIC.md for the full explanation) :
#      * a Firebase project with a Cloud Billing account  (Cloud Run free quota
#        is enough, but billing must be switched on by Google)
#      * gcloud CLI installed and logged in  : gcloud auth login
#      * firebase CLI installed and logged in : npm install -g firebase-tools
#  Usage :
#      FIREBASE_PROJECT=my-project-id ./deploy-full.sh
#      KEEP_WARM=1 FIREBASE_PROJECT=my-project-id ./deploy-full.sh
# ---------------------------------------------------------------------------
set -e
cd "$(dirname "$0")"

ROOT="$(cd ../.. && pwd)"
SERVICE="${SERVICE:-tollbooth-web}"
REGION="${REGION:-asia-south1}"          # Mumbai (supported for Hosting rewrites)
KEEP_WARM="${KEEP_WARM:-0}"

for tool in gcloud firebase; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "The $tool command is missing."
        echo "  gcloud   : https://cloud.google.com/sdk/docs/install"
        echo "  firebase : npm install -g firebase-tools"
        exit 1
    fi
done

PROJECT="${FIREBASE_PROJECT:-}"
if [ -z "$PROJECT" ]; then
    echo "Please give the Firebase project id :"
    echo "    FIREBASE_PROJECT=my-project-id ./deploy-full.sh"
    exit 1
fi

./sync-public.sh

echo
echo "=============================================================="
echo " STEP 1/2 : deploying the Java container to Cloud Run"
echo "   service : $SERVICE"
echo "   region  : $REGION"
echo "   project : $PROJECT"
echo "=============================================================="
MIN_INSTANCES=0
if [ "$KEEP_WARM" = "1" ]; then
    MIN_INSTANCES=1
    echo "KEEP_WARM=1 : one instance stays alive, so the accounts and the"
    echo "audit trail are never lost (this costs a little money)."
fi

# --source "$ROOT" : Cloud Build builds the Dockerfile of the project, so no
# local Docker installation is needed.
gcloud run deploy "$SERVICE" \
    --source "$ROOT" \
    --project "$PROJECT" \
    --region "$REGION" \
    --allow-unauthenticated \
    --port 8080 \
    --memory 512Mi \
    --cpu 1 \
    --timeout 3600 \
    --max-instances 1 \
    --min-instances "$MIN_INSTANCES" \
    --quiet

echo
echo "=============================================================="
echo " STEP 2/2 : deploying the web page to Firebase Hosting"
echo "=============================================================="
firebase deploy --only hosting --config firebase.json --project "$PROJECT"

echo
echo "=============================================================="
echo " DONE"
echo "   Public site : https://$PROJECT.web.app"
echo "   Accounts    : admin / Admin@123   and   operator / Operator@123"
echo
echo " Change those passwords after the first login, or set your own before"
echo " the first start with the Cloud Run variables :"
echo "   TOLLBOOTH_ADMIN_PASSWORD and TOLLBOOTH_OPERATOR_PASSWORD"
echo "=============================================================="
