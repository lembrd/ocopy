package org.lembrd.ocopy

import scala.annotation.tailrec
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
/**
  *
  * User: lembrd
  * Date: 14/09/16
  * Time: 23:56
  */
object OCopy {

  val th = new ThreadLocal[Helper[_ <: Context]]()

  def log(msg : String): Unit = {
    // println(msg);
  }
  
  def createBuilder[T: c.WeakTypeTag](c :Context)(from: c.Tree) : c.Tree = {
    import c.universe._
    log("----- CREATE BUILDER")
    val h = helper[c.type](c)
    val tpe = weakTypeOf[T]
    h.inType = tpe
    h.from = from
    val anyType = weakTypeOf[Any]
    q"_root_.org.lembrd.ocopy.OCopyBuilder[$tpe, $anyType]($from, null)"
  }

  def assignTo[T2: c.WeakTypeTag](c :Context) : c.Tree = {
    import c.universe._
    log("----- PUSH ASSIGN")
    val h = helper[c.type](c)
    val outType = weakTypeOf[T2]
    h.outType = outType
    val tpe = h.inType
    val from = h.from

    q"_root_.org.lembrd.ocopy.OCopyBuilder[$tpe, $outType]($from, null)"
  }

  def pushSetter[U: c.WeakTypeTag](c :Context)(path : c.Tree) : c.Tree = {
    import c.universe._
    log("----- PUSH SETTER")
    val h = helper[c.type](c)
    val tpe = weakTypeOf[U]


    h.push( h.TargetFieldSetter(path, tpe) )

    val ths = c.prefix
    q"_root_.org.lembrd.ocopy.AssignBuilder($ths)"
  }

  def pushGetter[U2: c.WeakTypeTag](c :Context)(path : c.Tree)( conv : c.Tree) : c.Tree = {
    import c.universe._
    log("----- PUSH GETTER")
    val h = helper[c.type](c)
    require(h.arrayBuffer.nonEmpty)
    val tpe = weakTypeOf[U2]
    val getter: h.SourceFieldGetter = h.SourceFieldGetter(Some(path), conv, tpe)
    h.arrayBuffer.last match {
      case setter : h.TargetFieldSetter => setter.getter = getter
      case _ => sys.error("Unsequenced call getter, setter: " + getter)
    }
    h.push(getter)

    val ths = c.prefix
    q""" $ths.parentBuilder """
  }

  def pushSelfGetter(c :Context)( conv : c.Tree) : c.Tree = {
    import c.universe._
    log("----- PUSH GETTER")
    val h = helper[c.type](c)
    require(h.arrayBuffer.nonEmpty)

    val getter: h.SourceFieldGetter = h.SourceFieldGetter(None, conv, h.inType)
    h.arrayBuffer.last match {
      case setter : h.TargetFieldSetter => setter.getter = getter
      case _ => sys.error("Unsequenced call getter, setter: " + getter)
    }
    h.push(getter)

    val ths = c.prefix
    q""" $ths.parentBuilder """
  }

  def pushGetterImplicit[U2: c.WeakTypeTag](c :Context)(path : c.Tree)( conv : c.Tree) : c.Tree = {
    import c.universe._
    log("----- PUSH GETTERimplicit")
    log("PATH: " + conv + " " + conv.getClass.getName)
    val h = helper[c.type](c)
    require(h.arrayBuffer.nonEmpty)

    val tpe = weakTypeOf[U2]
    val lt = TermName("x")

    val getter: h.SourceFieldGetter = h.SourceFieldGetter(Some(path), q"($lt:$tpe) => $conv.convert($lt)", tpe)
    h.arrayBuffer.last match {
      case setter : h.TargetFieldSetter => setter.getter = getter
      case _ => sys.error("Unsequenced call getter, setter: " + getter)
    }
    h.push(getter)

    val ths = c.prefix
    q""" $ths.parentBuilder """
  }

  def generateBuilder(c:Context) : c.Tree = {
    val h = helper[c.type](c)
    h.generate(h.from)
  }


