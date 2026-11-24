#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
J=$(mktemp -d); trap 'rm -rf "$J"' EXIT
kotlinc app/src/main/java/com/synthesis/service/PhysicalTransit{Profile,Fit}.kt scripts/PhysicalTransitFitCheck.kt -include-runtime -d "$J/check.jar"
java -jar "$J/check.jar"
