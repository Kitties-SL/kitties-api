#!/usr/bin/env bash
# Builds native container images for all (or selected) Kitties services.
# Requires Docker running — the native binary is compiled inside a Quarkus builder container.
#
# Usage:
#   ./build-native.sh                          # build all services
#   ./build-native.sh user-service cat-service # build specific services
#   ./build-native.sh --push                   # build all + push to registry
#   ./build-native.sh --skip-maven cat-service # docker build only (binary already in target/)

set -euo pipefail

ALL_SERVICES=(
  user-service
  auth-service
  storage-service
  cat-service
  notification-service
  adoption-service
  form-analysis-service
  organization-service
  chat-service
  gateway-service
  schedule-service
)

REGISTRY="ciscoadiz"
DOCKERFILE="src/main/docker/Dockerfile.native-micro"
SKIP_MAVEN=false
PUSH=false
SELECTED=()

# ── Arg parsing ────────────────────────────────────────────────────────────────
for arg in "$@"; do
  case "$arg" in
    --skip-maven) SKIP_MAVEN=true ;;
    --push)       PUSH=true ;;
    --*)          echo "Unknown flag: $arg" >&2; exit 1 ;;
    *)            SELECTED+=("$arg") ;;
  esac
done

[[ ${#SELECTED[@]} -eq 0 ]] && SELECTED=("${ALL_SERVICES[@]}")

# ── Validate ───────────────────────────────────────────────────────────────────
for svc in "${SELECTED[@]}"; do
  if [[ ! -d "$svc" ]]; then
    echo "ERROR: service directory '$svc' not found" >&2
    exit 1
  fi
done

# ── either-mon (shared lib, must be installed before individual service builds) ─
if [[ "$SKIP_MAVEN" == false ]]; then
  echo ""
  echo "==> Installing either-mon"
  mvn install -pl either-mon -DskipTests -q
fi

# ── Per-service build ──────────────────────────────────────────────────────────
FAILED=()

for svc in "${SELECTED[@]}"; do
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  $svc"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  if [[ "$SKIP_MAVEN" == false ]]; then
    echo "--> [1/2] mvn package -Pnative (container build)"
    if ! mvn package -Pnative \
        -Dquarkus.native.container-build=true \
        -Dquarkus.container-image.build=false \
        -DskipTests \
        -pl "$svc" \
        -q; then
      echo "ERROR: maven native build failed for $svc" >&2
      FAILED+=("$svc")
      continue
    fi
  fi

  echo "--> [2/2] docker build → $REGISTRY/$svc:latest"
  if ! docker build \
      -f "$svc/$DOCKERFILE" \
      -t "$REGISTRY/$svc:latest" \
      "$svc/"; then
    echo "ERROR: docker build failed for $svc" >&2
    FAILED+=("$svc")
    continue
  fi

  if [[ "$PUSH" == true ]]; then
    echo "--> pushing $REGISTRY/$svc:latest"
    docker push "$REGISTRY/$svc:latest"
  fi

  echo "--> OK: $REGISTRY/$svc:latest"
done

# ── Summary ────────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Summary"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

OK=()
for svc in "${SELECTED[@]}"; do
  if printf '%s\n' "${FAILED[@]:-}" | grep -qx "$svc"; then
    echo "  FAIL  $svc"
  else
    echo "  OK    $REGISTRY/$svc:latest"
    OK+=("$svc")
  fi
done

if [[ ${#FAILED[@]} -gt 0 ]]; then
  echo ""
  echo "Failed services: ${FAILED[*]}"
  exit 1
fi