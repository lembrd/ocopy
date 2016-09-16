package org.lembrd


import org.lembrd.ocopy.OCopyMacro.Converter

import scala.language.experimental.macros
/**
  *
  * User: lembrd
  * Date: 15/09/16
  * Time: 08:18
  */
package object ocopy {
  import OCopyMacro.Converter

  implicit def selfConvert[A] : Converter[A, A] = new Converter[A, A] {
    override def convert(o1: A): A = o1
  }

  object conversions extends BaseConversions {}

  case class OCopyBuilder[T1,T2](from:T1, handler : T1 => T2) {
    /**
      *
      * @tparam A define target type
      */
    def to[A] : OCopyBuilder[T1, A] = macro OCopyMacro.assignTo[A]
    /**
      * Append all fields from another object
      * @param from source object
      */
    def mergeAllFrom[X]( from : X )  : OCopyBuilder[T1,T2]  = macro OCopyMacro.mergeObject[X]

    type FUNCTOR[X] = (X) => Any
    def mergeExclude[X]( from : X)( exclusions: FUNCTOR[X]* ) : OCopyBuilder[T1,T2] = macro OCopyMacro.mergeObjectExclude[X]
    /**
      * Select field to transfer into
      * @param path What field to assign
      */
    def assign[U](path : T2 => U) : AssignBuilder[T1, T2, U] = macro OCopyMacro.pushSetter[U]
    /**
      * Build target object
      * @return Target Object
      */
    def build : T2 = macro OCopyMacro.generateBuilder
  }

  case class AssignBuilder[T1, T2, U2](parentBuilder : OCopyBuilder[T1,T2] ) {

    /**
      * transfer value using 'function' conv
      * @param path how to Get data1
      * @param conv  hot to Convert Data1 to the required type
      */
    def using[U1](path : T1 => U1)( conv : U1 => U2) : OCopyBuilder[T1,T2]  = macro OCopyMacro.pushConversionGetter[U1]

    /**
      * Transfer value from Object. Usable for complex object assembling
      * @param conv hot to Convert entire object from to the required type
      */
    def usingSelf( conv : T1 => U2 ) : OCopyBuilder[T1,T2]  = macro OCopyMacro.pushSelfGetter

    /**
      * Transfer value using implicit Converter object
      * @param path how to Get data1
      */
    def implicitly[U1](path : T1 => U1)(implicit conv : Converter[U1,U2]) : OCopyBuilder[T1,T2]  = macro OCopyMacro.pushGetterImplicit[U1]

    /**
      * Directly convert value from field from path (also can be done via 'implicit def' conversions defined in scope)
      * @param path how to Get data1
      */
    def from( path : T1 => U2 ) : OCopyBuilder[T1,T2]  = macro OCopyMacro.pushPlainGetter

    def fromOutside( value : U2 ) : OCopyBuilder[T1,T2]  = macro OCopyMacro.pushPlainObject

  }

  /**
    * Entry point for conversion
    * @param from Source Object
    */
  def transfer[T](from :T): OCopyBuilder[T, Any] = macro OCopyMacro.createBuilder[T]

}

trait BaseConversions {
  implicit def anyToOption[X]( x:X) : Option[X] = Some(x)

  implicit def optSomeConvert[A] : Converter[A, Option[A]] = new Converter[A, Option[A]] {
    override def convert(o1: A): Option[A] = Some(o1)
  }

}