package com.paidy.server

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.Cluster
import com.paidy.authorizations.actors.ScoreHistoryCacher
import com.typesafe.config.ConfigFactory

import scala.io.StdIn

/**
  * Created by yunishiyama on 2017/04/08.
  */
object ScoreHistoryCacheServer {

  def main(args: Array[String]): Unit = {
    val port: String = if( args.size > 0 ) args(0) else "0" //0 assigns a random port number

    val config =
      ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
        .withFallback(ConfigFactory.load("cache-server"))

    val systemName = config.getString("com.paidy.cluster-system")

    println(s"Launching cache server with Actor System name = ${systemName}, port=${config.getString("akka.remote.netty.tcp.port")}")

    implicit val system = ActorSystem(systemName,config)
    implicit val executionContext = system.dispatcher

    val cacher: ActorRef = system.actorOf(ScoreHistoryCacher.props, "cache")

    println("Caching server started.\nPress RETURN to stop...")
    StdIn.readLine()
    system.terminate()
  }
}
