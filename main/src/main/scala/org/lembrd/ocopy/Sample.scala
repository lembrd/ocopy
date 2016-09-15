package org.lembrd.ocopy
import org.lembrd.ocopy.conversions._

object Sample extends App {

  val from = ClassOne("a", 1, Another("b","c"), 0.1, 1)

  val to : ClassTwo = transfer(from).to[ClassTwo]
    // field1 copied by default (name and types equal)
    .assign(_.f4).using(_.field1){ x => x.length} // using inline converter
    .assign(_.f5).from(_.field2) // using implicit converter
    .assign(_.field3).from(_.field3.f1) // using long path
    .assign(_.field4).usingSelf{ x => new Complex2(x.field4_1, x.field4_2)} // complex object mapping instead of one field
    .build


  println(to)
}

case class Another(f1 : String, f2 : String)
case class ClassOne(field1 : String, field2 : Int, field3 : Another, field4_1 : Double, field4_2 : Int)

class Complex2(d1 : Double, d2 : Int)
case class ClassTwo(field1 : String,  field3 : String, f4 : Int, f5 : Option[Int], field4 : Complex2)
