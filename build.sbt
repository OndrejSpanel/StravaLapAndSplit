name := "StravaLapAndSplit"

scalaVersion := "2.11.8"

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

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.6"

libraryDependencies += "com.sparkjava" % "spark-core" % "1.1.1" excludeAll ExclusionRule(organization = "org.eclipse.jetty")

libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.6.1"

libraryDependencies += "commons-fileupload" % "commons-fileupload" % "1.3.2"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test"

libraryDependencies += "com.jsuereth" %% "scala-arm" % "1.4" exclude("org.scala-lang.plugins", "scala-continuations-library_" + scalaVersion.value.split('.').dropRight(1).mkString("."))

libraryDependencies += "org.apache.commons" % "commons-math" % "2.1"

val log4jVersion = "2.5"

libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % log4jVersion

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % log4jVersion

libraryDependencies += "org.apache.logging.log4j" % "log4j-1.2-api" % log4jVersion

appengineSettings
