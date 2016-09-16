package org.lembrd.ocopy
import org.lembrd.ocopy.conversions._

object Sample extends App {

  val from = ClassOne(field1 =  "f1", field2 = "f2", field3 = Another("a","b"), field4_1 = 0.1d, field4_2 = 42, userId = UserId(0), f5 = 50)
  // implicit def conversion
  implicit def userIdToLong(x:UserId
                           ) : Long = x.id

  val to : ClassTwo = transfer(from).to[ClassTwo]
    //automatically:  field1 copied by default (name and types equal)
    //automatically:  .assign(_.f5).from(_.f5) // using implicit Converter (value -> Some)
    //automatically:  .assign(_.userId).from(_.userId.id) // using implicit def UserId -> Long
    .assign(_.f4).using(_.field1){ x => x.length} // using inline converter
    .assign(_.field3).from(_.field3.f1) // using long path
    .assign(_.field4).usingSelf{ x => new Complex2(x.field4_1, x.field4_2)} // complex object mapping instead of one field
    .build

  println(to)
}



case class ClassOne(field1      : String,
                    field2      : String,
                    field3      : Another,
                    field4_1    : Double,
                    field4_2    : Int,
                    userId      : UserId,
                    f5          : Int)

case class ClassTwo(userId: Long,
                    field1 : String,
                    field3 : String,
                    f4 : Int,
                    f5 : Option[Int],
                    field4 : Complex2)

case class Another(f1 : String, f2 : String)
class Complex2(d1 : Double, d2 : Int)

case class UserId(id : Long) extends AnyVal