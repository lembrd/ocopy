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
object OCopyMacro {

  val th = new ThreadLocal[Helper[_ <: Context]]()

  def skipUndefinedFields() : Unit = {
    th.get().skipUndefinedFields = true
  }
  def log(msg : String): Unit = {
     println(Thread.currentThread().getId +" "+  msg)
  }
  
  def createBuilder[T: c.WeakTypeTag](c :Context)(from: c.Tree) : c.Tree = {
    import c.universe._
    log("\n\n----- CREATE BUILDER")
    val h = new Helper[c.type](c)
    Option(th.get()).foreach( x=> h.parent = x )
    th.set(h)

    val tpe = weakTypeOf[T]
    h.inType = tpe
    h.fromTarget = from

    h.sourceFields ++= h.getCaseMethods(tpe).map( kf => {
      kf -> q"$from.${kf.termName}"
    }).toMap

    val anyType = weakTypeOf[Any]
    q"_root_.org.lembrd.ocopy.OCopyBuilder[$tpe, $anyType]($from, null)"
  }

  def mergeObject[X: c.WeakTypeTag](c :Context)(from: c.Tree) : c.Tree = {
    import c.universe._
    log("\n---- MERGE from:" + from)
    val h = helper[c.type](c)
    val tpe = weakTypeOf[X]

    h.sourceFields ++= h.getCaseMethods(tpe).map( kf => {
      if (h.sourceFields.contains(kf)) {
        sys.error("Merged object override field: " + kf)
      }
      kf -> q"$from.${kf.termName}"
    }).toMap

    q"${c.prefix}"
  }

  def mergeObjectExclude[X: c.WeakTypeTag](c :Context)(from: c.Tree)(exclusions : c.Tree*) : c.Tree = {
    import c.universe._
    log("\n---- MERGE from:" + from)
    val h = helper[c.type](c)
    val tpe = weakTypeOf[X]

    val excludeNames = exclusions.map {
      case f@Function(ars, body) =>
        h.findLastTerm(body)
    }

    h.sourceFields ++= h.getCaseMethods(tpe)
      .filter( kf => !excludeNames.contains(kf.name))
      .map( kf => {
      if (h.sourceFields.contains(kf)) {
        sys.error("Merged object override field: " + kf)
      }
      kf -> q"$from.${kf.termName}"
    }).toMap

    q"${c.prefix}"
  }

