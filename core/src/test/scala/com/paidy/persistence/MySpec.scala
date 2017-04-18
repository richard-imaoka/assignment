package com.paidy.persistence

import java.io.File

import akka.actor.ActorSystem
import akka.persistence.Persistence
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.FileUtils
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpecLike}

abstract class MySpec(config: Config, systemNane: String = "MySpec" )
  extends TestKit(ActorSystem(systemNane, config)) with WordSpecLike with BeforeAndAfterEach with BeforeAndAfterAll with Matchers {

  val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.journal.leveldb-shared.store.dir",
    "akka.persistence.snapshot-store.local.dir").map(s ⇒ new File(system.settings.config.getString(s)))

  final override def beforeAll {
    atStartup()
  }

  override def afterAll {
    shutdown()
    afterTermination()
  }

  protected def atStartup() {
    storageLocations.foreach(FileUtils.deleteDirectory)
  }

  protected def afterTermination() {
    storageLocations.foreach(FileUtils.deleteDirectory)
  }
}

object MySpec {
  def config(plugin: String, test: String, serialization: String = "on", extraConfig: Option[String] = None) =
    extraConfig.map(ConfigFactory.parseString(_))
      .getOrElse(ConfigFactory.empty())
      .withFallback(ConfigFactory.parseString(
        s"""
      akka.actor.serialize-creators = ${serialization}
      akka.actor.serialize-messages = ${serialization}
      akka.actor.warn-about-java-serializer-usage = off
      akka.persistence.publish-plugin-commands = on
      akka.persistence.journal.plugin = "akka.persistence.journal.${plugin}"
      akka.persistence.journal.leveldb.dir = "target/journal-${test}"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshots-${test}/"
      akka.test.single-expect-default = 10s
    """))
    .withFallback(ConfigFactory.load("test-reference"))

}

