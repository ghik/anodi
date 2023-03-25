package com.github.ghik.anodi

import com.github.ghik.anodi.util.SourceInfo

import scala.annotation.compileTimeOnly
import scala.collection.mutable.ListBuffer
import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.quoted.*

trait ComponentsCompat:
  this: Components =>

  // accessible for the macro impl below
  private def cinfo(sourceInfo: SourceInfo): ComponentInfo = componentInfo(sourceInfo)

  inline def singleton[T](inline definition: => T)(using SourceInfo): Component[T] =
    asyncSingleton(implicit ctx => Future(definition))

  inline def asyncSingleton[T](inline definition: ExecutionContext => Future[T])(using SourceInfo): Component[T] =
    cached(asyncComponent(definition), componentInfo(SourceInfo()))

  inline def component[T](inline definition: => T)(using SourceInfo): Component[T] =
    asyncComponent(implicit ctx => Future(definition))

  inline def asyncComponent[T](inline definition: ExecutionContext => Future[T])(using SourceInfo): Component[T] =
    ${ComponentsCompat.mkComponent('{this}, '{definition}, '{summon[SourceInfo]})}

  inline given inject[T](using component: Component[T]): T = component.ref

end ComponentsCompat

object ComponentsCompat:
  def mkComponent[T](
    components: Expr[ComponentsCompat],
    definition: Expr[ExecutionContext => Future[T]],
    sourceInfo: Expr[SourceInfo],
  )(using Quotes, Type[T]): Expr[Component[T]] =
    import quotes.reflect.*

    val RefSym = TypeRepr.of[Component[?]].typeSymbol.methodMember("ref").head
    val depTrees = new ListBuffer[Expr[Component[?]]]

    val creator: Expr[IndexedSeq[Any] => ExecutionContext => Future[T]] =
      class RefExtractor(readyDeps: Expr[IndexedSeq[Any]]) extends ExprMap:
        def transform[A](e: Expr[A])(using Type[A])(using Quotes): Expr[A] = e.asTerm match
          case s@Select(prefix, "ref") if s.symbol == RefSym =>
            depTrees += prefix.asExprOf[Component[?]]
            '{$readyDeps(${Expr(depTrees.size - 1)}).asInstanceOf[A]}
          case _ =>
            transformChildren(e)
      '{readyDeps => ${new RefExtractor('readyDeps).transform(definition)}}

    '{new Component[T](
      $components.cinfo($sourceInfo),
      IndexedSeq(${Expr.ofSeq(depTrees.result())}*),
      $creator,
    )}
  end mkComponent

end ComponentsCompat
