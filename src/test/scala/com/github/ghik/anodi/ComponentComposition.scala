package com.github.ghik.anodi

import com.github.ghik.anodi.util.SourceInfo

import scala.concurrent.Await
import scala.concurrent.duration.Duration

abstract class BaseComponent(implicit info: SourceInfo) {
  println(s"${info.enclosingSymbolName}(${info.fileName}:${info.line}) init")
}

class SubDao(implicit info: SourceInfo) extends BaseComponent
class SubService(dao: SubDao)(implicit info: SourceInfo) extends BaseComponent

class SubSystem extends Components {
  override protected def componentNamePrefix: String = "sub."

  private val dao: Component[SubDao] =
    component(new SubDao)

  val service: Component[SubService] =
    component(new SubService(dao.ref))
}

class Service(subService: SubService)(implicit info: SourceInfo) extends BaseComponent

class System(subSystem: SubSystem) extends Components {
  val service: Component[Service] =
    component(new Service(subSystem.service.ref))
}

object ComponentComposition {
  def main(args: Array[String]): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val subSystem = new SubSystem
    val system = new System(subSystem)
    Await.result(system.service.init, Duration.Inf)
  }
}
