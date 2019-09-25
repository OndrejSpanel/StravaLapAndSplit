import sbt.Keys.scalacOptions
// shadow sbt-scalajs' crossProject and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

lazy val commonSettings = Seq(
  organization := "com.github.ondrejspanel",
  version := "0.1.10-beta",
  scalaVersion := "2.12.9",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
)

val udashVersion = "0.8.0"

val bootstrapVersion = "3.3.7-1"

val udashJQueryVersion = "3.0.1"

// TODO: try to share
lazy val jvmLibs = Seq(
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",

  "io.udash" %% "udash-core" % udashVersion,
  "io.udash" %% "udash-rest" % udashVersion,
  "io.udash" %% "udash-rpc" % udashVersion,
  "io.udash" %% "udash-css" % udashVersion,
)

lazy val jsLibs = libraryDependencies ++= Seq(
  "org.scalatest" %%% "scalatest" % "3.0.8" % "test",
  "org.scala-js" %%% "scalajs-dom" % "0.9.7",
  "org.querki" %%% "jquery-facade" % "1.2",

  "io.udash" %%% "udash-core" % udashVersion,
  "io.udash" %%% "udash-rest" % udashVersion,
  "io.udash" %%% "udash-rpc" % udashVersion,
  "io.udash" %%% "udash-css" % udashVersion,

  "io.udash" %%% "udash-bootstrap" % udashVersion,
  "io.udash" %%% "udash-charts" % udashVersion,
  "io.udash" %%% "udash-jquery" % udashJQueryVersion,

  "com.zoepepper" %%% "scalajs-jsjoda" % "1.1.1",
  "com.zoepepper" %%% "scalajs-jsjoda-as-java-time" % "1.1.1"
)

lazy val jsDeps = jsDependencies ++= Seq(
  // "jquery.js" is provided by "udash-jquery" dependency
  "org.webjars" % "bootstrap" % bootstrapVersion / "bootstrap.js" minified "bootstrap.min.js" dependsOn "jquery.js",
  "org.webjars.npm" % "js-joda" % "1.10.1" / "dist/js-joda.js" minified "dist/js-joda.min.js"
)

lazy val commonLibs = Seq(
  "joda-time" % "joda-time" % "2.10",
  "org.joda" % "joda-convert" % "1.8.1",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
)

val jacksonVersion = "2.9.9"

lazy val shared = (project in file("shared"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    commonSettings,
    libraryDependencies ++= commonLibs
  )

lazy val sharedJs = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure).in(file("shared-js"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(commonSettings)
  .jvmSettings(libraryDependencies ++= jvmLibs)
  .jsSettings(
    jsLibs,
    jsDeps
  )

lazy val sharedJs_JVM = sharedJs.jvm
lazy val sharedJs_JS = sharedJs.js


lazy val pushUploader = (project in file("push-uploader"))
  .enablePlugins(sbtassembly.AssemblyPlugin)
  .dependsOn(shared, sharedJs_JVM)
  .settings(
    name := "MixtioStart",
    commonSettings,
    libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.9",
    libraryDependencies ++= commonLibs ++ jvmLibs
  )

def inDevMode = sys.props.get("dev.mode").exists(value => value.equalsIgnoreCase("true"))

val cssDir = settingKey[File]("Target for `compileCss` task.")
val compileCss = taskKey[Unit]("Compiles CSS files.")
val compileCssOutput = taskKey[File]("Compiles CSS files.")
// you can also add `compileCss` as a dependency to
// the `compileStatics` and `compileAndOptimizeStatics` tasks


def addJavaScriptToServerResources(): Def.SettingsDefinition = {
  val optJs = if (inDevMode) fastOptJS else fullOptJS
  (resources in Compile) += (optJs in(frontend, Compile)).value.data
}

def addJSDependenciesToServerResources(): Def.SettingsDefinition = {
  val depJs = if (inDevMode) packageJSDependencies else packageMinifiedJSDependencies
  (resources in Compile) += (depJs in(frontend, Compile)).value
}

def addCssToServerResources(): Def.SettingsDefinition = {
  val css = (compileCssOutput in(frontend, Compile))
  (resources in Compile) += css.value
}

lazy val frontend = project.settings(
    commonSettings,
    jsLibs,
    cssDir := {
      (Compile / fastOptJS / target).value / "styles"
    },
    compileCss := Def.taskDyn {
      val dir = (Compile / cssDir).value
      val path = dir.absolutePath
      println(s"Generating CSS files in `$path`...")
      dir.mkdirs()
      // make sure you have configured the valid `CssRenderer` path
      // we assume that `CssRenderer` exists in the `backend` module
      (cssRenderer / Compile / runMain).toTask(s" com.github.opengrabeso.mixtio.cssrenderer.CssRenderer $path false")

    }.value,
    compileCssOutput := {(Compile / cssDir).value / "main.css"},
    compileCssOutput := compileCssOutput.dependsOn(compileCss).value
  ).enablePlugins(ScalaJSPlugin)
    .dependsOn(sharedJs_JS)

lazy val cssRenderer = (project in file("cssRenderer"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    libraryDependencies ++= jvmLibs,
    commonSettings
  )
  .dependsOn(sharedJs_JVM)

lazy val backend = (project in file("backend"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .dependsOn(shared, sharedJs_JVM)
  .settings(
    name := "Mixtio",

    addJavaScriptToServerResources(),
    addJSDependenciesToServerResources(),
    addCssToServerResources(),

    resourceGenerators in Compile += Def.task {
      val file = (resourceManaged in Compile).value / "config.properties"
      val contents = s"devMode=${inDevMode}"
      IO.write(file, contents)
      Seq(file)
    }.taskValue,

    commonSettings,

    libraryDependencies ++= commonLibs ++ jvmLibs ++ Seq(
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

lazy val root = (project in file(".")).aggregate(backend)