#!/bin/bash

# Script to clear chat-related data from Redis cluster
# This removes old data with @class type information

echo "🗑️  Clearing Redis chat data..."

# Redis cluster nodes
REDIS_NODES=(
  "10.0.101.34:6379"
  "10.0.101.34:6380"
  "10.0.101.34:6381"
  "10.0.101.34:6382"
  "10.0.101.34:6383"
  "10.0.101.34:6384"
)

# Redis password
REDIS_PASSWORD="local"

# Patterns to delete
PATTERNS=(
  "chat:data:*"
  "session:user:*"
  "ratelimit:*"
)

echo "Connecting to Redis cluster nodes..."

for NODE in "${REDIS_NODES[@]}"; do
  echo ""
  echo "📍 Processing node: $NODE"

  for PATTERN in "${PATTERNS[@]}"; do
    echo "  🔍 Deleting keys matching: $PATTERN"

    # Use redis-cli to scan and delete keys
    # Note: This only deletes keys stored on this specific node
    redis-cli -h ${NODE%:*} -p ${NODE#*:} -a "$REDIS_PASSWORD" --scan --pattern "$PATTERN" | \
      xargs -r redis-cli -h ${NODE%:*} -p ${NODE#*:} -a "$REDIS_PASSWORD" DEL 2>/dev/null || true
  done
done

echo ""
echo "✅ Redis data cleared successfully!"
echo ""
echo "💡 Remaining keys count:"
for NODE in "${REDIS_NODES[@]}"; do
  COUNT=$(redis-cli -h ${NODE%:*} -p ${NODE#*:} -a "$REDIS_PASSWORD" --scan --pattern "chat:data:*" 2>/dev/null | wc -l)
  echo "  - $NODE: $COUNT keys"
done
