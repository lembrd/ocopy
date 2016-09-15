name := "ocopy"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value

lazy val root = (project in file(".")) aggregate(macroProject, mainProject)

lazy val macroProject = (project in file("macro")).settings(
  scalacOptions += "-Ymacro-debug-lite",
  scalaVersion := "2.11.8",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

lazy val mainProject = (project in file("main")).settings(
  scalaVersion := "2.11.8",
  scalacOptions += "-Ymacro-debug-lite"

) dependsOn macroProject
