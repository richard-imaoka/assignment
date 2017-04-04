lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.paidy",
      scalaVersion := "2.12.1",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "assignment",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.4.17",
      "com.typesafe.akka" %% "akka-http" % "10.0.5",
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.5",
      "org.scalatest" %% "scalatest" % "3.0.1" % Test
    )
  )
