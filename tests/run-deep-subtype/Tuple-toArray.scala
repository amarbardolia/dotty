import scala.reflect.ClassTag

object Test {
  def main(args: Array[String]): Unit = {

    def printArray(xs: Array[Object]): Unit =
      println(java.util.Arrays.deepToString(xs))
    def testArray[T: ClassTag](n: Int, elem: Int => T): Unit = {
      val t: Tuple = Tuple.fromArray(Array.tabulate(n)(elem))
      printArray(t.toArray)
    }

    for (i <- 0 to 25)
      testArray(i, j => j)

    printArray(Tuple().toArray)
    printArray(Tuple(1).toArray)
    printArray((1, 2).toArray)
    printArray((1, 2, 3).toArray)
    printArray((1, 2, 3, 4).toArray)
    printArray((1, 2, 3, 4, 5).toArray)
    printArray((1, 2, 3, 4, 5, 6).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24).toArray)
    printArray((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25).toArray)

    printArray((1 *: Tuple()).toArray)
    printArray((1 *: 2 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: 15 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: 15 *: 16 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: 15 *: 16 *: 17 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: 15 *: 16 *: 17 *: 18 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: 15 *: 16 *: 17 *: 18 *: 19 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: 15 *: 16 *: 17 *: 18 *: 19 *: 20 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: 15 *: 16 *: 17 *: 18 *: 19 *: 20 *: 21 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: 15 *: 16 *: 17 *: 18 *: 19 *: 20 *: 21 *: 22 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: 15 *: 16 *: 17 *: 18 *: 19 *: 20 *: 21 *: 22 *: 23 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: 15 *: 16 *: 17 *: 18 *: 19 *: 20 *: 21 *: 22 *: 23 *: 24 *: Tuple()).toArray)
    printArray((1 *: 2 *: 3 *: 4 *: 5 *: 6 *: 7 *: 8 *: 9 *: 10 *: 11 *: 12 *: 13 *: 14 *: 15 *: 16 *: 17 *: 18 *: 19 *: 20 *: 21 *: 22 *: 23 *: 24 *: 25 *: Tuple()).toArray)
  }
}
