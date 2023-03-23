import com.github.ghik.sbt.nosbt.ProjectGroup
import sbt.Keys._
import sbt._
import sbtghactions.GenerativePlugin.autoImport._
import sbtghactions.{JavaSpec, RefPredicate}
import sbtide.Keys.ideBasePackages

object Anodi extends ProjectGroup("anodi") {
  object Versions {
    final val AvsCommons = "2.9.2"
    final val Scalatest = "3.2.15"
  }

  override def globalSettings: Seq[Def.Setting[_]] = Seq(
    excludeLintKeys ++= Set(
      ideBasePackages,
      projectInfo,
    ),
  )

  override def commonSettings: Seq[Def.Setting[_]] = Seq(
    scalaVersion := "2.13.10",
    organization := "com.github.ghik",
    homepage := Some(url("https://github.com/ghik/anodi")),
    ideBasePackages := Seq("com.github.ghik.anodi"),

    githubWorkflowTargetTags ++= Seq("v*"),
    githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17")),
    githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),

    githubWorkflowPublish := Seq(WorkflowStep.Sbt(
      List("ci-release"),
      env = Map(
        "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
      )
    )),

    projectInfo := ModuleInfo(
      nameFormal = "Anodi",
      description = "Almost no Dependency Injection for Scala",
      homepage = Some(url("https://github.com/ghik/anodi")),
      startYear = Some(2023),
      licenses = Vector(
        "Apache License, Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")
      ),
      organizationName = "ghik",
      organizationHomepage = Some(url("https://github.com/ghik")),
      scmInfo = Some(ScmInfo(
        browseUrl = url("https://github.com/ghik/anodi.git"),
        connection = "scm:git:git@github.com:ghik/anodi.git",
        devConnection = Some("scm:git:git@github.com:ghik/anodi.git")
      )),
      developers = Vector(
        Developer("ghik", "Roman Janusz", "romeqjanoosh@gmail.com", url("https://github.com/ghik"))
      ),
    ),

    Compile / scalacOptions ++= Seq(
      "-encoding", "utf-8",
      "-explaintypes",
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:implicitConversions",
      "-language:existentials",
      "-language:dynamics",
      "-language:experimental.macros",
      "-language:higherKinds",
      "-Werror",
      "-Xlint:-missing-interpolator,-adapted-args,-unused,_",
      "-Yrangepos",
    ),

    addCompilerPlugin("com.avsystem.commons" %% "commons-analyzer" % Versions.AvsCommons),

    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % Versions.Scalatest % Test,
    )
  )

  lazy val root: Project = mkRootProject.dependsOn(macros)

  lazy val macros: Project = mkSubProject.settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    ),
  )
}
