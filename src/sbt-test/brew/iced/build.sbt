seq(coffeeSettings:_*)

(BrewKeys.iced in (Compile, BrewKeys.coffee)) := true

InputKey[Unit]("contents") <<= inputTask { (argsTask: TaskKey[Seq[String]]) =>
  (argsTask, streams) map {
    (args, out) =>
      args match {
        case pair @ Seq(given, expected) =>
          pair.zip(pair map { f => IO.read(file(f)).trim }) match {
            case Seq((_, givenC), (_, expectedC)) =>
              if(givenC.equals(expectedC)) out.log.debug(
                "Contents match"
              ) else error(
                "Contents of (%s)\n'%s' does not match (%s)\n'%s'" format(
                  given, givenC, expected, expectedC)
              )
          }
      }
  }
}
