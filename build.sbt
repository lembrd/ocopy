import sbt.Keys._

name := "ocopy"
organization := "org.lembrd"
scalaVersion := "2.11.8"
val ver = "1.11"

val publishSettings = Seq(
  pomExtra := <scm>
    <url>https://github.com/lembrd/ocopy</url>
    <connection>git@github.com:lembrd/ocopy.git</connection>
  </scm>
    <developers>
      <developer>
        <id>lembrd</id>
        <name>Michael Shabunin</name>
        <url>https://github.com/lembrd</url>
      </developer>
    </developers>,
  publishMavenStyle := true,
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
