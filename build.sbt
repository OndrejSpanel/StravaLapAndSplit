import sbt.Keys.scalacOptions

lazy val commonSettings = Seq(
  organization := "com.github.ondrejspanel",
  version := "0.1.7-alpha",
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
)

lazy val commonLibs = Seq(
  "joda-time" % "joda-time" % "2.9.4",
  "org.joda" % "joda-convert" % "1.8.1",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
)

val jacksonVersion = "2.8.3"

lazy val shared = (project in file("shared"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    commonSettings,
    libraryDependencies ++= commonLibs
  )


lazy val pushUploader = (project in file("push-uploader"))
  .enablePlugins(sbtassembly.AssemblyPlugin)
  .dependsOn(shared)
  .settings(
    name := "StravamatStart",
    commonSettings,
    libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.9",
    libraryDependencies ++= commonLibs
  )



lazy val stravamat = (project in file("."))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .dependsOn(shared)
  .settings(
    appengineSettings,

    name := "Stravamat",

    commonSettings,

    libraryDependencies ++= commonLibs ++ Seq(
      "com.google.http-client" % "google-http-client-appengine" % "1.23.0",
      "com.google.http-client" % "google-http-client-jackson2" % "1.23.0",
      "com.google.apis" % "google-api-services-storage" % "v1-rev113-1.23.0",
      "com.google.appengine.tools" % "appengine-gcs-client" % "0.6",

      "javax.servlet" % "servlet-api" % "2.5" % "provided",
      "org.eclipse.jetty" % "jetty-server" % "9.3.18.v20170406" % "container",

      "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,

      "com.fasterxml" % "aalto-xml" % "1.0.0",

      "fr.opensagres.xdocreport.appengine-awt" % "appengine-awt" % "1.0.0",

      "com.sparkjava" % "spark-core" % "1.1.1" excludeAll ExclusionRule(organization = "org.eclipse.jetty"),
      "org.slf4j" % "slf4j-simple" % "1.6.1",
      "commons-fileupload" % "commons-fileupload" % "1.3.2",
      "com.jsuereth" %% "scala-arm" % "2.0" exclude(
        "org.scala-lang.plugins", "scala-continuations-library_" + scalaBinaryVersion.value
      ),
      "org.apache.commons" % "commons-math" % "2.1",
      "commons-io" % "commons-io" % "2.1"
    )
  )

