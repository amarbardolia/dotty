object App {
  def main(args: Array[String]): Unit = {
    trait ModuleSig {
      type Upper

      trait FooSig {
        type Type <: Upper
        def subst[F[_]](fa: F[Int]): F[Type]
      }

      val Foo: FooSig
    }
    val Module: ModuleSig = new ModuleSig {
      type Upper = Int

      val Foo: FooSig = new FooSig {
        type Type = Int
        def subst[F[_]](fa: F[Int]): F[Type] = fa
      }
    }
    type Upper = Module.Upper
    type Foo = Module.Foo.Type

    sealed abstract class K[F]
    case object K1 extends K[Int]
    case object K2 extends K[Foo]

    val kv: K[Foo] = Module.Foo.subst[K](K1)
    def test(k: K[Foo]): Unit = k match {
      case K2 => ()
    }

    test(kv)
  }
}
