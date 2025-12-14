docker exec -it redis redis-cli -a '{PASSWORD}' --cluster create \
  {IP} \
  --cluster-replicas 1
