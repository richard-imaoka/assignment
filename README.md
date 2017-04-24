## Overview

The application is running in Kubernetes at http://

The entire application consists of four separate servers, 

* FraudStatusHttpServer
* FraudStatusServer
* FraudScoreServer
* FraudIdManagerServer

## HOW TO RUN

### Run a fraud check for a new address 

1. To use utility shell scripts, please git clone this repository

`git clone git@github.com:richard-imaoka/assignment.git`

2. Run the following to get an address ID (UUID)

`curl -X POST http://***/address-id`

<img src="images/architecture1.png"  width="600">

3. Create address JSON with the address ID obtained in 2.

`./address-json.sh e72db56b-0c4e-415a-9cbb-0cc14d540392`

4. Send the address JSON from 3. to the fraud check http server

`./address-json.sh e72db56b-0c4e-415a-9cbb-0cc14d540392 | ./curl-json.sh `

<img src="images/architecture1.png" width="600">

5. You get a response JSON with fraud check status, if not timed out 

### More actions

6. Access to the following from your browser, to see all existing address IDs 

7. Pick any of address ID from 6. and substitute it in the following URL, to check historical fraud check scores for the address ID

## DESIGN

### Uniqueness of address by address ID 

* I introduced address ID, which is to distinguish an address from another 
  * This was not in assignment's specification
  * Even if we don't have any ID for an address, we need to know whether a given address is fraud-checked first time or it is same as previous one
  * So, introducing an ID would be practical

### Scalability 

<img src="images/individual.png" width="480">

The reason for having the separate servers is to allow you scaling each of them individually.
All these servers can communicate to each other using [Akka Cluster](http://doc.akka.io/docs/akka/2.5/scala/cluster-usage.html).

### FraudStatusHttpServer

<img src="images/FraudStatusHttpServer.png" width="200">

Here are URLs used in the HTTP server:

* /address-id
  * POST: create a new address ID
  * GET: get all existing address IDs
* /check
  * POST: perform fraud status check for the given address
* /address-scores:{address-id-UUID-string}
  * GET: get historical fraud check scores for the address ID

`FraudStatusHttpServer` is written in [Akka HTTP](http://doc.akka.io/docs/akka-http/current/scala.html)

### FraudIdManagerServer

This server keeps track of all existing address IDs (UUID)

<img src="images/FraudIdManager.png" width="480">

* Upon receiving a new `IdRequest`, 
  * it generates a new address ID
  * then asks `FraudStatusServer` to create a new `FraudStatusGateway` actor for the new address ID
* It uses [PersistentActor](http://doc.akka.io/docs/akka/2.5/scala/persistence.html) so that it remembers address IDs even when it is down

### FraudStatusServer

<img src="images/FraudStatusServer1.png" width="480">

* `FraudStatusServer` holds multiple `FraudStatusGateway` actors
* Each `FraudStatusGateway` actor is for one address ID
* [Akka's distributed pub sub send](http://doc.akka.io/docs/akka/2.4.17/scala/distributed-pub-sub.html#Send) is used to locate each actor

<img src="images/FraudStatusServer2.png" width="480">

* When `FraudStatusGateway` receives a `StatusRequest`, it eventually returns `StatusResponse` with either `true` or `false` status of fraud-check 
  * `FraudStatusGateway` asks `FraudScoreServer` for a new fraud score
  * If the score is < 0.78, the fraud-check status is `true`, otherwise `false`
  * When there are 10 historical fraud-check scores in the past (stored in `FraudStatusGateway`) **AND** their average > 0.7 `FraudStatusGateway` returns early with the status = `false`
  
### FraudScoreServer

<img src="images/FraudScoreServer.png" width="480">


### Caveats

* `FraudIdManager` became a single point of failure ... 
* Currently using MySQL for Akka Persistence storage, but better to use more distributed stores like Cassandra for performance
