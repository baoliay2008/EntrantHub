ThisBuild / version      := "0.1.0"
ThisBuild / scalaVersion := "3.7.2"


ThisBuild / scalacOptions ++= Seq(
  "-language:noAutoTupling"
)


lazy val root = (project in file("."))
  .settings(name := "entranthub")


val PekkoVersion     = "1.1.5"
val PekkoHttpVersion = "1.3.0"


libraryDependencies ++= Seq(
  // https://www.scalatest.org
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  // https://central.sonatype.com/artifact/org.slf4j/slf4j-api
  "org.slf4j" % "slf4j-api" % "2.0.17",
  // https://central.sonatype.com/artifact/ch.qos.logback/logback-classic
  "ch.qos.logback" % "logback-classic" % "1.5.18",
  // https://central.sonatype.com/artifact/io.github.cdimascio/dotenv-java
  "io.github.cdimascio" % "dotenv-java" % "3.2.0",
  // https://central.sonatype.com/artifact/org.apache.kafka/kafka-clients
  "org.apache.kafka" % "kafka-clients" % "4.0.0",
  // https://com-lihaoyi.github.io/upickle/
  "com.lihaoyi" %% "upickle" % "4.2.1",
  // https://scala-slick.org/doc/stable/gettingstarted.html
  "com.typesafe.slick" %% "slick"          % "3.6.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.6.1",
  // https://central.sonatype.com/artifact/org.postgresql/postgresql
  "org.postgresql" % "postgresql" % "42.7.7",
  // https://central.sonatype.com/artifact/org.apache.commons/commons-math3
  "org.apache.commons" % "commons-math3" % "3.6.1",
  // https://central.sonatype.com/artifact/org.jsoup/jsoup
  "org.jsoup" % "jsoup" % "1.21.1",
  // https://pekko.apache.org/docs/pekko-http/current/introduction.html
  "org.apache.pekko" %% "pekko-actor-typed" % PekkoVersion,
  "org.apache.pekko" %% "pekko-stream"      % PekkoVersion,
  "org.apache.pekko" %% "pekko-http"        % PekkoHttpVersion,
  "org.apache.pekko" %% "pekko-http-cors"   % PekkoHttpVersion,
)


// Assembly settings
assembly / mainClass := Some("App")


assembly / assemblyMergeStrategy := {
  case PathList("module-info.class") => MergeStrategy.discard
  case x                             => (assembly / assemblyMergeStrategy).value(x)
}


enablePlugins(AssemblyPlugin)
