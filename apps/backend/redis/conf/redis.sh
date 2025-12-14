docker exec -it redis redis-cli -a 'local' --cluster create \
  10.0.101.34:6379 10.0.101.159:6379 10.0.101.154:6379 \
  10.0.101.245:6379 10.0.101.212:6379 10.0.101.239:6379 \
  --cluster-replicas 1
