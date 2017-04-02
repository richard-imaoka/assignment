package com.paidy.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer._
import com.paidy.domain.Address

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn

object FraudCheckerServer {

  val idToAddressMapping = Map(
    "1234" -> Address("line1", "line2", "city", "state", "zip")
  )

  def resolveToAddress(id: String): Option[Address] ={
    idToAddressMapping.get(id)
  }

  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val scorer = system.actorOf(AddressFraudProbabilityScorer.props)
    implicit val timeout = Timeout(10 seconds)

    val route =
      path("check") {
        val fut = scorer ? ScoreAddress(Address("line1", "line2", "city", "state", "zip"))
        complete(Await.result(fut, timeout.duration).toString)
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}