package com.github.ghik.anodi

import com.github.ghik.anodi.util.SourceInfo

import scala.annotation.compileTimeOnly
import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.quoted.*

trait ComponentsCompat { this: Components =>

  // accessible for the macro impl below
  private def cinfo(sourceInfo: SourceInfo): ComponentInfo = componentInfo(sourceInfo)

  protected inline def singleton[T](inline definition: => T)(using SourceInfo): Component[T] =
    asyncSingleton(implicit ctx => Future(definition))

  protected inline def asyncSingleton[T](inline definition: ExecutionContext => Future[T])(using SourceInfo): Component[T] =
    cached(asyncComponent(definition), componentInfo(SourceInfo()))

  protected inline def component[T](inline definition: => T)(using SourceInfo): Component[T] =
    asyncComponent(implicit ctx => Future(definition))

  protected inline def asyncComponent[T](inline definition: ExecutionContext => Future[T])(using SourceInfo): Component[T] =
    ${ ComponentsCompat.mkComponent('{ this }, '{ definition }, '{ SourceInfo() }) }

  /**
   * Shorthand for `singleton(fromImplicits[T])`. See [[singleton]] and [[fromImplicits]].
   */
  protected inline def singletonFromImplicits[T]: Component[T] =
    singleton(fromImplicits[T])

  /**
   * Shorthand for `component(fromImplicits[T])`. See [[component]] and [[fromImplicits]].
   */
  protected inline def componentFromImplicits[T]: Component[T] =
    component(fromImplicits[T])

  /**
   * Instantiates a class type [[T]] using its primary constructor, passing `summon[ParamType]` as the value of
   * every parameter, i.e. every parameter value is searched for as an implicit/given.
   *
   * Example:
   * {{{
   *   class Foo
   *   class Bar
   *   class Service(foo: Foo, bar: Bar)
   *
   *   class MyAppComponents extends Components {
   *     implicit def foo: Component[Foo] = singleton(new Foo)
   *     implicit def bar: Component[Bar] = singleton(new Bar)
   *     implicit def service: Component[Service] = singleton(fromImplicits[Service])
   *   }
   * }}}
   *
   * In the above example, `fromImplicits[Service]` will be expanded as `new Service(summon[Foo], summon[Bar])`,
   * which will further expand to `new Service(foo.ref, bar.ref)`. Therefore, constructor arguments of `Service`
   * are resolved as implicits, on demand, even though constructor parameters of `Service` are not declared
   * as implicits. This makes it possible to rely on implicits as dependency injection mechanism, but without the
   * unintended side effects, e.g. exposing constructor parameters as implicits inside the class implementations.
   */
  protected inline def fromImplicits[T]: T =
    ${ ComponentsCompat.fromImplicits[T] }

  protected inline given inject[T](using inline component: Component[T]): T = component.ref
}

object ComponentsCompat {
  def mkComponent[T](
    components: Expr[ComponentsCompat],
    definition: Expr[ExecutionContext => Future[T]],
    sourceInfo: Expr[SourceInfo],
  )(using Quotes, Type[T]): Expr[Component[T]] = {
    import quotes.reflect.*

    val RefSym = TypeRepr.of[Component[?]].typeSymbol.methodMember("ref").head
    val depTrees = new ListBuffer[Expr[Component[?]]]

    val creator: Expr[IndexedSeq[Any] => ExecutionContext => Future[T]] =
      class RefExtractor(readyDeps: Expr[IndexedSeq[Any]]) extends ExprMap:
        def transform[A](e: Expr[A])(using Type[A])(using Quotes): Expr[A] = e.asTerm match
          case s@Select(prefix, "ref") if s.symbol == RefSym =>
            depTrees += prefix.asExprOf[Component[?]]
            '{ $readyDeps(${ Expr(depTrees.size - 1) }).asInstanceOf[A] }
          case _ =>
            transformChildren(e)
      '{ readyDeps => ${ new RefExtractor('readyDeps).transform(definition) } }

    '{ new Component[T](
      $components.cinfo($sourceInfo),
      ArraySeq(${ Expr.ofSeq(depTrees.result()) } *),
      $creator,
    ) }
  }

  def fromImplicits[T](using Quotes, Type[T]): Expr[T] = {
    import quotes.reflect.*

    val t = TypeRepr.of[T]
    val td = t.dealias

    val constr =
      td.classSymbol
        .filterNot(_.isAbstractType)
        .map(_.primaryConstructor)
        .filter(_ != Symbol.noSymbol)
        .getOrElse(report.errorAndAbort(s"${t.show} cannot be constructed"))

    def tt(tpe: TypeRepr): TypeTree =
      TypeTree.of(using tpe.asType)

    def mkSummon(tpe: TypeRepr): Term =
      TypeApply(Select.unique('{ Predef }.asTerm, "summon"), List(tt(tpe)))

    def mkApply(prefix: Term, tpe: TypeRepr): Term =
      tpe match {
        case MethodType(_, paramTypes, result) =>
          mkApply(Apply(prefix, paramTypes.map(mkSummon)), result)
        case _ =>
          prefix
      }

    val constrTpe = td.memberType(constr).appliedTo(td.typeArgs)
    val constrSelect = Select(New(TypeTree.of[T]), constr)
    val tapply = if (td.typeArgs.nonEmpty) TypeApply(constrSelect, td.typeArgs.map(tt)) else constrSelect
    mkApply(tapply, constrTpe).asExprOf[T]
  }
}
