package com.paidy.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer._
import com.paidy.domain.Address
import spray.json._

import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val addressFormat = jsonFormat5(Address) //to marshall/unmarshall (i.e. JSON <-> Address) for 5-parameter case class
  implicit val whatSoEverFormat = jsonFormat2(ReturnJSON)
}

case class ReturnJSON(status: Boolean, address: Address)

object FraudCheckerServer extends Directives with JsonSupport{

  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val scorer = system.actorOf(AddressFraudProbabilityScorer.props)
    implicit val timeout = Timeout(10 seconds)

    val route =
      path("check") {
        post {
          entity(as[Address]) { address => // will unmarshal JSON to Order
            println("received: ", address) //change it to logger
            val fut = scorer ? ScoreAddress(address)
            onComplete(fut){
              case Success(score) => {
                println("score: ", score)
                val status = score.asInstanceOf[Double] >= 0.78
                complete(ReturnJSON(status, address))
              }
              case Failure(ex) => {
                println(ex)
                complete("error happened")
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