## Overview

The entire application consists of three separate servers, 

* FraudStatusHttpServer
* FraudStatusServer
* FraudScoreServer

So far the application only runs on a local machine, as far as I tested on my MacBook.

The original plan was to run this in Google Container Engine with Kubernetes but I wasn't able to get there yet...

## HOW TO RUN

### FraudScoreServer 

As in `src/main/resources/application.conf`, the Akka Cluster seed nodes are hardcoded as follows:

``` 
cluster {
  seed-nodes = [
    "akka.tcp://FraudCheckerCluster@127.0.0.1:2551",
    "akka.tcp://FraudCheckerCluster@127.0.0.1:2552"
  ]
}
```

So we pass the port number 2551, and 2552 ot FraudScoreServer to run them as the seed nodes.

Firstly launch FraudScoreServer from terminal with port 2551,

```
$ sbt runMain com.paidy.server.FraudScoreServer 2551
```

and in a different terminal tab,

```
$ sbt runMain com.paidy.server.FraudScoreServer 2552
```

### FraudStatusServer 

Launch FraudStatusServer as follows,

```
$ sbt runMain com.paidy.server.FraudStatusServer 2553
```

and in a different terminal, you can start up another instance:

```
$ sbt runMain com.paidy.server.FraudStatusServer 2554
```

You don't need to specify a port number as it will be a non-seed node in Akka Cluster.
However, a fixed port number makes things easy in case you restart the process. Otherwise,
you would need to manually remove the stopped `IP:Port` from the cluster.

### FraudStatusHttpServer 

Finally you can bring up an HTTP server instance.

```
$ sbt runMain com.paidy.server.FraudStatusHttpServer 2555
```

and see a message like below:

```
Server online at http://localhost:8080
```

### Curl to send a JSON request

curl command like below sends 

```
$ curl -H 'Content-Type:application/json' -d '{"city":"Tokyo","line1":"Minato-ku","line2":"Roppongi","state":"","zip":"106-0032" }' http://localhost:8080/check
```

## DESIGN

