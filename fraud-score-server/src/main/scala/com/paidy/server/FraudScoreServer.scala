package com.paidy.server

import akka.actor.{ActorRef, ActorSystem}
import com.paidy.authorizations.actors.FraudScoreGateway
import com.typesafe.config.ConfigFactory
/**
  * Created by yunishiyama on 2017/04/07.
  */
object FraudScoreServer {

  def main(args: Array[String]): Unit = {

    val portFromEnv = System.getenv("SEED_PORT")
    println(s"portFromEnv=${portFromEnv}")
    val port = if (args.size > 0) args(0) else if (portFromEnv != null) portFromEnv else "0"

    val ipFromEnv = System.getenv("THIS_IP")
    println(s"ipFromEnv=${ipFromEnv}")
    val internalIp = if(ipFromEnv != null) ipFromEnv else NetworkConfig.hostLocalAddress

    val appConfig = ConfigFactory.load("scoring-server")
    val clusterName = appConfig.getString("com.paidy.cluster-system")

    val config = ConfigFactory.parseString("akka.cluster.roles = [backend]").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")).
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.bind-hostname=$internalIp")).
      withFallback(NetworkConfig.seedsConfig(appConfig, clusterName)).
      withFallback(appConfig)

    println(s"akka.remote.netty.tcp.port=${config.getValue("akka.remote.netty.tcp.port")}")
    println(s"akka.remote.netty.tcp.bind-hostname=${config.getValue("akka.remote.netty.tcp.bind-hostname")}")
    println(s"akka.cluster.seed-nodes=${config.getValue("akka.cluster.seed-nodes")}")

    println(s"Launching scoring server with Actor System name = ${clusterName}, port=${config.getString("akka.remote.netty.tcp.port")}")

    implicit val system = ActorSystem(clusterName,config)
    implicit val executionContext = system.dispatcher

    val scorer: ActorRef = system.actorOf(FraudScoreGateway.props, "scorer")

    println("Scoring server started.")
  }
}
