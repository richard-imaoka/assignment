package com.paidy.server

import akka.actor.ActorSystem
import com.paidy.identifiers.actors.AddressIdManager
import com.typesafe.config.ConfigFactory
/**
  * Created by yunishiyama on 2017/04/07.
  */
object FraudIdManagerServer {

  def main(args: Array[String]): Unit = {

    val overrideConfig = if (args.size > 0) { s"akka.remote.netty.tcp.port=${args(0)}" } else ""

    val appConfig = ConfigFactory.load()
    val clusterName = appConfig.getString("clustering.system")

    val config = ConfigFactory.parseString(overrideConfig).
      withFallback(NetworkConfig.seedsConfig(appConfig, clusterName)).
      withFallback(appConfig)

    println(s"akka.remote.netty.tcp.port=${config.getValue("akka.remote.netty.tcp.port")}")
    println(s"akka.remote.netty.tcp.hostname=${config.getValue("akka.remote.netty.tcp.hostname")}")
    println(s"Launching ${getClass.getSimpleName} with Actor System name = ${clusterName}")

    implicit val system = ActorSystem(clusterName,config)
    implicit val executionContext = system.dispatcher

    system.actorOf(AddressIdManager.props, AddressIdManager.name)
  }
}
