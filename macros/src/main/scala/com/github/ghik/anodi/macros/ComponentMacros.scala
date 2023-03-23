package com.github.ghik.anodi
package macros

import scala.collection.mutable.ListBuffer
import scala.reflect.macros.blackbox

class ComponentMacros(val c: blackbox.Context) {

  import c.universe._

  def RootPkg = q"_root_"
  def ScalaPkg = q"$RootPkg.scala"
  def AnodiPkg = q"$RootPkg.com.github.ghik.anodi"
  def ComponentCls: Tree = tq"$AnodiPkg.Component"
  def ComponentObj: Tree = q"$AnodiPkg.Component"

  def getType(typeTree: Tree): Type =
    c.typecheck(typeTree, c.TYPEmode).tpe

  lazy val ComponentTpe: Type = getType(tq"$ComponentCls[_]")
  lazy val ComponentRefSym: Symbol = ComponentTpe.member(TermName("ref"))
  lazy val InjectSym: Symbol = getType(tq"$AnodiPkg.Components").member(TermName("inject"))
  lazy val ComponentInfoSym: Symbol = getType(tq"$AnodiPkg.ComponentInfo.type").member(TermName("info"))

  object ComponentRef {
    def unapply(tree: Tree): Option[Tree] = tree match {
      case Select(component, TermName("ref")) if tree.symbol == ComponentRefSym =>
        Some(component)
      case Apply(conversion, List(component)) if conversion.symbol == InjectSym =>
        Some(component)
      case _ => None
    }
  }

  private def mkComponent(tpe: Type, sourceInfo: Tree, definition: Tree, singleton: Boolean, async: Boolean): Tree = {
    val depArrayName = c.freshName(TermName("deps"))
    val infoName = c.freshName(TermName("info"))
    val depsBuf = new ListBuffer[Tree]

    object LocalSymbolsCollector extends Traverser {
      private val symsBuilder = Set.newBuilder[Symbol]
      def symbolsFound: Set[Symbol] = symsBuilder.result()

      override def traverse(tree: Tree): Unit = tree match {
        case ComponentRef(_) => // stop
        case t@(_: DefTree | _: Function | _: Bind) if t.symbol != null =>
          symsBuilder += t.symbol
          super.traverse(tree)
        case _ =>
          super.traverse(tree)
      }
    }

    LocalSymbolsCollector.traverse(definition)
    val componentDefLocals = LocalSymbolsCollector.symbolsFound

    def validateDependency(tree: Tree): Tree = {
      val needsRetyping = tree.exists {
        case _: DefTree | _: Function | _: Bind => true
        case _ => false
      }
      tree.foreach {
        case t@ComponentRef(_) =>
          c.error(t.pos, s"illegal nested component reference inside expression representing component dependency")
        case t if t.symbol != null && componentDefLocals.contains(t.symbol) =>
          c.error(t.pos, s"illegal local value or method reference inside expression representing component dependency")
        case _ =>
      }
      if (needsRetyping) c.untypecheck(tree) else tree
    }

    object DependencyExtractor extends Transformer {
      override def transform(tree: Tree): Tree = tree match {
        case ComponentRef(component) =>
          depsBuf += validateDependency(component)
          val depTpe = component.tpe.baseType(ComponentTpe.typeSymbol).typeArgs.head
          q"$depArrayName(${depsBuf.size - 1}).asInstanceOf[$depTpe]"
        case t if t.symbol == ComponentInfoSym =>
          q"$infoName"
        case _ =>
          super.transform(tree)
      }
    }

    val transformedDefinition = DependencyExtractor.transform(definition)

    val needsRetyping = transformedDefinition != definition ||
      definition.exists {
        case _: DefTree | _: Function | _: Bind => true
        case _ => false
      }
    val finalDefinition =
      if (needsRetyping) c.untypecheck(transformedDefinition) else definition

    val asyncDefinition =
      if (async) finalDefinition
      else q"$AnodiPkg.Component.async($finalDefinition)"

    val result =
      q"""
        val $infoName = ${c.prefix}.componentInfo($sourceInfo)
        new $AnodiPkg.Component[$tpe](
          $infoName,
          $ScalaPkg.IndexedSeq(..${depsBuf.result()}),
          ($depArrayName: $ScalaPkg.IndexedSeq[$ScalaPkg.Any]) => $asyncDefinition
        )
       """

    //TODO: can I avoid recreating ComponentInfo?
    if (singleton)
      q"${c.prefix}.cached($result, ${c.prefix}.componentInfo($sourceInfo))"
    else
      result
  }

  private def ensureRangePositions(): Unit =
    if (!c.compilerSettings.contains("-Yrangepos")) {
      c.abort(c.enclosingPosition, "Component related macros require -Yrangepos")
    }

  def component[T: c.WeakTypeTag](definition: Tree)(sourceInfo: Tree): Tree = {
    ensureRangePositions()
    mkComponent(weakTypeOf[T], sourceInfo, definition, singleton = false, async = false)
  }

  def singleton[T: c.WeakTypeTag](definition: Tree)(sourceInfo: Tree): Tree = {
    ensureRangePositions()
    mkComponent(weakTypeOf[T], sourceInfo, definition, singleton = true, async = false)
  }

  def asyncComponent[T: c.WeakTypeTag](definition: Tree)(sourceInfo: Tree): Tree = {
    ensureRangePositions()
    mkComponent(weakTypeOf[T], sourceInfo, definition, singleton = false, async = true)
  }

  def asyncSingleton[T: c.WeakTypeTag](definition: Tree)(sourceInfo: Tree): Tree = {
    ensureRangePositions()
    mkComponent(weakTypeOf[T], sourceInfo, definition, singleton = true, async = true)
  }

  def autoComponent[T: c.WeakTypeTag](definition: Tree)(sourceInfo: Tree): Tree = {
    ensureRangePositions()
    val component = mkComponent(weakTypeOf[T], sourceInfo, definition, singleton = false, async = false)
    q"$AnodiPkg.AutoComponent($component)"
  }

  def reifyAllSingletons: Tree = {
    val prefixName = c.freshName(TermName("prefix"))
    val bufName = c.freshName(TermName("buf"))

    val componentMethods =
      c.prefix.actualType.members.iterator
        .filter(s => s.isMethod && !s.isSynthetic).map(_.asMethod)
        .filter { m =>
          m.typeParams.isEmpty && m.paramLists.isEmpty &&
            m.typeSignatureIn(c.prefix.actualType).resultType <:< ComponentTpe
        }
        .toList

    q"""
       val $prefixName = ${c.prefix}
       val $bufName = new $ScalaPkg.collection.mutable.ListBuffer[$ComponentTpe]
       def addIfCached(_c: $ComponentTpe): Unit =
         if(_c.isCached) $bufName += _c
       ..${componentMethods.map(m => q"addIfCached($prefixName.$m)")}
       $bufName.result()
       """
  }

  private final lazy val ownerChain = {
    val sym = c.typecheck(q"val ${c.freshName(TermName(""))} = null").symbol
    Iterator.iterate(sym)(_.owner).takeWhile(_ != NoSymbol).drop(1).toList
  }

  def sourceInfo: Tree = {
    def enclosingSymName(sym: Symbol) =
      sym.filter(_.isTerm).map(_.asTerm.getter).orElse(sym).name.decodedName.toString

    val pos = c.enclosingPosition
    q"""
      $AnodiPkg.util.SourceInfo(
        ${pos.source.path},
        ${pos.source.file.name},
        ${pos.line},
        ${enclosingSymName(ownerChain.head)}
      )
     """
  }
}
