package com.paidy.server

import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.paidy.authorizations.actors.FraudStatusGateway.{StatusRequest, StatusResponse}
import com.paidy.domain.Address
import com.typesafe.config.ConfigFactory
import spray.json._

import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val addressFormat = jsonFormat5(Address) //to marshall/unmarshall (i.e. JSON <-> Address) for 5-parameter case class
  implicit val returnJSONFormat = jsonFormat2(ReturnJSON)
}

case class ReturnJSON(status: Boolean, address: Address)

//use logger instead of println
//read ip from config
//read port from config
//service failure handling

object FraudStatusHttpServer extends Directives with JsonSupport{

  def main(args: Array[String]) {
    val port: String = if( args.size > 0 ) args(0) else "0" //0 assigns a random port number

    val config =
      ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
        .withFallback(ConfigFactory.load("fraud-checker-http"))

    val systemName = config.getString("com.paidy.cluster-system")

    implicit val system = ActorSystem(systemName, config)
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val mediator = DistributedPubSub(system).mediator
    implicit val timeout = Timeout(5 seconds)

    val route =
      path("check") {
        post {
          entity(as[Address]) { address =>
            println("received: ", address)
            val fut = mediator ? Send(path = "/user/cache", msg = StatusRequest(address), localAffinity = false)
            onComplete(fut){
              case Success(r) => {
                val response = r.asInstanceOf[StatusResponse]
                val returnJSON = ReturnJSON(response.status, response.address)
                println(returnJSON)
                complete(returnJSON)
              }
              case Failure(ex) => {
                failWith(ex) // this doesn't expose the error details to the HTTP client but just gives HTTP 500
              }
            }
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}