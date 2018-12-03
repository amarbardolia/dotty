package dotty.tools
package dotc
package typer

import core._
import ast._
import ast.Trees._
import StdNames._
import Contexts._, Symbols._, Types._, SymDenotations._, Names._, NameOps._, Flags._, Decorators._
import ProtoTypes._
import util.Positions._
import collection.mutable
import Constants.Constant
import config.Printers.typr
import Inferencing._
import transform.TypeUtils._
import transform.SymUtils._


// TODOs:
//  - handle case where there's no companion object
//  - check that derived instances are stable
//  - reference derived instances with correct prefix instead of just the symbol

/** A typer mixin that implements typeclass derivation functionality */
trait Deriving { this: Typer =>

  /** A helper class to derive type class instances for one class or object
   *  @param  cls              The class symbol of the class or object with a `derives` clause
   *  @param  templateStartPos The default position that should be given to generic
   *                           synthesized infrastructure code that is not connected with a
   *                           `derives` instance.
   */
  class Deriver(cls: ClassSymbol, templateStartPos: Position)(implicit ctx: Context) {

    /** A buffer for synthesized symbols */
    private var synthetics = new mutable.ListBuffer[Symbol]

    /** the children of `cls` ordered by textual occurrence */
    lazy val children = cls.children.sortBy(_.pos.start)

    /** The shape (of type Shape.Case) of a case given by `sym`. `sym` is either `cls`
     *  itself, or a subclass of `cls`, or an instance of `cls`.
     */
    private def caseShape(sym: Symbol): Type = {
      val (constr, elems) =
        sym match {
          case caseClass: ClassSymbol =>
            caseClass.primaryConstructor.info match {
              case info: PolyType =>
                def instantiate(implicit ctx: Context) = {
                  val poly = constrained(info, untpd.EmptyTree)._1
                  val mono @ MethodType(_) = poly.resultType
                  val resType = mono.finalResultType
                  resType <:< cls.appliedRef
                  val tparams = poly.paramRefs
                  val variances = caseClass.typeParams.map(_.paramVariance)
                  val instanceTypes = (tparams, variances).zipped.map((tparam, variance) =>
                    ctx.typeComparer.instanceType(tparam, fromBelow = variance < 0))
                  (resType.substParams(poly, instanceTypes),
                   mono.paramInfos.map(_.substParams(poly, instanceTypes)))
                }
                instantiate(ctx.fresh.setExploreTyperState().setOwner(caseClass))
              case info: MethodType =>
                (cls.typeRef, info.paramInfos)
              case _ =>
                (cls.typeRef, Nil)
            }
          case _ =>
            (sym.termRef, Nil)
        }
      val elemShape = (elems :\ (defn.UnitType: Type))(defn.PairType.appliedTo(_, _))
      defn.ShapeCaseType.appliedTo(constr, elemShape)
    }

    /** The shape of `cls` if `cls` is sealed */
    private def sealedShape: Type = {
      val cases = children.map(caseShape)
      val casesShape = (cases :\ (defn.UnitType: Type))(defn.PairType.appliedTo(_, _))
      defn.ShapeCasesType.appliedTo(casesShape)
    }

    /** The shape of `cls`, referring directly to the type parameters of `cls` instead
     *  of abstracting over them.
     *  Returns NoType if `cls` is neither sealed nor a case class or object.
     */
    lazy val shapeWithClassParams: Type =
      if (cls.is(Case)) caseShape(cls)
      else if (cls.is(Sealed)) sealedShape
      else NoType

    /** A completer for the synthesized `Shape` type. */
    class ShapeCompleter extends TypeParamsCompleter {

      override def completerTypeParams(sym: Symbol)(implicit ctx: Context) = cls.typeParams

      def completeInCreationContext(denot: SymDenotation) = {
        val shape0 = shapeWithClassParams
        val tparams = cls.typeParams
        val abstractedShape =
          if (!shape0.exists) {
            ctx.error(em"Cannot derive for $cls; it is neither sealed nor a case class or object", templateStartPos)
            UnspecifiedErrorType
          }
          else if (tparams.isEmpty)
            shape0
          else
            HKTypeLambda(tparams.map(_.name.withVariance(0)))(
              tl => tparams.map(tparam => tl.integrate(tparams, tparam.info).bounds),
              tl => tl.integrate(tparams, shape0))
        denot.info = TypeAlias(abstractedShape)
      }

      def complete(denot: SymDenotation)(implicit ctx: Context) =
        completeInCreationContext(denot)
    }

