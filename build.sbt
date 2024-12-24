import Dependencies._

ThisBuild / scalaVersion     := "3.3.4"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.stoufexis"
ThisBuild / organizationName := "stoufexis"

lazy val compileFlags: Seq[String] =
  Seq(
    "-Ykind-projector:underscores",
    "-Wvalue-discard",
    "-Wunused:implicits",
    "-Wunused:explicits",
    "-Wunused:imports",
    "-Wunused:locals",
    "-Wunused:params",
    "-Wunused:privates",
    "-source:future",
    "-new-syntax"
  )

lazy val http: Seq[ModuleID] =
  Seq(
    "org.http4s" %% "http4s-ember-server" % "0.23.30",
    "org.http4s" %% "http4s-dsl"          % "0.23.30",
    "org.http4s" %% "http4s-core"         % "0.23.30"
  )

lazy val effect: Seq[ModuleID] =
  Seq(
    "co.fs2"        %% "fs2-core"    % "3.11.0",
    "org.typelevel" %% "cats-effect" % "3.5.7",
    "org.typelevel" %% "cats-core"   % "2.12.0"
  )

lazy val root = (project in file("."))
  .settings(
    name := "subtub",
    scalacOptions ++= compileFlags,
    libraryDependencies ++= http ++ effect,
    libraryDependencies += munit % Test
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
