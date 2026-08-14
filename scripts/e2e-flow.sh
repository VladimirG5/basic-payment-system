#!/usr/bin/env bash
# End-to-end verification of the transfer flow:
# register -> login -> initiate -> read OTP from gateway logs -> confirm ->
# verify both account balances via GET /api/v1/accounts/{id}, plus an
# idempotency check on the confirm step.
#
# Requires the docker-compose stack running (`docker compose up -d`).
# Registers two fresh, uniquely-named users each run so it's safely re-runnable
# without depending on or disturbing the seeded demo accounts, and cleans up
# after itself on exit (pass or fail) via the trap below.
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
GATEWAY_CONTAINER="${GATEWAY_CONTAINER:-gateway-service}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-banking-mysql}"

PASSWORD="Password123!"
TIMESTAMP=$(date +%s)
SENDER_EMAIL="e2e-sender-${TIMESTAMP}@example.com"
RECEIVER_EMAIL="e2e-receiver-${TIMESTAMP}@example.com"
FUND_AMOUNT="500.0000"
TRANSFER_AMOUNT="75.00"

PASS_COUNT=0
FAIL_COUNT=0
SENDER_ACCOUNT_ID=""
RECEIVER_ACCOUNT_ID=""

json_str() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
json_num() { echo "$1" | grep -o "\"$2\":[0-9.]*" | head -1 | cut -d: -f2; }

