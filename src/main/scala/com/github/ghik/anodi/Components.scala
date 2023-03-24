package com.github.ghik.anodi

import com.github.ghik.anodi.util.SourceInfo

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.compileTimeOnly
import scala.concurrent.Future

/**
 * Base trait for classes that define collections of interdependent [[Component]]s.
 */
trait Components extends ComponentsCompat with ComponentsLowPrio {
  protected def componentNamePrefix: String = ""

  protected def componentInfo(sourceInfo: SourceInfo): ComponentInfo =
    ComponentInfo(componentNamePrefix, sourceInfo)

  private lazy val singletonsCache =
    new ConcurrentHashMap[ComponentInfo, AtomicReference[Future[?]]]

  protected def cached[T](component: Component[T], freshInfo: ComponentInfo): Component[T] = {
    val cacheStorage = singletonsCache
      .computeIfAbsent(freshInfo, _ => new AtomicReference)
      .asInstanceOf[AtomicReference[Future[T]]]
    component.cached(cacheStorage, freshInfo)
  }

  // avoids divergent implicit expansion involving `inject`
  // this is not strictly necessary but makes compiler error messages nicer
  // i.e. the compiler will emit "could not find implicit value" instead of "divergent implicit expansion"
  implicit def ambiguousArbitraryComponent1[T]: Component[T] = null
  implicit def ambiguousArbitraryComponent2[T]: Component[T] = null
}
trait ComponentsLowPrio {
  @compileTimeOnly("implicit Component[T] => implicit T inference only works inside code passed to component/singleton macro")
  implicit def inject[T](implicit component: Component[T]): T = sys.error("stub")
}
