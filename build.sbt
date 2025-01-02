ThisBuild / scalaVersion     := "3.3.4"
ThisBuild / version          := "0.1.0"
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
    "org.http4s" %% "http4s-core"         % "0.23.30",
    "org.http4s" %% "http4s-circe"        % "0.23.30"
  )

lazy val effect: Seq[ModuleID] =
  Seq(
    "co.fs2"        %% "fs2-core"       % "3.11.0",
    "org.typelevel" %% "cats-effect"    % "3.5.7",
    "org.typelevel" %% "cats-core"      % "2.12.0",
    "org.typelevel" %% "alleycats-core" % "2.12.0"
  )

lazy val serdes: Seq[ModuleID] =
  Seq(
    "org.scodec" %% "scodec-core"   % "2.3.1",
    "io.circe"   %% "circe-core"    % "0.14.1",
    "io.circe"   %% "circe-generic" % "0.14.1",
    "io.circe"   %% "circe-parser"  % "0.14.1"
  )

lazy val log: Seq[ModuleID] =
  Seq(
    "org.typelevel" %% "log4cats-core"   % "2.6.0",
    "org.typelevel" %% "log4cats-slf4j"  % "2.6.0",
    "ch.qos.logback" % "logback-classic" % "1.5.6"
  )

lazy val test: Seq[ModuleID] =
  Seq(
    "com.disneystreaming" %% "weaver-cats" % "0.8.4" % Test
  )

lazy val root = (project in file("."))
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(
    name := "subtub",
    scalacOptions ++= compileFlags,
    libraryDependencies ++= http ++ effect ++ serdes ++ log ++ test,
    // docker
    dockerBaseImage := "openjdk:11",
    dockerRepository := Some("stefanostouf"),
    dockerUpdateLatest := true
  )
