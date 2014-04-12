package com.github.fommil.utils

import org.parboiled.scala._

/**
 * Boilerplate killer when writing parsers in parboiled where strings could
 * easily have whitespace before or after. Any space at the start or end of a
 * stray `String` will be interpreted as `Whitespace`, and stray `Strings` are
 * also lifted to `Rule0`s.
 */
trait WhitespaceAwareParser extends Parser {
  private def stripStart(s: String) = s.dropWhile(_ == ' ')
  private def stripEnd(s: String) = stripStart(s.reverse).reverse
  private def stripBoth(s: String) = stripStart(stripEnd(s)) // NOT s.trim

  implicit def strToRule(string: String): Rule0 = string match {
    case " " => WhiteSpace
    case s if (s startsWith " ") & (s endsWith " ") => WhiteSpace ~ str(stripBoth(s)) ~ WhiteSpace
    case s if s startsWith " " => WhiteSpace ~ str(stripStart(s))
    case s if s endsWith " " => str(stripEnd(s)) ~ WhiteSpace
    case s => str(s)
  }

  def WhiteSpace = rule { zeroOrMore(anyOf(" \n\r\t\f")) }
}