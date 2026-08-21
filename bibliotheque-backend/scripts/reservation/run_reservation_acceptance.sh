#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-bibliotheque}"
DB_USER="${DB_USER:-bibliotheque}"
DB_PASSWORD="${DB_PASSWORD:-bibliotheque}"
AUTH_USER="${AUTH_USER:-admin}"
AUTH_PASSWORD="${AUTH_PASSWORD:-admin123}"
export PGPASSWORD="$DB_PASSWORD"

psql_cmd=(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1)
fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }
scalar(){ "${psql_cmd[@]}" -Atq -c "$1"; }

request(){
  local method="$1" path="$2" payload="${3:-}" tmp
  tmp="$(mktemp)"
  if [[ -n "$payload" ]]; then
    HTTP_CODE="$(curl -sS -o "$tmp" -w '%{http_code}' -X "$method" \
      -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
      -d "$payload" "$BASE_URL$path")"
  else
    HTTP_CODE="$(curl -sS -o "$tmp" -w '%{http_code}' -X "$method" \
      -H "Authorization: Bearer $TOKEN" "$BASE_URL$path")"
  fi
  BODY="$(cat "$tmp")"; rm -f "$tmp"
}

assert_code(){ [[ "$HTTP_CODE" == "$1" ]] || fail "HTTP attendu=$1 obtenu=$HTTP_CODE body=$BODY"; }
assert_contains(){ [[ "$BODY" == *"$1"* ]] || fail "Body ne contient pas '$1' : $BODY"; }

"${psql_cmd[@]}" -f "$SCRIPT_DIR/01_prepare_scenario.sql" >/dev/null
"${psql_cmd[@]}" -f "$SCRIPT_DIR/02_check_scenario.sql"
curl -fsS "$BASE_URL/v3/api-docs" >/dev/null || fail "Backend inaccessible sur $BASE_URL"

AUTH_BODY="$(curl -sS -X POST "$BASE_URL/authenticate" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$AUTH_USER\",\"password\":\"$AUTH_PASSWORD\"}")"
TOKEN="$(printf '%s' "$AUTH_BODY" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("jwtToken",""))')"
[[ -n "$TOKEN" ]] || fail "JWT introuvable: $AUTH_BODY"

L1="$(scalar "SELECT book_id FROM books WHERE book_name='[TEST-RES] L1 Disponible' LIMIT 1;")"
L2="$(scalar "SELECT book_id FROM books WHERE book_name='[TEST-RES] L2 Emprunte' LIMIT 1;")"
L3="$(scalar "SELECT book_id FROM books WHERE book_name='[TEST-RES] L3 Emprunte' LIMIT 1;")"
L4="$(scalar "SELECT book_id FROM books WHERE book_name='[TEST-RES] L4 Emprunte' LIMIT 1;")"
L5="$(scalar "SELECT book_id FROM books WHERE book_name='[TEST-RES] L5 Emprunte' LIMIT 1;")"
A1="$(scalar "SELECT user_id FROM users WHERE username='reservation_a1' LIMIT 1;")"
A2="$(scalar "SELECT user_id FROM users WHERE username='reservation_a2' LIMIT 1;")"

for v in "$L1" "$L2" "$L3" "$L4" "$L5" "$A1" "$A2"; do [[ -n "$v" ]] || fail 'Fixture manquante'; done

request POST '/api/reservations' "{\"livreId\":$L1,\"adherentId\":$A1}"
assert_code 409; assert_contains 'RG-01'; pass 'RG-01'

request POST '/api/reservations' "{\"livreId\":$L2,\"adherentId\":$A1}"
assert_code 201
printf '%s' "$BODY" | python3 -c '
import json,sys
from datetime import datetime,timedelta
d=json.load(sys.stdin)
a=datetime.fromisoformat(d["dateReservation"].replace("Z","+00:00"))
b=datetime.fromisoformat(d["dateExpiration"].replace("Z","+00:00"))
assert b-a == timedelta(days=7), (b-a)
assert d["statut"] == "EN_ATTENTE", d["statut"]
'
pass 'RG-04 + creation valide'

request POST '/api/reservations' "{\"livreId\":$L2,\"adherentId\":$A1}"
assert_code 409; assert_contains 'RG-02'; pass 'RG-02'

for BOOK_ID in "$L2" "$L3" "$L4"; do
  request POST '/api/reservations' "{\"livreId\":$BOOK_ID,\"adherentId\":$A2}"
  assert_code 201
done
request POST '/api/reservations' "{\"livreId\":$L5,\"adherentId\":$A2}"
assert_code 409; assert_contains 'RG-03'; pass 'RG-03'

echo '=============================='
echo 'RG-01 : PASS'
echo 'RG-02 : PASS'
echo 'RG-03 : PASS'
echo 'RG-04 : PASS'
echo 'TOTAL : 4/4 PASS'
