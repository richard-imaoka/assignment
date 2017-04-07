package com.paidy.server

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import com.paidy.authorizations.actors.AddressFraudProbabilityScorer
import com.typesafe.config.ConfigFactory

import scala.io.StdIn

/**
  * Created by yunishiyama on 2017/04/07.
  */
object ScoringServer {

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem("scoring-server",ConfigFactory.load())
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val scorer: ActorRef = system.actorOf(AddressFraudProbabilityScorer.props)

    println("Scoring server started.\nPress RETURN to stop...")
    StdIn.readLine()
    system.terminate()
  }
}
