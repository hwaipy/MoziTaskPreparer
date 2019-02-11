name := "MoziTaskPreparer"
version := "0.1.0"
scalaVersion := "2.12.8"
organization := "com.hwaipy"

// Add dependency on ScalaFX library
libraryDependencies += "org.scalafx" %% "scalafx" % "11-R16"
libraryDependencies += "org.apache.poi" % "poi" % "3.15"
libraryDependencies += "org.apache.poi" % "poi-ooxml" % "3.15"

// Determine OS version of JavaFX binaries
lazy val osName = System.getProperty("os.name") match {
  case n if n.startsWith("Linux")   => "linux"
  case n if n.startsWith("Mac")     => "mac"
  case n if n.startsWith("Windows") => "win"
  case _ => throw new Exception("Unknown platform!")
}

lazy val javaFXModules = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
libraryDependencies ++= javaFXModules.map( m =>
  "org.openjfx" % s"javafx-$m" % "11" classifier osName
)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"
scalacOptions ++= Seq("-feature")
scalacOptions ++= Seq("-deprecation")
fork := true
