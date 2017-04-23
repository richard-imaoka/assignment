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
import com.paidy.authorizations.actors.FraudStatusGateway.{GetHistoricalScores, StatusRequest, StatusResponse}
import com.paidy.domain.Address
import com.paidy.identifiers.actors.AddressIdManager
import com.paidy.identifiers.actors.AddressIdManager.{IdRequest, IdResponse}
import com.typesafe.config.ConfigFactory
import spray.json._

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.util.{Failure, Success}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object UuidJsonFormat extends RootJsonFormat[UUID] {
    def write(id: UUID) = JsString(id.toString)
    def read(value: JsValue): UUID = value match {
      case jstr: JsString =>
        try {
          UUID.fromString(jstr.value)
        }
        catch{
          case e: IllegalArgumentException =>
            deserializationError(s"${jstr.value} is not a valid string for UUID", e)
        }
      case _ =>
        deserializationError(s"${value} is not a valid string for UUID")
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

    val overrideConfig = if (args.size > 0) { s"akka.remote.netty.tcp.port=${args(0)}" } else ""

    val appConfig = ConfigFactory.load()
    val clusterName = appConfig.getString("clustering.system")

    val config = ConfigFactory.parseString(overrideConfig).
      withFallback(NetworkConfig.seedsConfig(appConfig, clusterName)).
      withFallback(appConfig)

    println(s"akka.remote.netty.tcp.port=${config.getValue("akka.remote.netty.tcp.port")}")
    println(s"akka.remote.netty.tcp.hostname=${config.getValue("akka.remote.netty.tcp.hostname")}")

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
      } ~ path("address-id") {
        post {
          println("new address ID request received")
          val fut = mediator ? Send(path = AddressIdManager.path, msg = IdRequest, localAffinity = false)
          onComplete(fut) {
            case Success(response) =>
              println(response)
              complete(response.asInstanceOf[IdResponse].addressID)
            case Failure(e) =>
              failWith(e)
          }
        }
      } ~ path("address-scores" / JavaUUID) { uuid =>
        get {
          println(s"address-scores request received for ${uuid}")
          val fut = mediator ? Send(path = FraudStatusGateway.path(uuid), msg = GetHistoricalScores, localAffinity = false)
          onComplete(fut) {
            case Success(response) =>
              complete(response.asInstanceOf[Queue[Double]].toList.toJson)
            case Failure(e) =>
              failWith(e)
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, config.getString("akka.remote.netty.tcp.hostname"), 8080)

    println(s"Server online at http://${config.getString("akka.remote.netty.tcp.hostname")}:8080/")
  }
}