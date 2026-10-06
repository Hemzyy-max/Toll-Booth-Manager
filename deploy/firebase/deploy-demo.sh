#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  deploy-demo.sh   -   OPTION B : free static demo on Firebase Hosting
# ---------------------------------------------------------------------------
#  Publishes the web page with the browser demo backend (demo-backend.js).
#  No Java server, no Cloud Run, no billing account : it works on the free
#  Spark plan of Firebase.
#
#  READ FIRST : in this mode the login is checked inside the browser, because
#  Firebase Hosting can only serve static files. The real login system needs
#  option A (see deploy-full.sh and docs/DEPLOY_PUBLIC.md).
#
#  Usage :
#      FIREBASE_PROJECT=my-project-id ./deploy-demo.sh
#      (or copy .firebaserc.example to .firebaserc and just run ./deploy-demo.sh)
# ---------------------------------------------------------------------------
set -e
cd "$(dirname "$0")"

if ! command -v firebase >/dev/null 2>&1; then
    echo "The Firebase CLI is not installed. Install it with :"
    echo "    npm install -g firebase-tools"
    echo "then log in with :  firebase login"
    exit 1
fi

# keep the page and the demo backend in step with resources/static
./sync-public.sh

PROJECT="${FIREBASE_PROJECT:-}"
if [ -z "$PROJECT" ]; then
    if [ -f .firebaserc ] && ! grep -q 'PUT-YOUR-FIREBASE-PROJECT-ID-HERE' .firebaserc; then
        PROJECT=""
    else
        echo "Please give the Firebase project id, for example :"
        echo "    FIREBASE_PROJECT=toll-booth-demo ./deploy-demo.sh"
        echo "(list your projects with :  firebase projects:list )"
        exit 1
    fi
fi

echo "Deploying the static demo to Firebase Hosting..."
if [ -n "$PROJECT" ]; then
    firebase deploy --only hosting --config firebase.demo.json --project "$PROJECT"
    echo
    echo "Public URL : https://$PROJECT.web.app"
else
    firebase deploy --only hosting --config firebase.demo.json
    echo
    echo "Public URL : see the Hosting URL printed above (.web.app / .firebaseapp.com)"
fi
