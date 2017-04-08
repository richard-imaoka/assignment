package com.paidy.server

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import akka.cluster.Cluster
import com.paidy.authorizations.actors.ScorerDestination
import com.typesafe.config.ConfigFactory

import scala.io.StdIn

/**
  * Created by yunishiyama on 2017/04/07.
  */
object ScoringServer {

  def main(args: Array[String]): Unit = {

    val port: String = if( args.size > 0 ) args(0) else "0" //0 assigns a random port number

    val config =
      ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
      .withFallback(ConfigFactory.load("scoring-server"))

    val systemName = config.getString("com.paidy.server.actor-system")

    println(s"Launching scoring server with Actor System name = ${systemName}, port=${config.getString("akka.remote.netty.tcp.port")}")

    implicit val system = ActorSystem(systemName,config)
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val scorer: ActorRef = system.actorOf(ScorerDestination.props)

    Cluster(system)

    println("Scoring server started.\nPress RETURN to stop...")
    StdIn.readLine()
    system.terminate()
  }
}
