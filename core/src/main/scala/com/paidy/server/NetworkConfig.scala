package com.paidy.server

import java.net.NetworkInterface
import java.net.InetAddress

import scala.collection.JavaConversions._
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

/**
  * Created by Admin on 2016-08-31.
  */
object NetworkConfig {

  def seedNodesIps: Seq[String] = Option(System.getenv("SEED_DISCOVERY_SERVICE")).
    map(InetAddress.getAllByName(_).map(_.getHostAddress).toSeq).
    getOrElse(Seq.empty)

  def seedNodesPorts: Seq[String] = Option(System.getenv("CLUSTER_PORT")).
    map(port => Seq.fill(seedNodesIps.size)(port)).getOrElse(Seq.empty)

  def seedsConfig(config: Config, clusterName: String): Config =
    if(!seedNodesIps.isEmpty)
      ConfigFactory.empty().withValue("akka.cluster.seed-nodes",
        ConfigValueFactory.fromIterable(seedNodesIps.zip(seedNodesPorts).
          map{case (ip, port) => s"akka.tcp://$clusterName@$ip:$port"}))
    else ConfigFactory.empty()
}
