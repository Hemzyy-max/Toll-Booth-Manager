#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  IoT Based Toll Booth Manager System - compile and run script (Linux / macOS)
#  Usage :  ./compile-and-run.sh
# ---------------------------------------------------------------------------
set -e
cd "$(dirname "$0")"

echo "=========================================="
echo " Compiling the toll booth manager project "
echo "=========================================="
mkdir -p out
javac -d out $(find tollbooth -name "*.java")
echo "Compilation successful. Class files are in the 'out' folder."
echo
echo "Starting the application (type 10 to exit)..."
echo
java -cp out tollbooth.TollBoothManager
