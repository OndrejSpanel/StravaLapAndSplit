name := "StravaLapAndSplit"

scalaVersion := "2.11.8"

enablePlugins(SbtTwirl)

libraryDependencies ++= Seq(
  "com.google.http-client" % "google-http-client-appengine" % "1.22.0",
  "com.google.http-client" % "google-http-client-jackson" % "1.22.0",
  "javax.servlet" % "servlet-api" % "2.5" % "provided",
  "org.mortbay.jetty" % "jetty" % "6.1.22" % "container",
  "joda-time" % "joda-time" % "2.9.4",
  "org.joda" % "joda-convert" % "1.8.1",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.8.2",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.2"
)

libraryDependencies += "com.sparkjava" % "spark-core" % "1.1.1"

appengineSettings
