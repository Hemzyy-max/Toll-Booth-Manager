#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  IoT Based Toll Booth Manager - compile and run the REAL TIME WEB application
#  Usage :  ./run-web.sh              (port 3000)
#           PORT=8080 ./run-web.sh    (another port)
# ---------------------------------------------------------------------------
set -e
cd "$(dirname "$0")"

PORT="${PORT:-3000}"

echo "=========================================="
echo " Compiling the toll booth manager project "
echo "=========================================="
mkdir -p out
javac -d out $(find tollbooth -name "*.java")

# Copy the web page next to the class files (the server reads it from the
# classpath, with a fall back to the resources folder).
if [ -d resources/static ]; then
    mkdir -p out/static
    cp -f resources/static/* out/static/
fi

echo "Compilation successful."
echo
echo "Starting the real time web application on port $PORT ..."
echo "Open  http://localhost:$PORT  in a browser."
echo "First start creates the accounts  admin / Admin@123  and  operator / Operator@123"
echo "Press Ctrl+C to stop the server."
echo
java -cp out tollbooth.web.WebLauncher --port "$PORT"
