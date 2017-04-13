package com.paidy.server;

import akka.actor.{ActorRef, ActorSystem}
import com.paidy.authorizations.actors.FraudStatusGateway
import com.typesafe.config.ConfigFactory

import scala.io.StdIn
/**
  * Created by yunishiyama on 2017/04/08.
  */
object FraudStatusServer {

  def main(args: Array[String]): Unit = {
    val port = if (args.isEmpty) "0" else args(0)

    val internalIp = NetworkConfig.hostLocalAddress

    val appConfig = ConfigFactory.load("scoring-server")
    val clusterName = appConfig.getString("com.paidy.cluster-system")

    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.bind-hostname=$internalIp")).
      withFallback(NetworkConfig.seedsConfig(appConfig, clusterName)).
      withFallback(appConfig)

    println(s"Launching cache server with Actor System name = ${clusterName}, port=${config.getString("akka.remote.netty.tcp.port")}")

    implicit val system = ActorSystem(clusterName,config)
    implicit val executionContext = system.dispatcher

    val cacher: ActorRef = system.actorOf(FraudStatusGateway.props, "cache")

    println("Caching server started.\nPress RETURN to stop...")
    StdIn.readLine()
    system.terminate()
  }
}
