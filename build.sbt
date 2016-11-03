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

libraryDependencies += "com.sparkjava" % "spark-core" % "1.1.1"

libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.6.1"

libraryDependencies += "commons-fileupload" % "commons-fileupload" % "1.3.2"

libraryDependencies += "org.javassist" % "javassist" % "3.18.2-GA"

appengineSettings