    private def add(sym: Symbol): sym.type = {
      ctx.enter(sym)
      synthetics += sym
      sym
    }

    /** Create a synthetic symbol owned by current owner */
    private def newSymbol(name: Name, info: Type, pos: Position, flags: FlagSet = EmptyFlags)(implicit ctx: Context) =
      ctx.newSymbol(ctx.owner, name, flags | Synthetic, info, coord = pos)

    /** Create a synthetic method owned by current owner */
    private def newMethod(name: TermName, info: Type,
                          pos: Position = ctx.owner.pos,
                          flags: FlagSet = EmptyFlags)(implicit ctx: Context): TermSymbol =
      newSymbol(name, info, pos, flags | Method).asTerm

    /** Enter type class instance with given name and info in current scope, provided
     *  an instance with the same name does not exist already.
     *  @param  reportErrors  Report an error if an instance with the same name exists already
     */
    private def addDerivedInstance(clsName: Name, info: Type, pos: Position, reportErrors: Boolean) = {
      val instanceName = s"derived$$$clsName".toTermName
      if (ctx.denotNamed(instanceName).exists) {
        if (reportErrors) ctx.error(i"duplicate typeclass derivation for $clsName")
      }
      else add(newMethod(instanceName, info, pos, Implicit))
    }

    /* Check derived type tree `derived` for the following well-formedness conditions:
      * (1) It must be a class type with a stable prefix (@see checkClassTypeWithStablePrefix)
      * (2) It must have exactly one type parameter
      * If it passes the checks, enter a typeclass instance for it in the current scope.
      * Given
      *
      *    class C[Ts] .... derives ... D ...
      *
      * where `T_1, ..., T_n` are the first-kinded type parameters in `Ts`,
      * the typeclass instance has the form
      *
      *     implicit def derived$D(implicit ev_1: D[T1], ..., ev_n: D[T_n]): D[C[Ts]] = D.derived
      *
      * See test run/typeclass-derivation2 for examples that spell out what would be generated.
      * Note that the name of the derived method containd the name in the derives clause, not
      * the underlying class name. This allows one to disambiguate derivations of type classes
      * that have the same name but different prefixes through selective aliasing.
      */
    private def processDerivedInstance(derived: untpd.Tree): Unit = {
      val uncheckedType = typedAheadType(derived, AnyTypeConstructorProto).tpe.dealias
      val derivedType = checkClassType(uncheckedType, derived.pos, traitReq = false, stablePrefixReq = true)
      val nparams = derivedType.classSymbol.typeParams.length
      if (nparams == 1) {
        val typeClass = derivedType.classSymbol
        val firstKindedParams = cls.typeParams.filterNot(_.info.isLambdaSub)
        val evidenceParamInfos =
          for (param <- firstKindedParams) yield derivedType.appliedTo(param.typeRef)
        val resultType = derivedType.appliedTo(cls.appliedRef)
        val instanceInfo =
          if (cls.typeParams.isEmpty) ExprType(resultType)
          else PolyType.fromParams(cls.typeParams, ImplicitMethodType(evidenceParamInfos, resultType))
        addDerivedInstance(derivedType.typeSymbol.name, instanceInfo, derived.pos, reportErrors = true)
      }
      else
        ctx.error(
          i"derived class $derivedType should have one type paramater but has $nparams",
          derived.pos)
    }

    /** Add value corresponding to `val reflectedClass = new ReflectedClass(...)`
     *  to `synthetics`, unless a definition of `reflectedClass` exists already.
     */
    private def addReflectedClass(): Unit =
      if (!ctx.denotNamed(nme.reflectedClass).exists) {
        add(newSymbol(nme.reflectedClass, defn.ReflectedClassType, templateStartPos))
      }

    /** Add `type Shape = ... ` type to `synthetics`, unless a definition of type `Shape` exists already */
    private def addShape(): Unit =
      if (!ctx.denotNamed(tpnme.Shape).exists) {
        val shapeSym = add(newSymbol(tpnme.Shape, new ShapeCompleter, templateStartPos))
        val lazyShapedInfo = new LazyType {
          def complete(denot: SymDenotation)(implicit ctx: Context) = {
            val tparams = cls.typeParams
            val appliedShape = shapeSym.typeRef.appliedTo(tparams.map(_.typeRef))
            val shapedType = defn.ShapedType.appliedTo(cls.appliedRef, appliedShape)
            denot.info = PolyType.fromParams(tparams, shapedType).ensureMethodic
          }
        }
        addDerivedInstance(defn.ShapedType.name, lazyShapedInfo, templateStartPos, reportErrors = false)
      }

