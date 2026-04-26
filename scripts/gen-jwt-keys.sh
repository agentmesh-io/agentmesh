#!/usr/bin/env bash
# scripts/gen-jwt-keys.sh — Generate (or rotate) the AgentMesh JWT RSA-2048 key pair.
#
# Idempotent: only generates if files are absent, unless --force is passed.
# Writes to ${AGENTMESH_JWT_KEYS_DIR:-$HOME/.agentmesh/jwt}.
# Per Architect Protocol §1 + §5: keys live on the Blackhole SSD when
# AGENTMESH_JWT_KEYS_DIR is set to a /Volumes/Blackhole path.

set -euo pipefail

KEYS_DIR="${AGENTMESH_JWT_KEYS_DIR:-$HOME/.agentmesh/jwt}"
PRIV="${KEYS_DIR}/private_key.pem"
PUB="${KEYS_DIR}/public_key.pem"
FORCE="${1:-}"

mkdir -p "${KEYS_DIR}"
chmod 700 "${KEYS_DIR}" 2>/dev/null || true

if [[ -f "${PRIV}" && -f "${PUB}" && "${FORCE}" != "--force" ]]; then
  echo "[gen-jwt-keys] keys already present at ${KEYS_DIR} — pass --force to rotate"
  exit 0
fi

echo "[gen-jwt-keys] generating RSA-2048 key pair → ${KEYS_DIR}"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${PRIV}" 2>/dev/null
openssl rsa -in "${PRIV}" -pubout -out "${PUB}" 2>/dev/null
chmod 600 "${PRIV}"
chmod 644 "${PUB}"

# Print key id (matches Java RsaKeyProvider.computeKeyId — first 16 hex chars of sha256(DER))
DER=$(openssl rsa -in "${PRIV}" -pubout -outform DER 2>/dev/null | xxd -p -c 9999)
KID=$(printf '%s' "${DER}" | xxd -r -p | shasum -a 256 | cut -c1-16)
echo "[gen-jwt-keys] keyId=${KID}"
echo "[gen-jwt-keys] OK"

