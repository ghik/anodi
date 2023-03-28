package com.github.ghik.anodi

import com.github.ghik.anodi.macros.ComponentMacros
import com.github.ghik.anodi.util.SourceInfo

import scala.annotation.compileTimeOnly
import scala.concurrent.{ExecutionContext, Future}

trait ComponentsCompat extends ComponentsLowPrio { this: Components =>
  /**
   * Creates a [[Component]] based on a definition (i.e. a constructor invocation). The definition may refer to
   * other components as dependencies using `.ref`. This macro will transform the definition by extracting dependencies
   * in a way that allows them to be initialized in parallel, before initializing the current component itself.
   */
  protected def component[T](
    definition: => T
  )(implicit
    sourceInfo: SourceInfo
  ): Component[T] = macro ComponentMacros.component[T]

  /**
   * Asynchronous version of [[component]] macro.
   */
  protected def asyncComponent[T](
    definition: ExecutionContext => Future[T]
  )(implicit
    sourceInfo: SourceInfo
  ): Component[T] = macro ComponentMacros.asyncComponent[T]

  /**
   * This is the same as [[component]] except that the created [[Component]] is cached inside an outer instance that
   * implements [[Components]]. This way you can implement your components using `def`s rather than `val`s
   * (`val`s can be problematic in traits) but caching will make sure that your `def` always returns the same,
   * cached [[Component]] instance. The cache key is based on source position so overriding a method that returns
   * `singleton` will create separate [[Component]] with different cache key.
   */
  protected def singleton[T](
    definition: => T
  )(implicit
    sourceInfo: SourceInfo
  ): Component[T] = macro ComponentMacros.singleton[T]

  /**
   * Asynchronous version of [[singleton]] macro.
   */
  protected def asyncSingleton[T](
    definition: ExecutionContext => Future[T]
  )(implicit
    sourceInfo: SourceInfo
  ): Component[T] = macro ComponentMacros.asyncSingleton[T]

  protected def singletonFromImplicits[T]: Component[T] = macro ComponentMacros.singletonFromImplicits[T]

  protected def componentFromImplicits[T]: Component[T] = macro ComponentMacros.componentFromImplicits[T]

  protected def fromImplicits[T]: T = macro ComponentMacros.fromImplicits[T]

  // avoids divergent implicit expansion involving `inject`
  // this is not strictly necessary but makes compiler error messages nicer
  // i.e. the compiler will emit "could not find implicit value" instead of "divergent implicit expansion"
  protected implicit def ambiguousArbitraryComponent1[T]: Component[T] = null
  protected implicit def ambiguousArbitraryComponent2[T]: Component[T] = null
}
trait ComponentsLowPrio { this: Components =>
  @compileTimeOnly("implicit Component[T] => implicit T inference only works inside code passed to component/singleton macro")
  protected implicit def inject[T](implicit component: Component[T]): T = sys.error("stub")
}
