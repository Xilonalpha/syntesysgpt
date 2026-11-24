#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
K="${KOTLIN_HOME:-$(dirname "$(dirname "$(command -v kotlinc)")")}"; B=app/src/main/java/com/synthesis
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
"$K/bin/kotlinc" scripts/discovery_check_stubs/*.kt "$B"/domain/model/{Observation,Discovery,Anomaly,Correlation,Hypothesis}.kt "$B"/engine/discovery/*.kt "$B"/engine/normalizer/DataNormalizer.kt "$B"/engine/hypothesis/ImpactIpEngine.kt scripts/DiscoveryIntegrityCheck.kt -cp "$K/lib/kotlinx-coroutines-core-jvm.jar" -d "$T/check.jar"
"$K/bin/kotlin" -cp "$T/check.jar:$K/lib/kotlinx-coroutines-core-jvm.jar" DiscoveryIntegrityCheckKt
