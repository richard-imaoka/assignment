package com.paidy.server

import java.util.UUID

import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.paidy.authorizations.actors.FraudStatusGateway
import com.paidy.authorizations.actors.FraudStatusGateway.{StatusRequest, StatusResponse}
import com.paidy.domain.Address
import com.typesafe.config.ConfigFactory
import spray.json._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object UuidJsonFormat extends RootJsonFormat[UUID] {
    def write(id: UUID) = JsString(id.toString)
    def read(value: JsValue): UUID =
      try {
        UUID.fromString(value.toString)
      }
      catch{
        case e: IllegalArgumentException =>
         deserializationError(s"${value} is not a valid string for UUID", e)
      }
  }

  implicit val addressFormat = jsonFormat6(Address) //to marshall/unmarshall (i.e. JSON <-> Address) for 5-parameter case class
  implicit val returnJSONFormat = jsonFormat2(ReturnJSON)
}

case class ReturnJSON(status: Boolean, address: Address)

//use logger instead of println
//read ip from config
//read port from config
//service failure handling

object FraudStatusHttpServer extends Directives with JsonSupport{

  def main(args: Array[String]) {

    val portFromEnv = System.getenv("THIS_PORT")
    println(s"portFromEnv=${portFromEnv}")
    val port = if (args.size > 0) args(0) else if (portFromEnv != null) portFromEnv else "0"

    val internalIp = NetworkConfig.hostLocalAddress

    val appConfig = ConfigFactory.load("scoring-server")
    val clusterName = appConfig.getString("com.paidy.cluster-system")

    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.bind-hostname=$internalIp")).
      withFallback(NetworkConfig.seedsConfig(appConfig, clusterName)).
      withFallback(appConfig)

    println(s"akka.remote.netty.tcp.port=${config.getValue("akka.remote.netty.tcp.port")}")
    println(s"akka.remote.netty.tcp.bind-hostname=${config.getValue("akka.remote.netty.tcp.bind-hostname")}")

    implicit val system = ActorSystem(clusterName, config)
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val mediator = DistributedPubSub(system).mediator
    implicit val timeout = Timeout(5 seconds)

    val route =
      path("check") {
        post {
          entity(as[Address]) { address =>
            println("received: ", address)
            val fut = mediator ? Send(path = FraudStatusGateway.path(address.addressID), msg = StatusRequest(address), localAffinity = false)
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

    println(s"Server online at http://localhost:8080/")
  }
}