package org.lembrd
import scala.language.experimental.macros
/**
  *
  * User: lembrd
  * Date: 15/09/16
  * Time: 08:18
  */
package object ocopy {
  import OCopy.Converter

  object conversions {
    implicit def selfConvert[A] : Converter[A, A] = new Converter[A, A] {
      override def convert(o1: A): A = o1
    }

    implicit def optSomeConvert[A] : Converter[A, Option[A]] = new Converter[A, Option[A]] {
      override def convert(o1: A): Option[A] = Some(o1)
    }
  }

  case class OCopyBuilder[T1,T2](from:T1, handler : T1 => T2) {
    def to[A] : OCopyBuilder[T1, A] = macro OCopy.assignTo[A]

    def assign[U](path : T2 => U) : AssignBuilder[T1, T2, U] = macro OCopy.pushSetter[U]
    // TODO: Fill In Default
    // TODO: Fail If Lost
    def build : T2 = macro OCopy.generateBuilder
  }

  case class AssignBuilder[T1, T2, U2](parentBuilder : OCopyBuilder[T1,T2] ) {

    def using[U1](path : T1 => U1)( conv : U1 => U2) : OCopyBuilder[T1,T2]  = macro OCopy.pushGetter[U1]
    def usingSelf( conv : T1 => U2 ) : OCopyBuilder[T1,T2]  = macro OCopy.pushSelfGetter
    def from[U1](path : T1 => U1)(implicit conv : Converter[U1,U2]) : OCopyBuilder[T1,T2]  = macro OCopy.pushGetterImplicit[U1]
  }

  def transfer[T](from :T): OCopyBuilder[T, Any] = macro OCopy.createBuilder[T]

}
