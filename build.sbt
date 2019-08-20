import sbt.Keys.scalacOptions

lazy val commonSettings = Seq(
  organization := "com.github.ondrejspanel",
  version := "0.1.10-beta",
  scalaVersion := "2.11.12",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
)

lazy val commonLibs = Seq(
  "joda-time" % "joda-time" % "2.10",
  "org.joda" % "joda-convert" % "1.8.1",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
)

val jacksonVersion = "2.9.9"

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
    name := "MixtioStart",
    commonSettings,
    libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.9",
    libraryDependencies ++= commonLibs
  )

def inDevMode = sys.props.get("dev.mode").exists(value => value.equalsIgnoreCase("true"))

def addJavaScriptToServerResources(): Def.SettingsDefinition = {
  if (inDevMode) {
    println("SBT for Scala.js example app is in dev mode")
    (resources in Compile) += (fastOptJS in(js, Compile)).value.data
  } else {
    println("SBT for Scala.js example app is in production mode")
    (resources in Compile) += (fullOptJS in(js, Compile)).value.data
  }
}

def addJSDependenciesToServerResources(): Def.SettingsDefinition = {
  (resources in Compile) += (packageMinifiedJSDependencies in(js, Compile)).value
}

lazy val js = project.settings(
    commonSettings
  ).enablePlugins(ScalaJSPlugin)


lazy val mixtio = (project in file("."))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .dependsOn(shared)
  .settings(
    name := "Mixtio",

    addJavaScriptToServerResources(),
    addJSDependenciesToServerResources(),

    resourceGenerators in Compile += Def.task {
      val file = (resourceManaged in Compile).value / "config.properties"
      val contents = s"devMode=${inDevMode}"
      IO.write(file, contents)
      Seq(file)
    }.taskValue,

    commonSettings,

    libraryDependencies ++= commonLibs ++ Seq(
      "com.google.http-client" % "google-http-client-appengine" % "1.31.0",
      "com.google.http-client" % "google-http-client-jackson2" % "1.31.0",
      "com.google.apis" % "google-api-services-storage" % "v1-rev158-1.25.0",
      "com.google.appengine.tools" % "appengine-gcs-client" % "0.8",

      "javax.servlet" % "servlet-api" % "2.5" % "provided",
      "org.eclipse.jetty" % "jetty-server" % "9.3.18.v20170406" % "container",

      "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,

      "com.fasterxml" % "aalto-xml" % "1.0.0",

      //"org.webjars" % "webjars-locator-core" % "0.39",

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
  ).enablePlugins(AppenginePlugin)


