name := "deepdive"

version := "0.1"

scalaVersion := "2.10.3"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "spray" at "http://repo.spray.io/"

resolvers += Resolver.sonatypeRepo("public")

libraryDependencies ++= List(
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.3",
  "com.typesafe.akka" %% "akka-slf4j" % "2.2.3",
  "ch.qos.logback" % "logback-classic" % "1.0.7",
  "com.typesafe" % "config" % "1.0.2",
  "com.github.scopt" %% "scopt" % "3.2.0",
  "org.scalatest" % "scalatest_2.10" % "2.0.RC2" % "test",
  "play" %% "anorm" % "2.1.5",
  "com.github.seratch" %% "scalikejdbc" % "[0.5,)",
  "org.postgresql" % "postgresql" % "9.2-1003-jdbc4",
  "com.h2database" % "h2" % "1.3.166",
  "io.spray" %%  "spray-json" % "1.2.5",
  "net.sf.opencsv" % "opencsv" % "2.3",
  "com.netflix.rxjava" % "rxjava-scala" % "0.15.1"
)

parallelExecution in Test := false