package com.paidy.server

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.cluster.Cluster
import com.paidy.authorizations.actors.FraudScoreGateway
import com.typesafe.config.ConfigFactory
import sample.cluster.factorial.{FactorialBackend, MetricsListener, NetworkConfig}

import scala.io.StdIn

/**
  * Created by yunishiyama on 2017/04/07.
  */
object FraudScoreServer {

  def main(args: Array[String]): Unit = {

    val port = if (args.isEmpty) "0" else args(0)

    val internalIp = NetworkConfig.hostLocalAddress

    val appConfig = ConfigFactory.load("scoring-server")
    val clusterName = appConfig.getString("com.paidy.cluster-system")

    val config = ConfigFactory.parseString("akka.cluster.roles = [backend]").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")).
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.bind-hostname=$internalIp")).
      withFallback(NetworkConfig.seedsConfig(appConfig, clusterName)).
      withFallback(appConfig)

    println(s"Launching scoring server with Actor System name = ${clusterName}, port=${config.getString("akka.remote.netty.tcp.port")}")

    implicit val system = ActorSystem(clusterName,config)
    implicit val executionContext = system.dispatcher

    val scorer: ActorRef = system.actorOf(FraudScoreGateway.props, "scorer")

    Cluster(system)

    println("Scoring server started.\nPress RETURN to stop...")
    StdIn.readLine()
    system.terminate()
  }
}
