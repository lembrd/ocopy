import sbt.Keys._

name := "ocopy"
organization := "org.lembrd"
scalaVersion := "2.11.8"

val publishSettings = Seq(
  publishMavenStyle := false,
  publishArtifact in Test := false,
  licenses += ("MIT", url("https://github.com/lembrd/ocopy/blob/master/LICENSE"))
)


lazy val macroProject = (project in file("macro")).settings(
  name := "ocopy-macro",
  organization := "org.lembrd",
  version := "1.9",
  scalacOptions += "-Ymacro-debug-lite",
  scalaVersion := "2.11.8",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

lazy val mainProject = (project in file("main")).settings(
  name := "ocopy-lib",
  version := "1.9",
  organization := "org.lembrd",
  scalaVersion := "2.11.8",
  scalacOptions += "-Ymacro-debug-lite",
  libraryDependencies += "org.scalatest"           %% "scalatest" % "2.2.4" % "test"
) dependsOn macroProject

lazy val root = (project in file(".")) aggregate(macroProject, mainProject)
