package util


object Extensions:

  extension [A](a: A)
    inline def |>[B](inline f: A => B): B =
      f(a)

  extension [A](opt: Option[A])
    inline def getOrThrow(inline msg: => String = "Option.getOrThrow: value was empty"): A =
      opt.getOrElse(
        throw java.util.NoSuchElementException(msg)
      )

end Extensions
