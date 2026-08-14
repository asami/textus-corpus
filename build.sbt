import org.goldenport.cozy.CozyPlugin.autoImport._
import org.goldenport.cozy.CozyProjectIdentityEvidence
import sbt.Keys.*

lazy val projectIdentityEvidence = settingKey[CozyProjectIdentityEvidence]("Admitted project.yaml component identity evidence")
lazy val root = project
  .in(file("."))
  .enablePlugins(org.goldenport.cozy.CozyPlugin)
  .settings(
    projectIdentityEvidence := ProjectYamlBuild.admitted(cozyProjectMetadata.value, scalaBinaryVersion.value),
    organization := ProjectYamlBuild.organization(projectIdentityEvidence.value),
    moduleName := ProjectYamlBuild.moduleName(projectIdentityEvidence.value),
    name := moduleName.value,
    version := ProjectYamlBuild.version(projectIdentityEvidence.value),
    scalaVersion := ProjectYamlBuild.requiredValue(cozyProjectMetadata.value, "build.scalaVersion"),
    useCoursier := false,

    resolvers += Resolver.defaultLocal,
    resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
    resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository"),
    resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven",
    libraryDependencies ++= ProjectYamlBuild.dependencies(cozyProjectMetadata.value),

    cozyGeneratorBackend := "cozy",
    cozyDelegateProjectDir := None,
    cozyDelegateCoursierVersion := Some(
      ProjectYamlBuild.requiredValue(
        cozyProjectMetadata.value,
        "build.cozyVersion"
      )
    ),
    cozyCarName := ProjectYamlBuild.carBaseName(projectIdentityEvidence.value),
    cozyManifestMetadata ++=
      cozyProjectMetadata.value.mapUnder("packaging.car.manifest_metadata") ++
        ProjectYamlBuild.manifestMetadata(projectIdentityEvidence.value)
  )