  def helper[C <: Context](c : Context) : Helper[c.type] = {
    Option(th.get()).map( _.asInstanceOf[Helper[c.type]]).getOrElse{
      log("----- CREATE HELPER")
      val helper = new Helper[c.type](c)
      th.set(helper)
      helper
    }
  }


/*
new org.lembrd.ocopy.ClassTwo(
field1 = Sample.this.from.field1,
f4 = ((x: String) => x.length())(((x$2: org.lembrd.ocopy.ClassOne) => x$2.field1)(Sample.this.from)),
f5 = ((x: Int) => ocopy.this.`package`.optSomeConvert[Int].convert(x))(((x$4: org.lembrd.ocopy.ClassOne) => x$4.field2)(Sample.this.from)),
field3 = ((x: Int) => x.toString())(((x$6: org.lembrd.ocopy.ClassOne) => x$6.field2)(Sample.this.from)),
field4 = ((x: org.lembrd.ocopy.ClassOne) => new Complex2(x.field4_1, x.field4_2))(((x: org.lembrd.ocopy.ClassOne) => Sample.this.from)(Sample.this.from)))
 */

  class Helper[C <: Context](val c: C) {

    import c.universe._

    trait Operation {
      def toField : Field
    }

    var outType : Type = null
    var inType  : Type = null
    var from    : c.Tree = null

    val arrayBuffer = collection.mutable.ArrayBuffer[Operation]()

    def generate(from : c.Tree) : c.Tree = {
      require(outType != null)
      require(inType != null)

      generateTarget(from, inType, outType)
    }

    def push(o:Operation) = {
      log("-- PUSH: " + o)
      arrayBuffer += o
    }

    @tailrec
    final def findLastTerm(x:Tree) : String = {
      x match {
        case sel@Select(parent@Select(p,pn), name) =>
          findLastTerm(parent)
        case sel@Select(id@Ident(pn), name) =>
          name.decodedName.toString
      }
    }

    case class SourceFieldGetter(path : Option[c.Tree],
                                 conv : c.Tree,
                                 tpe : Type) extends Operation {

      override def toField : Field = {
        path match {
          case Some( f@Function(ars, body) ) =>
            Field( findLastTerm(body), tpe)

          case None =>
            Field("", tpe)
        }
      }
    }

    case class TargetFieldSetter(path : c.Tree, tpe : Type) extends Operation {
      var getter : SourceFieldGetter = null
      override def toField : Field = {
        path match {
          case f@Function(ars, body) =>
            Field( findLastTerm(body), f.tpe.typeArgs(1))
        }
      }
    }

    case class Field(name: String, tpe: Type)

    def getCaseMethods(t: Type) = {
      t.members.toList.collect {
        case m: MethodSymbol if m.isCaseAccessor =>
          val fieldName = m.name.decodedName.toString
          val f = Field(fieldName, m.returnType)
          log("AnalyzeSource: " + f)
          f
      }.toSet
    }

    def instantiateTarget(tpe : Type, fields : Iterable[Tree]) = {
      q""" new $tpe(..$fields) """
    }

    def defaultValue(f: Field) = {
      val tn = TermName(f.name)

      f.tpe match {
        case x if x == weakTypeOf[String] => q""" $tn = "" """
        case x if x == weakTypeOf[Int] => q""" $tn = 0 """
        case _ => q""" $tn = null """
      }
    }

    def generateTarget(from: c.Tree, fromTpe : Type, toTpe : Type): c.Tree = {



      val sourceFields = getCaseMethods(fromTpe)
      val targetFields = getCaseMethods(toTpe)

      val getters = arrayBuffer.collect{
        case op : SourceFieldGetter => op.toField -> op
      }.toIndexedSeq.toMap

      val setters = arrayBuffer.collect{
        case op : TargetFieldSetter => op.toField -> op
      }.toIndexedSeq.toMap

      val newInTarget = targetFields -- sourceFields -- setters.keys
      val lostFromSource = sourceFields -- targetFields -- getters.keys

      log("New Fields: " + newInTarget.mkString("\n"))
      log("Lost Fields: " + lostFromSource.mkString("\n"))

      val toSimpleCopy =
        targetFields.collect{
          case k if sourceFields.contains(k) && !setters.contains(k) =>
            val tn = TermName(k.name)
            q""" $tn = $from.$tn """
        }

      val newSetters = setters.map{
        case (k,v) =>
          require(v.getter != null)

          val conv = v.getter.conv
          val tn = TermName(k.name)

          v.getter.path.map( getter => {
            q""" $tn =  $conv( $getter( $from ) ) """

          }).getOrElse{
            q""" $tn =  $conv(  $from  ) """
          }
      }

      instantiateTarget(toTpe, toSimpleCopy ++ newSetters)
    }
  }

  trait Converter[T1,T2] {
    def convert(o1 : T1) : T2
  }

}

