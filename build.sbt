name := """peer-assessment"""
organization := "com.baubek"
maintainer := "bekzatbaubek"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.7"

libraryDependencies ++= Seq(
  "com.google.inject" % "guice" % "5.0.1",
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test,
  "org.xerial" % "sqlite-jdbc" % "3.36.0.3",
  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.3",
  "org.mindrot" % "jbcrypt" % "0.4"
)

import com.typesafe.sbt.packager.docker._
dockerUpdateLatest := true
dockerUsername := Some("bekzatbaubek")
dockerExposedPorts := Seq(9000)

dockerCommands := Seq()
dockerCommands := Seq(
  Cmd("FROM", "alpine:latest"),
  Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),
  Cmd("USER", "root"),
  Cmd("EXPOSE", "9000"),
  ExecCmd("RUN", "apk", "add", "--no-cache", "openjdk11", "bash"),
  Cmd("WORKDIR", "/opt/docker"),
  ExecCmd("COPY", "opt", "/opt"),
  ExecCmd("RUN", "chmod", "+x", "/opt/docker/bin/peer-assessment"),
  ExecCmd("ENTRYPOINT", "/opt/docker/bin/peer-assessment"),
  ExecCmd("CMD")
)