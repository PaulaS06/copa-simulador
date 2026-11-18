scalaVersion := "3.9.0"

lazy val root = rootProject
  .settings(
    name := "scala",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "ujson" % "3.3.1"
    ),
    Compile / run / fork := true,
    Compile / mainClass := Some("CopaSimulador")
  )
