package org.lembrd.ocopy.test
import org.lembrd.ocopy._
import org.lembrd.ocopy.conversions._
import org.scalatest.{FlatSpec, Matchers}

import scala.util.Random
/**
  *
  * User: lembrd
  * Date: 16/09/16
  * Time: 18:12
  */
class OCopyTest extends FlatSpec with Matchers {

  "OCopy" should "transfer complex classes" in {
    val from = ClassOne(field1 =  "f1", field2 = "f2", field3 = Another("a","b"), field4_1 = 0.1d, field4_2 = 42, userId = Some(UserId(0)), f5 = 50)
    val mergeSource = ClassToBeMerged(mergedField = 113L)

    // implicit def conversion
    implicit def userIdToLong(x:UserId) : Long = x.id

    val to : ClassTwo = transfer(from)
      .to[ClassTwo]
//      .mergeAllFrom( mergeSource )  // append fields from object mergeSource
      .mergeExclude(mergeSource)(_.field1, _.fieldExclude2) // append fields from object mergeSource with exclusion checking
      .assign(_.outsideField).fromOutside( Random.nextLong() )  // append field from outside
      //automatically:  field1 copied by default (name and types equal)
      //automatically:  .assign(_.f5).from(_.f5) // using Option wrapper
      //automatically:  userId => _.userId.id // using implicit def [UserId] -> [Long] and wrapped by Option.map
      .assign(_.f4).using(_.field1){ x => x.length} // using inline converter
      .assign(_.field3).from(_.field3.f1) // using long path
      .assign(_.field4).usingSelf{ x => new Complex2(x.field4_1, x.field4_2)} // complex object mapping instead of one field
      .build
/*
The actual generated code is:

new org.lembrd.ocopy.test.ClassTwo(
  f4 = ((x: String) => x.length())(((x$5: org.lembrd.ocopy.test.ClassOne) => x$5.field1)(from)),
  field4 = ((x: org.lembrd.ocopy.test.ClassOne) => new Complex2(x.field4_1, x.field4_2))(from),
  field1 = from.field1,
  mergedField = mergeSource.mergedField,
  userId = from.userId.map(((ftnx0) => ftnx0)),
  outsideField = org.lembrd.ocopy.`package`.conversions.anyToOption[Long](scala.util.Random.nextLong()),
  field3 = ((x$7: org.lembrd.ocopy.test.ClassOne) => x$7.field3.f1)(from),
  f5 = from.f5)
 */
    println(to)

    to.field1 shouldBe "f1"
    to.field3 shouldBe "a"
    to.userId shouldBe Some(0L)
  }
}





case class ClassOne(field1      : String,
                    field2      : String,
                    field3      : Another,
                    field4_1    : Double,
                    field4_2    : Int,
                    userId      : Option[UserId],
                    f5          : Int
                   )
case class ClassToBeMerged(
                            mergedField : Long,
                            fieldExclude2 : String = "",
                            field1 : String = ""
                          )
case class ClassTwo(userId: Option[Long],
                    field1 : String,
                    field3 : String,
                    mergedField : Long,
                    f4 : Int,
                    f5 : Option[Long],
                    field4 : Complex2,
                    outsideField : Option[Long]
                   )

case class Another(f1 : String, f2 : String)
class Complex2(d1 : Double, d2 : Int)

case class UserId(id : Long) extends AnyVal