check() {
  local description="$1" actual="$2" expected="$3"
  if [ "$actual" = "$expected" ]; then
    echo "  PASS: $description ($actual)"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo "  FAIL: $description (expected $expected, got $actual)"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

# No deposit endpoint exists in this system by design (out of scope per the spec),
# so this script bootstraps the sender's funds directly and always removes its
# own test users/accounts/transactions afterward - runs on any exit, pass or fail.
cleanup() {
  if [ -n "$SENDER_ACCOUNT_ID" ] || [ -n "$RECEIVER_ACCOUNT_ID" ]; then
    docker exec "$MYSQL_CONTAINER" mysql -ubanking -pbankingpass banking -e "
      DELETE FROM transactions WHERE source_account_id IN (${SENDER_ACCOUNT_ID:-0}, ${RECEIVER_ACCOUNT_ID:-0})
         OR destination_account_id IN (${SENDER_ACCOUNT_ID:-0}, ${RECEIVER_ACCOUNT_ID:-0});
      DELETE FROM accounts WHERE id IN (${SENDER_ACCOUNT_ID:-0}, ${RECEIVER_ACCOUNT_ID:-0});
      DELETE FROM users WHERE email IN ('$SENDER_EMAIL', '$RECEIVER_EMAIL');
    " 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "== 1. Register sender and receiver =="
SENDER_REG=$(curl -sf -X POST "$GATEWAY_URL/api/v1/auth/register" -H "Content-Type: application/json" \
  -d "{\"fullName\":\"E2E Sender\",\"email\":\"$SENDER_EMAIL\",\"password\":\"$PASSWORD\"}")
RECEIVER_REG=$(curl -sf -X POST "$GATEWAY_URL/api/v1/auth/register" -H "Content-Type: application/json" \
  -d "{\"fullName\":\"E2E Receiver\",\"email\":\"$RECEIVER_EMAIL\",\"password\":\"$PASSWORD\"}")

SENDER_ACCOUNT_ID=$(json_num "$SENDER_REG" accountId)
RECEIVER_ACCOUNT_ID=$(json_num "$RECEIVER_REG" accountId)
echo "  sender account=$SENDER_ACCOUNT_ID receiver account=$RECEIVER_ACCOUNT_ID"

echo "== 2. Fund sender's account (bootstrap - no deposit endpoint exists) =="
docker exec "$MYSQL_CONTAINER" mysql -ubanking -pbankingpass banking \
  -e "UPDATE accounts SET balance = $FUND_AMOUNT WHERE id = $SENDER_ACCOUNT_ID;" 2>/dev/null

echo "== 3. Login as sender =="
LOGIN=$(curl -sf -X POST "$GATEWAY_URL/api/v1/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$SENDER_EMAIL\",\"password\":\"$PASSWORD\"}")
TOKEN=$(json_str "$LOGIN" token)
check "login returned a token" "$([ -n "$TOKEN" ] && echo yes || echo no)" "yes"

echo "== 4. Read sender balance before transfer via GET /api/v1/accounts/{id} =="
SENDER_BEFORE=$(curl -sf "$GATEWAY_URL/api/v1/accounts/$SENDER_ACCOUNT_ID" -H "Authorization: Bearer $TOKEN")
SENDER_BALANCE_BEFORE=$(json_num "$SENDER_BEFORE" balance)
echo "  sender balance before: $SENDER_BALANCE_BEFORE"

echo "== 5. Initiate transfer =="
INITIATE=$(curl -sf -X POST "$GATEWAY_URL/api/v1/transfers/initiate" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d "{\"sourceAccountId\":$SENDER_ACCOUNT_ID,\"destinationAccountId\":$RECEIVER_ACCOUNT_ID,\"amount\":$TRANSFER_AMOUNT,\"currency\":\"USD\",\"description\":\"e2e-flow verification\"}")
CHALLENGE_ID=$(json_str "$INITIATE" challengeId)
check "initiate returned a challengeId" "$([ -n "$CHALLENGE_ID" ] && echo yes || echo no)" "yes"

echo "== 6. Read OTP from gateway-service logs (mock delivery) =="
OTP=$(docker logs "$GATEWAY_CONTAINER" 2>&1 | grep "$CHALLENGE_ID" | grep -oE 'code=[0-9]{6}' | tail -1 | cut -d= -f2)
check "OTP extracted from logs" "$([ -n "$OTP" ] && echo yes || echo no)" "yes"

echo "== 7. Confirm transfer =="
IDEMPOTENCY_KEY="e2e-flow-${TIMESTAMP}"
CONFIRM=$(curl -sf -X POST "$GATEWAY_URL/api/v1/transfers/confirm" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -H "X-Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "{\"challengeId\":\"$CHALLENGE_ID\",\"otpCode\":\"$OTP\"}")
CONFIRM_STATUS=$(json_str "$CONFIRM" status)
check "confirm returned SUCCESS" "$CONFIRM_STATUS" "SUCCESS"

echo "== 8. Verify both balances after transfer via GET /api/v1/accounts/{id} =="
SENDER_AFTER=$(curl -sf "$GATEWAY_URL/api/v1/accounts/$SENDER_ACCOUNT_ID" -H "Authorization: Bearer $TOKEN")
RECEIVER_LOGIN=$(curl -sf -X POST "$GATEWAY_URL/api/v1/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$RECEIVER_EMAIL\",\"password\":\"$PASSWORD\"}")
RECEIVER_TOKEN=$(json_str "$RECEIVER_LOGIN" token)
RECEIVER_AFTER=$(curl -sf "$GATEWAY_URL/api/v1/accounts/$RECEIVER_ACCOUNT_ID" -H "Authorization: Bearer $RECEIVER_TOKEN")

SENDER_BALANCE_AFTER=$(json_num "$SENDER_AFTER" balance)
RECEIVER_BALANCE_AFTER=$(json_num "$RECEIVER_AFTER" balance)

EXPECTED_SENDER=$(awk "BEGIN { printf \"%.4f\", $SENDER_BALANCE_BEFORE - $TRANSFER_AMOUNT }")
EXPECTED_RECEIVER=$(awk "BEGIN { printf \"%.4f\", $TRANSFER_AMOUNT }")

check "sender balance debited by $TRANSFER_AMOUNT" "$SENDER_BALANCE_AFTER" "$EXPECTED_SENDER"
check "receiver balance credited by $TRANSFER_AMOUNT" "$RECEIVER_BALANCE_AFTER" "$EXPECTED_RECEIVER"

echo "== 9. Idempotency check: repeat confirm with the same X-Idempotency-Key =="
CONFIRM_REPEAT=$(curl -sf -X POST "$GATEWAY_URL/api/v1/transfers/confirm" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -H "X-Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "{\"challengeId\":\"$CHALLENGE_ID\",\"otpCode\":\"$OTP\"}")
CONFIRM_TXN=$(json_num "$CONFIRM" transactionId)
CONFIRM_REPEAT_TXN=$(json_num "$CONFIRM_REPEAT" transactionId)
check "repeated confirm returns the same transactionId (idempotent)" "$CONFIRM_REPEAT_TXN" "$CONFIRM_TXN"

echo
echo "=================================="
echo "Results: $PASS_COUNT passed, $FAIL_COUNT failed"
echo "=================================="
[ "$FAIL_COUNT" -eq 0 ]
