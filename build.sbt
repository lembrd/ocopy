import sbt.Keys._

name := "ocopy"
organization := "org.lembrd"
scalaVersion := "2.11.8"
val ver = "1.10"

val publishSettings = Seq(
  publishMavenStyle := false,
  publishArtifact in Test := false,
  bintrayPackageLabels := Seq("ocopy-lib", "ocopy-macro"),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
)


lazy val macroProject = (project in file("macro")).settings(
  name := "ocopy-macro",
  organization := "org.lembrd",
  version := ver,
//  scalacOptions += "-Ymacro-debug-lite",
  scalaVersion := "2.11.8",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
).settings(publishSettings:_*)

lazy val mainProject = (project in file("main")).settings(
  name := "ocopy-lib",
  version := ver,
  organization := "org.lembrd",
  scalaVersion := "2.11.8",
//  scalacOptions += "-Ymacro-debug-lite",
  libraryDependencies += "org.scalatest"           %% "scalatest" % "2.2.4" % "test"
).settings(publishSettings:_*) dependsOn macroProject

lazy val root = (project in file(".")).settings(
  version := ver,
  publish := { }
) aggregate(macroProject, mainProject)
