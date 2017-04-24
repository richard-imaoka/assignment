lazy val root = (project in file("."))
  .aggregate(core, fraudIdManager, fraudScoreServer, fraudStatusServer, fraudStatusHttpServer)

lazy val core = (project in file("core")).
  settings(
    inThisBuild(List(
      organization := "com.paidy",
      scalaVersion := "2.11.8",
      version      := "0.1.0-SNAPSHOT",
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.4.17",
        "com.typesafe.akka" %% "akka-cluster" % "2.4.17",
        "com.typesafe.akka" %% "akka-cluster-tools" % "2.4.17", //cluster distributed pub-sub
        "com.typesafe.akka" %% "akka-persistence" % "2.4.17",
        "com.typesafe.akka" %% "akka-http" % "10.0.5",
        "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.5",
        //for akka persistense
        "org.iq80.leveldb"          % "leveldb"        % "0.7",
        "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
        "com.okumin" %% "akka-persistence-sql-async" % "0.4.0",
        "com.github.mauricio" %% "mysql-async" % "0.2.20",
        "org.slf4j" % "slf4j-log4j12" % "1.7.2",
        //for tests
        "commons-io" % "commons-io" % "2.5"  % "test",
        "org.scalatest" %% "scalatest" % "3.0.1" % Test,
        "com.typesafe.akka" %% "akka-testkit" % "2.4.17" % Test
      ),
      fork in run := true,
      fork in Test := true
    )),
    name := "core"

  )

lazy val fraudIdManager = (project in file("fraud-id-manager"))
  .settings(
    name := "fraud-id-manager",
    dockerBaseImage := "flangelier/scala"
  )
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)

lazy val fraudScoreServer = (project in file("fraud-score-server"))
  .settings(
    name := "fraud-score-server",
    dockerBaseImage := "flangelier/scala"
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