    /** Create symbols for derived instances and infrastructure,
     *  append them to `synthetics` buffer,
     *  and enter them into class scope.
     */
    def enterDerived(derived: List[untpd.Tree]) = {
      derived.foreach(processDerivedInstance(_))
      addShape()
      addReflectedClass()
    }

    private def tupleElems(tp: Type): List[Type] = tp match {
      case AppliedType(fn, hd :: tl :: Nil) if fn.classSymbol == defn.PairClass =>
        hd :: tupleElems(tl)
      case _ =>
        Nil
    }

    /** Extractor for the `cases` in a `Shaped.Cases(cases)` shape */
    private object ShapeCases {
      def unapply(shape: Type): Option[List[Type]] = shape match {
        case AppliedType(fn, cases :: Nil) if fn.classSymbol == defn.ShapeCasesClass =>
          Some(tupleElems(cases))
        case _ =>
          None
      }
    }

    /** Extractor for the `pattern` and `elements` in a `Shaped.Case(pattern, elements)` shape */
    private object ShapeCase  {
      def unapply(shape: Type): Option[(Type, List[Type])] = shape match {
        case AppliedType(fn, pat :: elems :: Nil) if fn.classSymbol == defn.ShapeCaseClass =>
          Some((pat, tupleElems(elems)))
        case _ =>
          None
      }
    }

    /** A helper class to create definition trees for `synthetics` */
    class Finalizer {
      import tpd._

      /** The previously synthetsized `reflectedClass` symbol */
      private def reflectedClass =
        synthetics.find(sym => !sym.is(Method) && sym.name == nme.reflectedClass).get.asTerm

      /** The string to pass to `ReflectedClass` for initializing case and element labels.
       *  See documentation of `ReflectedClass.label` for what needs to be passed.
       */
      private def labelString(sh: Type): String = sh match {
        case ShapeCases(cases) =>
          cases.map(labelString).mkString("\u0001")
        case ShapeCase(pat: TermRef, _) =>
          pat.symbol.name.toString
        case ShapeCase(pat, elems) =>
          val patCls = pat.widen.classSymbol
          val patLabel = patCls.name.stripModuleClassSuffix.toString
          val elemLabels = patCls.caseAccessors.map(_.name.toString)
          (patLabel :: elemLabels).mkString("\u0000")
      }

      /** The RHS of the `reflectedClass` value definition */
      private def reflectedClassRHS =
        New(defn.ReflectedClassType, Literal(Constant(labelString(shapeWithClassParams))) :: Nil)

