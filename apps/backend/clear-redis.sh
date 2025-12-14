#!/bin/bash

# Clear Redis data script - Run this on EC2 instances

echo "🗑️  Clearing Redis chat data from cluster..."

# Source .env file to get Redis configuration
if [ -f .env ]; then
  export $(cat .env | grep -v '^#' | xargs)
else
  echo "❌ .env file not found!"
  exit 1
fi

# Parse Redis cluster nodes
IFS=',' read -ra NODES <<< "$REDIS_CLUSTER_NODES"

echo "Found ${#NODES[@]} Redis nodes in cluster"

# Patterns to delete
declare -a PATTERNS=(
  "chat:data:*"
  "session:user:*"
  "ratelimit:*"
)

TOTAL_DELETED=0

for NODE in "${NODES[@]}"; do
  # Remove redis:// prefix if present
  NODE="${NODE#redis://}"
  NODE="${NODE// /}"

  echo ""
  echo "📍 Clearing node: $NODE"

  for PATTERN in "${PATTERNS[@]}"; do
    echo "  🔍 Pattern: $PATTERN"

    # Count keys before deletion
    KEY_COUNT=$(redis-cli -h ${NODE%:*} -p ${NODE#*:} -a "$REDIS_PASSWORD" --no-auth-warning --scan --pattern "$PATTERN" 2>/dev/null | wc -l)

    if [ "$KEY_COUNT" -gt 0 ]; then
      echo "    Found $KEY_COUNT keys"

      # Delete keys
      redis-cli -h ${NODE%:*} -p ${NODE#*:} -a "$REDIS_PASSWORD" --no-auth-warning --scan --pattern "$PATTERN" 2>/dev/null | \
        xargs -r redis-cli -h ${NODE%:*} -p ${NODE#*:} -a "$REDIS_PASSWORD" --no-auth-warning DEL 2>/dev/null

      TOTAL_DELETED=$((TOTAL_DELETED + KEY_COUNT))
      echo "    ✅ Deleted $KEY_COUNT keys"
    else
      echo "    No keys found"
    fi
  done
done

echo ""
echo "✅ Cleared $TOTAL_DELETED keys total from Redis cluster"
echo ""
