docker exec -it mongo mongosh --eval 'rs.initiate({
  _id: "rs0",
  members: [
    { _id: 0, host: "10.0.101.58:27017", priority: 2 },
    { _id: 1, host: "10.0.101.125:27017", priority: 1 },
    { _id: 2, host: "10.0.101.202:27017", priority: 1 }
  ]
});
rs.status();
'
