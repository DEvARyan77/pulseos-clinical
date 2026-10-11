#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -e "$ROOT/.env" ]]; then
  echo "Existing .env preserved."
  exit 0
fi
umask 077
cat > "$ROOT/.env" <<VARS
POSTGRES_DB=pulseos
POSTGRES_USER=pulseos
POSTGRES_PASSWORD=$(openssl rand -hex 24)
WEB_PORT=3000
VARS
chmod 600 "$ROOT/.env"
echo "Generated a private .env with a random database password."
