import com.github.ghik.sbt.nosbt.ProjectGroup
import sbt.Keys.*
import sbt.{Def, *}
import sbtghactions.GenerativePlugin.autoImport.*
import sbtghactions.{JavaSpec, RefPredicate}
import sbtide.Keys.ideBasePackages

object Anodi extends ProjectGroup("anodi") {
  object Versions {
    final val Scalatest = "3.2.15"
  }

  override def globalSettings: Seq[Def.Setting[?]] = Seq(
    excludeLintKeys ++= Set(
      ideBasePackages,
      projectInfo,
    ),
  )

  override def buildSettings: Seq[Def.Setting[?]] = Seq(
    crossScalaVersions := Seq("2.13.10", "3.2.2"),
    scalaVersion := crossScalaVersions.value.last,

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
      ),
    )),
  )

  override def commonSettings: Seq[Def.Setting[?]] = Seq(
    organization := "com.github.ghik",
    homepage := Some(url("https://github.com/ghik/anodi")),
    ideBasePackages := Seq("com.github.ghik.anodi"),

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
    ),

    Compile / scalacOptions ++= (scalaBinaryVersion.value match {
      case "2.13" => Seq(
        "-Xlint:-missing-interpolator,-adapted-args,-unused,_",
        "-Xsource:3",
        "-Yrangepos",
      )
      case "3" => Seq()
    }),

    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % Versions.Scalatest % Test,
    ),

    Compile / doc / sources := Seq.empty,
  )

  lazy val root: Project = mkRootProject
    .aggregate(macros)
    .dependsOn(macros)

  lazy val macros: Project = mkSubProject.settings(
    libraryDependencies ++= (scalaBinaryVersion.value match {
      case "2.13" => Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      )
      case "3" => Seq()
    }),
  )
}
