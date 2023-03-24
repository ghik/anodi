package com.github.ghik.anodi

import com.github.ghik.anodi.util.SourceInfo

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.compileTimeOnly
import scala.concurrent.Future

/**
 * Base trait for classes that define collections of interdependent [[Component]]s.
 */
trait Components extends ComponentsCompat {
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
}
