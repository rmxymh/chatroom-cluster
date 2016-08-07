name := "chatroom"

version := "0.0.1"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.8",
  "com.typesafe.akka" %% "akka-remote" % "2.4.8",
  "com.typesafe.akka" %% "akka-cluster" % "2.4.8"
)

