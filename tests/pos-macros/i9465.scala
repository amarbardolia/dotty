import scala.quoted._

trait Coll[A] {
  type C[X] // must be abstract
  def map[B]: C[Any] // needs both the type param and a return type that refers to C
}

object QuoteTest {
  def compile(expr: Expr[Any])(using QuoteContext): Expr[Any] = expr match {
    case '{ (??? : Coll[$y]).map[$b] } => ???
  }
}
