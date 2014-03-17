package scala.tools.refactoring
package tests.enumerators

import tests.util.TestHelper
import org.junit.Assert
import org.junit.Assert._
import sourcegen.SourceGenerator
import common.SilentTracing
import common.ConsoleTracing
import tools.nsc.symtab.Flags
import tools.nsc.ast.parser.Tokens

import language.{postfixOps, implicitConversions}

class SourceGenTest extends TestHelper with SilentTracing {

  import global._

  def generateText(t: => Tree): String = global.ask { () =>
    createText(t, sourceFile = Some(t.pos.source))
  }

  val enumerator: Traversable[String] = ???
  
  def oracle(code: String): Boolean = ???

  @Test
  def testPrintParens() = global.ask { () =>

    for (code <- enumerator) {
	    val ast = treeFrom("""
	    trait tr {
	      def member(a: Int, li: List[Int]) = {
	        (a equals li.head) || true
	      }
	    }
	    """)
	
	    assertTrue( oracle(generateText(ast)) )
    }
  }

}

