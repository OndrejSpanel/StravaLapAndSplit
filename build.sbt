import sbt.Keys.scalacOptions

lazy val commonSettings = Seq(
  organization := "com.github.ondrejspanel",
  version := "v0.1.2-alpha",
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
)

lazy val commonLibs = Seq(
  "joda-time" % "joda-time" % "2.9.4",
  "org.joda" % "joda-convert" % "1.8.1"
)

val jacksonVersion = "2.8.3"


lazy val shared = (project in file("shared")).settings(
  commonSettings,
  libraryDependencies ++= commonLibs
)


lazy val pushUploader = (project in file("push-uploader"))
  .dependsOn(shared)
  .settings(
  name := "StravamatStart",
  commonSettings,

  //libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.3",
  libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.9",

  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.3",
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
      "com.google.http-client" % "google-http-client-appengine" % "1.22.0",
      "com.google.http-client" % "google-http-client-jackson2" % "1.22.0",
      "com.google.apis" % "google-api-services-storage" % "v1-rev92-1.22.0",
      "com.google.appengine.tools" % "appengine-gcs-client" % "0.6",

      "javax.servlet" % "servlet-api" % "2.5" % "provided",
      "org.mortbay.jetty" % "jetty" % "6.1.22" % "container",
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
      "commons-io" % "commons-io" % "2.1"
    )
  )

