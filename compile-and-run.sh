#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  IoT Based Toll Booth Manager - compile and run the CONSOLE application
#  Usage :  ./compile-and-run.sh
# ---------------------------------------------------------------------------
set -e
cd "$(dirname "$0")"

echo "=========================================="
echo " Compiling the toll booth manager project "
echo "=========================================="
mkdir -p out
javac -d out $(find tollbooth -name "*.java")

# The web page (HTML / CSS / JavaScript) lives in resources/static. Copy it next
# to the class files so that the web server can serve it from the classpath.
if [ -d resources/static ]; then
    mkdir -p out/static
    cp -f resources/static/* out/static/
fi

echo "Compilation successful. Class files are in the 'out' folder."
echo
echo "Starting the CONSOLE application (type 10 to exit)..."
echo "Tip : for the real time web application run  ./run-web.sh"
echo
java -cp out tollbooth.TollBoothManager
