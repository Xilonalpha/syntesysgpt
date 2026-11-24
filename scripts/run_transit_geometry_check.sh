#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
J=$(mktemp -d); trap 'rm -rf "$J"' EXIT
kotlinc app/src/main/java/com/synthesis/service/{PhysicalTransitProfile,PhysicalTransitFit,TransitGeometryFit}.kt scripts/TransitGeometryCheck.kt -include-runtime -d "$J/check.jar"
java -jar "$J/check.jar"
