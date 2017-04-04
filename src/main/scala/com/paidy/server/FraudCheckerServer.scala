package com.paidy.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer._
import com.paidy.domain.Address
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val addressFormat = jsonFormat5(Address) //to unmarshall (i.e. JSON -> Address) for 5-parameter case class
}

object FraudCheckerServer extends Directives with JsonSupport{

  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val scorer = system.actorOf(AddressFraudProbabilityScorer.props)
    implicit val timeout = Timeout(10 seconds)

    val route =
      path("check") {
        get{
          val fut = scorer ? ScoreAddress(Address("line1", "line2", "city", "state", "zip"))
          complete(Await.result(fut, timeout.duration).toString)
        } ~
        post {
          entity(as[Address]) { address => // will unmarshal JSON to Order
            println("received: ", address) //change it to logger
            complete(s"line1: ${address.line1}")
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