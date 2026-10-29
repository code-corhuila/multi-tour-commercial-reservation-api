#!/usr/bin/env bash
# Development only: prints an RS256 JWT with the claims this API verifies.
# Sign with a throwaway private key and set its public half in JWT_PUBLIC_KEY(_FILE).
# Never use this in a shared or production environment; no key is stored in the repository.
set -euo pipefail

usage() {
  echo "usage: $0 --key PRIVATE_KEY.pem --tenant UUID --sub ID [--roles a,b] [--permissions x,y] [--ttl SECONDS]" >&2
  exit 2
}

key=""; tenant=""; sub=""; roles=""; permissions=""; ttl=3600
while [ $# -gt 0 ]; do
  [ $# -ge 2 ] || usage
  case "$1" in
    --key) key="$2" ;;
    --tenant) tenant="$2" ;;
    --sub) sub="$2" ;;
    --roles) roles="$2" ;;
    --permissions) permissions="$2" ;;
    --ttl) ttl="$2" ;;
    *) usage ;;
  esac
  shift 2
done
[ -n "$key" ] && [ -n "$tenant" ] && [ -n "$sub" ] || usage
[ -f "$key" ] || { echo "private key not found: $key" >&2; exit 2; }
case "$ttl" in ''|*[!0-9]*) echo "--ttl must be a number of seconds" >&2; exit 2 ;; esac
command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 2; }

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
json_array() {
  local out="" item
  IFS=',' read -ra items <<< "$1"
  for item in "${items[@]}"; do
    [ -n "$item" ] && out="${out:+$out,}\"$item\""
  done
  printf '[%s]' "$out"
}

now=$(date +%s)
claims="{\"sub\":\"$sub\",\"iat\":$now,\"exp\":$((now + ttl)),\"tenantId\":\"$tenant\",\"roles\":$(json_array "$roles")"
[ -n "$permissions" ] && claims="$claims,\"permissions\":$(json_array "$permissions")"
claims="$claims}"

input="$(printf '{"alg":"RS256","typ":"JWT"}' | b64url).$(printf '%s' "$claims" | b64url)"
signature=$(printf '%s' "$input" | openssl dgst -sha256 -sign "$key" | b64url)
printf '%s.%s\n' "$input" "$signature"
