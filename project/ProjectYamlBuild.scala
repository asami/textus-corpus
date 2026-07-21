import org.goldenport.cozy.CozyProjectConfig
import sbt._

object ProjectYamlBuild {
  def load(file: File): CozyProjectConfig =
    CozyProjectConfig.load(file)

  def requiredValue(config: CozyProjectConfig, path: String): String =
    config.value(path).getOrElse(sys.error(s"$path is required in project.yaml"))

  def dependencies(config: CozyProjectConfig): Seq[ModuleID] =
    _dependencies(config, "compile", None) ++
      _dependencies(config, "test", Some(Test))

  private def _dependencies(
    config: CozyProjectConfig,
    scope: String,
    configuration: Option[Configuration]
  ): Seq[ModuleID] =
    config.list(s"build.dependencies.$scope").map { coordinate =>
      val module = _module(coordinate)
      configuration.fold(module)(module % _)
    }

  private def _module(coordinate: String): ModuleID =
    coordinate.split(":", -1).toList match {
      case organization :: "" :: artifact :: version :: Nil =>
        organization %% artifact % version
      case organization :: artifact :: version :: Nil =>
        organization % artifact % version
      case _ =>
        sys.error(
          s"Invalid project.yaml dependency '$coordinate'; expected organization:artifact:version or organization::artifact:version"
        )
    }
}