      /** The RHS of the `derived$Shaped` typeclass instance.
       *  Example: For the class definition
       *
       *    enum Lst[+T] derives ... { case Cons(hd: T, tl: Lst[T]); case Nil }
       *
       *  the following typeclass instance is generated:
       *
       *    implicit def derived$Shape[T]: Shaped[Lst[T], Shape[T]] = new {
       *      def reflect(x$0: Lst[T]): Mirror = x$0 match {
       *        case x$0: Cons[T]  => reflectedClass.mirror(0, x$0)
       *        case x$0: Nil.type => reflectedClass.mirror(1)
       *      }
       *      def reify(c: Mirror): Lst[T] = c.ordinal match {
       *        case 0 => Cons[T](c(0).asInstanceOf[T], c(1).asInstanceOf[Lst[T]])
       *        case 1 => Nil
       *      }
       *      def common = reflectedClass
       *    }
       */
      private def shapedRHS(shapedType: Type)(implicit ctx: Context) = {
        val AppliedType(_, clsArg :: shapeArg :: Nil) = shapedType
        val shape = shapeArg.dealias

        val implClassSym = ctx.newNormalizedClassSymbol(
          ctx.owner, tpnme.ANON_CLASS, EmptyFlags, shapedType :: Nil, coord = templateStartPos)
        val implClassCtx = ctx.withOwner(implClassSym)
        val implClassConstr =
          newMethod(nme.CONSTRUCTOR, MethodType(Nil, implClassSym.typeRef))(implClassCtx).entered

        def implClassStats(implicit ctx: Context): List[Tree] = {

          val reflectMethod: DefDef = {
            val meth = newMethod(nme.reflect, MethodType(clsArg :: Nil, defn.MirrorType)).entered
            def rhs(paramRef: Tree)(implicit ctx: Context): Tree = {
              def reflectCase(scrut: Tree, idx: Int, elems: List[Type]): Tree = {
                val ordinal = Literal(Constant(idx))
                val args = if (elems.isEmpty) List(ordinal) else List(ordinal, scrut)
                val mirror = defn.ReflectedClassType
                  .member(nme.mirror)
                  .suchThat(sym => args.tpes.corresponds(sym.info.firstParamTypes)(_ <:< _))
                ref(reflectedClass).select(mirror.symbol).appliedToArgs(args)
              }
              shape match {
                case ShapeCases(cases) =>
                  val clauses = cases.zipWithIndex.map {
                    case (ShapeCase(pat, elems), idx) =>
                      val patVar = newSymbol(nme.syntheticParamName(0), pat, meth.pos)
                      CaseDef(
                        Bind(patVar, Typed(untpd.Ident(nme.WILDCARD).withType(pat), TypeTree(pat))),
                        EmptyTree,
                        reflectCase(ref(patVar), idx, elems))
                  }
                  Match(paramRef, clauses)
                case ShapeCase(pat, elems) =>
                  reflectCase(paramRef, 0, elems)
              }
            }
            tpd.DefDef(meth, paramss => rhs(paramss.head.head)(ctx.fresh.setOwner(meth).setNewScope))
          }

          val reifyMethod: DefDef = {
            val meth = newMethod(nme.reify, MethodType(defn.MirrorType :: Nil, clsArg)).entered
            def rhs(paramRef: Tree)(implicit ctx: Context): Tree = {
              def reifyCase(caseType: Type, elems: List[Type]): Tree = caseType match {
                case caseType: TermRef =>
                  ref(caseType)
                case caseType =>
                  val args =
                    for ((elemTp, idx) <- elems.zipWithIndex)
                    yield paramRef.select(nme.apply).appliedTo(Literal(Constant(idx))).asInstance(elemTp)
                  New(caseType, args)
              }
              shape match {
                case ShapeCases(cases) =>
                  val clauses =
                    for ((ShapeCase(pat, elems), idx) <- cases.zipWithIndex)
                    yield CaseDef(Literal(Constant(idx)), EmptyTree, reifyCase(pat, elems))
                  Match(paramRef.select(nme.ordinal), clauses)
                case ShapeCase(pat, elems) =>
                  reifyCase(pat, elems)
              }
            }

            tpd.DefDef(meth, paramss => rhs(paramss.head.head)(ctx.withOwner(meth)))
          }

          val commonMethod: DefDef = {
            val meth = newMethod(nme.common, ExprType(defn.ReflectedClassType)).entered
            tpd.DefDef(meth, ref(reflectedClass))
          }

          List(reflectMethod, reifyMethod, commonMethod)
        }

        val implClassDef = ClassDef(implClassSym, DefDef(implClassConstr), implClassStats(implClassCtx))
        Block(implClassDef :: Nil, New(implClassSym.typeRef, Nil))
      }

      /** The type class instance definition with symbol `sym` */
      private def typeclassInstance(sym: Symbol)(implicit ctx: Context) =
        (tparamRefs: List[Type]) => (paramRefss: List[List[tpd.Tree]]) => {
          val tparams = tparamRefs.map(_.typeSymbol.asType)
          val params = if (paramRefss.isEmpty) Nil else paramRefss.head.map(_.symbol.asTerm)
          tparams.foreach(ctx.enter)
          params.foreach(ctx.enter)
          def instantiated(info: Type): Type = info match {
            case info: PolyType => instantiated(info.instantiate(tparamRefs))
            case info: MethodType => info.instantiate(params.map(_.termRef))
            case info => info
          }
          val resultType = instantiated(sym.info)
          val typeCls = resultType.classSymbol
          if (typeCls == defn.ShapedClass)
            shapedRHS(resultType)
          else {
            val module = untpd.ref(typeCls.companionModule.termRef).withPos(sym.pos)
            val rhs = untpd.Select(module, nme.derived)
            typed(rhs, resultType)
          }
        }

      def syntheticDef(sym: Symbol): Tree =
        if (sym.isType)
          tpd.TypeDef(sym.asType)
        else if (sym.is(Method))
          tpd.polyDefDef(sym.asTerm, typeclassInstance(sym)(ctx.fresh.setOwner(sym).setNewScope))
        else
          tpd.ValDef(sym.asTerm, reflectedClassRHS)

      def syntheticDefs: List[Tree] = synthetics.map(syntheticDef).toList
    }

    def finalize(stat: tpd.TypeDef): tpd.Tree = {
      val templ @ Template(_, _, _, _) = stat.rhs
      tpd.cpy.TypeDef(stat)(
        rhs = tpd.cpy.Template(templ)(body = templ.body ++ new Finalizer().syntheticDefs))
    }
  }
}
