curl -H 'Content-Type:application/json' -d  "{ \"addressID\":\"$1\", \"city\":\"Tokyo\",\"line1\":\"Minato-ku\",\"line2\":\"Roppongi\",\"state\":\"\",\"zip\":\"106-0032\" }" http://localhost:8080/check

# curl -H 'Content-Type:application/json' -d '{\"city\":\"Tokyo\",\"line1\":\"Minato-ku\",\"line2\":\"Roppongi\",\"state\":\"\",\"zip\":\"106-0032\" }' http://localhost:8080/check