package scala.tools.refactoring.regeneration

import scala.collection.mutable.ListBuffer

trait LayoutHandler extends scala.tools.refactoring.LayoutPreferences {
  self: scala.tools.refactoring.Tracing =>
  
  def processRequisites(current: Fragment, layoutAfterCurrent: String, layoutBeforeNext: String, next: Fragment) = context("requisites") {
  
    trace("layout     %s, %s", layoutAfterCurrent, layoutBeforeNext)
    
    // check for overlapping layouts and requirements! => testSortWithJustOne
    def getRequisite(r: Requisite) = if(!(SourceHelper.stripComment(layoutAfterCurrent + layoutBeforeNext)).contains(r.check)) {
      trace("%s does not contain requisite %s → write %s", layoutAfterCurrent + layoutBeforeNext, r.check, r.write)
      r.write 
    } else {
      ""
    }
      
    def mapRequirements(rs: ListBuffer[Requisite]) = rs.map( getRequisite ) mkString ""

    val NewlineSeparator = """(?ms)(.*?)(\n.*)""".r
    
    val(layoutBeforeNewline, layoutAfterNewline) = layoutAfterCurrent match {
      case NewlineSeparator(before, after) => (before, after)
      case s => (s, "")
    }
      
    using(layoutBeforeNewline + mapRequirements(current.requiredAfter) + layoutAfterNewline + layoutBeforeNext + mapRequirements(next.requiredBefore)) { res =>
      if(res == "\n\n}") {
        println("here")
      }
      trace("results in %s", res)
    }
  }
  
  def fixIndentation(layout: String, existingIndentation: Option[Tuple2[Int, Int]], isEndOfScope: Boolean, currentScopeIndentation: Int): String = context("fix indentation") {

    if(layout.contains('\n')) {
      
      def indentString(length: Int) = {
        layout.replaceAll("""(?ms)\n[\t ]*""", "\n" + (" " * length))
      }
      
      existingIndentation match {
        case Some((originalScopeIndentation, originalIndentation)) =>
          trace("this is a reused fragment")

            val newIndentation = currentScopeIndentation + (originalIndentation - originalScopeIndentation)
            
            trace("original indentation was %d, original scope indentation was %d", originalIndentation, originalScopeIndentation)
            trace("new scope's indentation is %d → indent to %d", currentScopeIndentation, newIndentation)
            
            if(newIndentation != originalIndentation) indentString(newIndentation) else layout
          
        case None =>
          trace("this is a new fragment")
        
          if(isEndOfScope) {
            trace("at the end of the scope, take scope's parent indentation %d", currentScopeIndentation)
            indentString(currentScopeIndentation)
          } else {
            trace("new scope's indentation is %d → indent to %d ", currentScopeIndentation, currentScopeIndentation + 2)
            indentString(currentScopeIndentation + 2)
          }
      }
    } else layout
  }

  def splitLayoutBetween(parts: Option[Triple[Fragment,List[Fragment],Fragment]]) = parts match {
    
    case Some((left, layoutFragments, right)) =>
    
      def mergeLayoutWithComment(l: Seq[Char], c: Seq[Char]) = l zip c map {
        case (' ', _1) => _1
        case (_1, ' ') => _1
        case ('\n', '\n') => '\n'
      } mkString
    
      context("split layout") {
        val EmptyParens = """(.*?\(\s*\)\s*)(.*)""".r
        val OpeningBrace = """(.*?\()(.*)""".r
        val ClosingBrace = """(?ms)(.*?)(\).*)""".r
        val Comma = """(.*?),\s?(.*)""".r
        val NewLine = """(?ms)(.*?\n)(.*)""".r
        
        val (layout, comments) = SourceHelper splitComment (layoutFragments mkString)

        trace("splitting layout %s between %s and %s. Comments are %s", layout, left, right, comments)
        
        val(l, r, why) = (left, layout, right) match {
          case(_, EmptyParens(l, r) , _) => (l, r, "EmptyParens")
          case(_, OpeningBrace(l, r), _) => (l, r, "OpeningBrace")
          case(_, ClosingBrace(l, r), _) => (l, r, "ClosingBrace")
          case(_, NewLine(l, r)     , _) => (l, r, "NewLine")
          case(_, Comma(l, r),        _) => (l, r, "Comma")
          case(_, s                 , _) => (s, "","NoMatch")
        }
        trace("Rule %s splits layout into %s and %s", why, l, r)
        
        (mergeLayoutWithComment(l, comments), mergeLayoutWithComment(r reverse, comments reverse) reverse)
      }
    case None => ("", "")
  }
}