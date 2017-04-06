package com.paidy.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.paidy.authorizations.actors.{AddressFraudProbabilityScorer, MiddleMan}
import com.paidy.authorizations.actors.MiddleMan.ScoreRequest
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

object FraudCheckerServer extends Directives with JsonSupport{

  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system",ConfigFactory.load())
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val middleman = system.actorOf(MiddleMan.props)
    implicit val timeout = Timeout(5 seconds)

    val route =
      path("check") {
        post {
          entity(as[Address]) { address =>
            println("received: ", address)
            val fut = middleman ? ScoreRequest(address)
            onComplete(fut){
              case Success(s) => {
                val status = s.asInstanceOf[Boolean]
                val returnJSON = ReturnJSON(status, address)
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