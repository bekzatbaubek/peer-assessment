name := """peer-assessment"""
organization := "com.baubek"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.7"

libraryDependencies ++= Seq(
  "com.google.inject" % "guice" % "5.0.1",
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test,
  jdbc,
  "org.xerial" % "sqlite-jdbc" % "3.36.0.3",
  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.3",
  "org.mindrot" % "jbcrypt" % "0.4"
)

import com.typesafe.sbt.packager.docker._
dockerBaseImage := "alpine:latest"
dockerExposedPorts := Seq(9000)
dockerCommands ++= Seq(
  Cmd("USER", "root"),
  ExecCmd("RUN", "apk", "add", "--no-cache", "openjdk11", "bash"),
)