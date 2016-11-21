name := "Stravamat"

scalaVersion := "2.11.8"

val log4jVersion = "2.5"
val jacksonVersion = "2.8.3"

libraryDependencies ++= Seq(
  "com.google.http-client" % "google-http-client-appengine" % "1.22.0",
  "com.google.http-client" % "google-http-client-jackson2" % "1.22.0",
  "javax.servlet" % "servlet-api" % "2.5" % "provided",
  "org.mortbay.jetty" % "jetty" % "6.1.22" % "container",
  "joda-time" % "joda-time" % "2.9.4",
  "org.joda" % "joda-convert" % "1.8.1",
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
  "com.sparkjava" % "spark-core" % "1.1.1" excludeAll ExclusionRule(organization = "org.eclipse.jetty"),
  "org.slf4j" % "slf4j-simple" % "1.6.1",
  "commons-fileupload" % "commons-fileupload" % "1.3.2",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "com.jsuereth" %% "scala-arm" % "1.4" exclude(
    "org.scala-lang.plugins", "scala-continuations-library_" + scalaBinaryVersion.value
  ),
  "org.apache.commons" % "commons-math" % "2.1",
  "org.apache.logging.log4j" % "log4j-core" % log4jVersion,
  "org.apache.logging.log4j" % "log4j-api" % log4jVersion,
  "org.apache.logging.log4j" % "log4j-1.2-api" % log4jVersion
)


appengineSettings
