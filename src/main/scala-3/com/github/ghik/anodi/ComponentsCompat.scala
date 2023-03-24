package com.github.ghik.anodi

import com.github.ghik.anodi.util.SourceInfo

import scala.annotation.compileTimeOnly
import scala.concurrent.{ExecutionContext, Future}

trait ComponentsCompat:
  this: Components =>

  inline def component[T](inline definition: => T)(using SourceInfo): Component[T] = ???
  inline def asyncComponent[T](inline definition: ExecutionContext => Future[T])(using SourceInfo): Component[T] = ???
  inline def singleton[T](inline definition: => T)(using SourceInfo): Component[T] = ???
  inline def asyncSingleton[T](inline definition: ExecutionContext => Future[T])(using SourceInfo): Component[T] = ???

  @compileTimeOnly("implicit Component[T] => implicit T inference only works inside code passed to component/singleton macro")
  implicit def inject[T](implicit component: Component[T]): T = sys.error("stub")
