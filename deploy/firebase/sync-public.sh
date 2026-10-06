#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  sync-public.sh
#  Copies the web page of the project into the two deployment folders :
#
#    public/       -> option A : the REAL application
#                     (the page talks to the Java server through the
#                      "/api/**" -> Cloud Run rewrite)
#    public-demo/  -> option B : the FREE static demo
#                     (the page talks to demo-backend.js inside the browser)
#
#  Run this after every change of resources/static/*.html|css|js, then deploy.
# ---------------------------------------------------------------------------
set -e
cd "$(dirname "$0")"

ROOT="$(cd ../.. && pwd)"
SRC="$ROOT/resources/static"

if [ ! -f "$SRC/index.html" ]; then
    echo "ERROR : $SRC/index.html not found. Run this script from the repository."
    exit 1
fi

mkdir -p public public-demo

# ---- option A : plain copy of the real page -------------------------------
cp -f "$SRC/index.html" "$SRC/styles.css" "$SRC/app.js" "$SRC/favicon.svg" public/

# ---- option B : the same page plus the browser demo backend --------------
cp -f "$SRC/index.html" "$SRC/styles.css" "$SRC/app.js" "$SRC/favicon.svg" public-demo/
cp -f demo-backend.js public-demo/demo-backend.js

# Load the demo backend BEFORE app.js, so it can replace fetch() and
# EventSource() before the page starts using them. If the tag is already there,
# nothing changes (the script stays safe to run many times).
if ! grep -q 'demo-backend.js' public-demo/index.html; then
    sed -i 's#<script src="/app.js"></script>#<script src="/demo-backend.js"></script>\n<script src="/app.js"></script>#' public-demo/index.html
fi

echo "public/       : $(ls public | tr '\n' ' ')"
echo "public-demo/  : $(ls public-demo | tr '\n' ' ')"
grep -c 'demo-backend.js' public-demo/index.html | xargs echo "demo script tag in public-demo/index.html :"
