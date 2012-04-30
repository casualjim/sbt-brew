seq(coffeeSettings:_*)

(includeFilter in (Compile, BrewKeys.coffee)) := ("*.coffee" - "*.no.coffee")