  def assignTo[T2: c.WeakTypeTag](c :Context) : c.Tree = {
    import c.universe._
    log("----- PUSH ASSIGN")
    val h = helper[c.type](c)
    val outType = weakTypeOf[T2]
    h.outType = outType
    val tpe = h.inType
    val from = h.fromTarget

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

  def pushConversionGetter[U2: c.WeakTypeTag](c :Context)(path : c.Tree)(conv : c.Tree) : c.Tree = {
    import c.universe._
    log("----- PUSH GETTER")
    val h = helper[c.type](c)
    h.pushGetter(h.SourceFieldGetter(h.fromTarget, Some(path), Some(conv), weakTypeOf[U2]))

    val ths = c.prefix
    q""" $ths.parentBuilder """
  }


  def pushPlainGetter(c :Context)(path : c.Tree) : c.Tree = {
    import c.universe._
    log("----- PUSH GETTER")
    val h = helper[c.type](c)
    val tpe = path match {
      case func @Function(params, body) =>
        func.tpe.typeArgs(1)
    }
    h.pushGetter(h.SourceFieldGetter(h.fromTarget, Some(path), None, tpe))

    val ths = c.prefix
    q""" $ths.parentBuilder """
  }

  def pushPlainObject(c :Context)(value : c.Tree) : c.Tree = {
    import c.universe._
    log("----- PUSH PLAIN OBJECT :" + value)
    val h = helper[c.type](c)
    h.pushGetter(h.SourceFieldGetter(value, None, None, value.tpe))

    val ths = c.prefix
    q""" $ths.parentBuilder """
  }

  def pushSelfGetter(c :Context)( conv : c.Tree) : c.Tree = {
    import c.universe._
    log("----- PUSH GETTER")
    val h = helper[c.type](c)
    h.pushGetter(h.SourceFieldGetter(h.fromTarget, None, Some(conv), h.inType))

    val ths = c.prefix
    q""" $ths.parentBuilder """
  }


  def pushGetterImplicit[U2: c.WeakTypeTag](c :Context)(path : c.Tree)( conv : c.Tree) : c.Tree = {
    import c.universe._
    log("\n\n----- PUSH GETTERimplicit")
    log("PATH: " + conv + " " + conv.getClass.getName)
    val h = helper[c.type](c)

    val tpe = weakTypeOf[U2]
    val testQ = q"ocopy.this.`package`.selfConvert[$tpe]"

    log(
      s"""
         |TREE Convert: $conv
         |$testQ
         |  EQUAL:${testQ.equalsStructure( conv)}""".stripMargin)

    val lt = TermName("x")

    val getter = if ( testQ.equalsStructure( conv) ) {
      h.SourceFieldGetter(h.fromTarget, Some(path), None, tpe)
    } else {
      h.SourceFieldGetter(h.fromTarget, Some(path), Some(q"($lt:$tpe) => $conv.convert($lt)"), tpe)
    }

    h.pushGetter( getter )

    val ths = c.prefix
    q""" $ths.parentBuilder """
  }

  def generateBuilder(c:Context) : c.Tree = {
    val h = helper[c.type](c)
    try {
      h.generate()

    } finally {
      th.set(h.parent)
    }
  }

  def helper[C <: Context](c : Context) : Helper[c.type] = {
    val a = th.get().asInstanceOf[Helper[c.type]]
    require(a != null, "Builder is not initialized")
    a
/*
    Option(th.get()).map( _.asInstanceOf[Helper[c.type]]).getOrElse{
      log("----- CREATE HELPER")
      val helper = new Helper[c.type](c)
      th.set(helper)
      helper
    }
*/
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
    var parent : Helper[_ <: Context] = null

    var skipUndefinedFields : Boolean = false

    def pushGetter(getter: SourceFieldGetter) : Unit = {
      require(arrayBuffer.nonEmpty)

      arrayBuffer.last match {
        case setter: TargetFieldSetter => setter.getter = getter
        case _ => sys.error("Unsequenced call getter, setter: " + getter)
      }
    }

    import c.universe._

    trait Operation {
      def toField : FieldKey
    }

    var outType : Type = null
    var inType  : Type = null
    var fromTarget    : c.Tree = null

    val arrayBuffer = collection.mutable.ArrayBuffer[Operation]()

    val sourceFields = collection.mutable.Map[FieldKey, Tree ]()

    def generate() : c.Tree = {
      require(outType != null)
      require(inType != null)

      generateTarget( outType)
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

    case class SourceFieldGetter( from : Tree,
                                  path : Option[c.Tree],
                                  conv : Option[c.Tree],
                                  tpe  : Type) extends Operation {

      override def toField : FieldKey = {
        path match {
          case Some( f@Function(ars, body) ) =>
            val name = findLastTerm(body)

            FieldKey( name, tpe )

          case None =>
            FieldKey( "", tpe )
        }
      }
    }

    case class TargetFieldSetter(path : c.Tree, tpe : Type) extends Operation {
      var getter : SourceFieldGetter = null
      override def toField : FieldKey = {
        path match {
          case f@Function(ars, body) =>
            FieldKey( findLastTerm(body), f.tpe.typeArgs(1))
        }
      }
    }

    val OPTION = c.universe.rootMirror.staticClass("scala.Option")
    def isOption(tpe : Type) = {
      tpe.typeSymbol == OPTION
    }
/*

    trait Field{
      def name : String
      def tpe : Type
    }
*/

    case class FieldKey(name: String, tpe: Type) {
      def termName = TermName(name)
    }

    def getCaseMethods(t: Type) : Set[FieldKey] = {
      t.members.toList.collect {
        case m: MethodSymbol if m.isCaseAccessor =>
          val fieldName = m.name.decodedName.toString
          val tn = TermName(fieldName)
          val f = FieldKey(fieldName, m.returnType)
//          val f = SourceGetter(fieldName, m.returnType, q"$pathToObject.$tn" )
          log("AnalyzeSource: " + f)
          f
      }.toSet
    }

    def instantiateTarget(tpe : Type, fields : Seq[Tree]) = {
      val params = getCaseMethods(tpe)

      if ( (! skipUndefinedFields) && (params.size != fields.size) ) {
        val setters = fields.mkString("\n")
        sys.error(
          s"""Some fields are undefined (you can suppress this check by call skipUndefinedFields() method ):
             |Class: $tpe
             |Declared fields (${params.size}):
             |${params.mkString("\n")}
             |
             |---------------------
             |Generated setters (${fields.size}):
             |$setters
             |
             |""".stripMargin)
      }

      q""" new $tpe(..$fields) """
    }

/*
    def defaultValue(f: Field) = {
      val tn = TermName(f.name)

      f.tpe match {
        case x if x == weakTypeOf[String] => q""" $tn = "" """
        case x if x == weakTypeOf[Int] => q""" $tn = 0 """
        case _ => q""" $tn = null """
      }
    }
*/

    def generateTarget( toTpe : Type): c.Tree = {

      val targetFields = getCaseMethods(toTpe)

/*
      val getters = arrayBuffer.collect{
        case op : SourceFieldGetter => op.toField -> op
      }.toIndexedSeq.toMap
*/

      val setters = arrayBuffer.collect{
        case op : TargetFieldSetter => op.toField -> op
      }.toIndexedSeq.toMap

      def usingGetter(k:FieldKey,  getter : SourceFieldGetter) = {

        val tn = TermName(k.name)
        val from = getter.from

        getter.path.map( getterPath => {
          getter.conv.map(convTree => {
            q""" $tn =  $convTree( $getterPath( $from ) ) """
          }).getOrElse{
            q""" $tn =  $getterPath( $from ) """
          }
        }).getOrElse{
          getter.conv.map(convTree => {
            q""" $tn =  $convTree(  $from  ) """
          }).getOrElse{
            q""" $tn =  $from """
          }

        }
      }

      type ConversionResult = (Tree) => Tree

      val NORESUT : ConversionResult = (x) => throw new UnsupportedOperationException

      def typeToTypeConversion(fromType : Type, toType : Type, fromPath : Tree) : ConversionResult = {
        val qq = q"{ val x :$toType = $fromPath }"
        c.typecheck(qq,silent = true) match {
          case EmptyTree =>
            val cl = c.universe.rootMirror.staticClass("_root_.org.lembrd.ocopy.OCopyMacro.Converter")
            val converterType = c.universe.appliedType(cl, fromType, toType)
            val cnv = c.inferImplicitValue(converterType,silent = true)
            cnv match {
              case EmptyTree => NORESUT
              case tree => (x) => q"$cnv.convert($x)"
            }
          case tree => (x) => x
        }
      }

      val toSimpleCopy =
        targetFields.collect{
          case k if sourceFields.contains(k) && !setters.contains(k) =>
            val tn = TermName(k.name)
            val path = sourceFields(k)
            q""" $tn = $path """

          case k if sourceFields.exists(_._1.name == k.name) && !setters.contains(k) =>
            // in this case we should try to find implicit from context
            val fromField = sourceFields.find(_._1.name == k.name).head
            val fromType = fromField._1.tpe
            val fromPath = fromField._2

            val tn = TermName(k.name)
            val tree = typeToTypeConversion(fromType, k.tpe, q"$fromPath")
            tree match {
              case NORESUT =>
                if (isOption(k.tpe)) {  // target field is Option
                  if (isOption(fromType)) { // source field also is Option

                    typeToTypeConversion( fromType.typeArgs(0), k.tpe.typeArgs(0), q"$fromPath.get") match {
                      case NORESUT => sys.error("Could not transfer " + fromField + " -> " + k)

                      case tree =>

                        val t = tree( q"ftnx0")
                        val xtt = k.tpe.typeArgs(0)
                        q""" $tn = $fromPath.map( ftnx0 => $t ) """
                    }
                  } else { // source is not an option
                    typeToTypeConversion(fromType, k.tpe.typeArgs(0), q"$fromPath") match {
                      case NORESUT =>  sys.error("Could not transfer " + fromField + " -> " + k)
                      case tree =>
                        val t = tree( q"$fromPath")
                        q""" $tn = scala.Some( $t )"""
                    }
                  }
               } else {
                  sys.error("Could not transfer " + fromField + " -> " + k)
               }
              case tree =>
                val t = tree( q"$fromPath")
                q""" $tn = $t"""

            }



        }

      val newSetters = setters.map{
        case (k,v) =>
          usingGetter(k, v.getter)
      }

      instantiateTarget(toTpe, (toSimpleCopy ++ newSetters).toIndexedSeq)
    }
  }

  trait Converter[T1,T2] {
    def convert(o1 : T1) : T2
  }

}

