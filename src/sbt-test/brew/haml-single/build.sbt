seq(hamlSettings:_*)

sourceDirectory in (Compile, BrewKeys.haml) <<= (sourceDirectory in (Compile, BrewKeys.coffee))

resourceManaged in (Compile, BrewKeys.haml) <<= (resourceManaged in (Compile, BrewKeys.coffee))

InputKey[Unit]("contents") <<= inputTask { (argsTask: TaskKey[Seq[String]]) =>
  (argsTask, streams) map {
    (args, out) =>
      args match {
        case Seq(given, expected) =>
          if(IO.read(file(given)).trim.equals(IO.read(file(expected)).trim)) out.log.debug(
            "Contents match"
          )
          else error(
            "Contents of (%s)\n'%s' does not match (%s)\n'%s'" format(
              given, IO.read(file(given)), expected, IO.read(file(expected))
            )
          )
      }
  }
}
