lazy val root = (project in file("."))
  .aggregate(core, fraudScoreServer, fraudStatusServer, fraudStatusHttpServer)

lazy val core = (project in file("core")).
  settings(
    inThisBuild(List(
      organization := "com.paidy",
      scalaVersion := "2.12.1",
      version      := "0.1.0-SNAPSHOT",
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.4.17",
        "com.typesafe.akka" %% "akka-cluster" % "2.4.17",
        "com.typesafe.akka" %% "akka-cluster-tools" % "2.4.17", //cluster distributed pub-sub
        "com.typesafe.akka" %% "akka-persistence" % "2.5.0",
        "com.typesafe.akka" %% "akka-http" % "10.0.5",
        "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.5",
        //for akka persistense
        "org.iq80.leveldb"          % "leveldb"        % "0.7",
        "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
        //for tests
        "org.scalatest" %% "scalatest" % "3.0.1" % Test
      )
    )),
    name := "core"
  )


lazy val fraudScoreServer = (project in file("fraud-score-server"))
  .settings(
    name := "fraud-score-server",
    dockerBaseImage in Docker := "flangelier/scala"
  )
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)

lazy val fraudStatusServer = (project in file("fraud-status-server"))
  .settings(
    name := "fraud-status-server",
    dockerBaseImage := "flangelier/scala"
  )
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)

lazy val fraudStatusHttpServer = (project in file("fraud-status-http-server"))
  .settings(
    name := "fraud-status-http-server",
    dockerBaseImage := "flangelier/scala"
  )
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
