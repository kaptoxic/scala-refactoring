package scala.tools.refactoring.implementations

import scala.tools.refactoring.MultiStageRefactoring
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.interactive.Global
import scala.tools.refactoring.common.Change
import scala.tools.refactoring.analysis.FullIndexes

abstract class ExtractLocal(override val global: Global) extends MultiStageRefactoring(global) {
  
  import global._
  
  abstract class PreparationResult {
    def selectedExpression: Tree
  }
  
  abstract class RefactoringParameters {
    def name: String
  }
  
  def prepare(s: Selection) = {
    s.findSelectedOfType[TermTree] match {
      case Some(term) =>
        Right(new PreparationResult {
          val selectedExpression = term
        })
      case None => Left(new PreparationError("no term selected"))
    }
  }
    
  def perform(selection: Selection, prepared: PreparationResult, params: RefactoringParameters): Either[RefactoringError, TreeModifications] = {
    
    import prepared._
    import params._
    
    implicit def replacesTree(t1: Tree) = new {
      def replaces(t2: Tree) = t1 setPos t2.pos
    }
    
    def isInnerMost[T <: Tree](t: T)(implicit m: Manifest[T]) = ! t.children.exists(_.exists(m.erasure.isInstance(_)))
    
    trace("Selected: %s", selectedExpression)
    
    val newVal = mkValDef(name, selectedExpression)
    val valRef = Ident(name)
    
    def findBlockInsertionPosition(root: Tree, near: Tree) = {
      
      def isCandidateForInsertion(t: Tree) = t.pos.includes(near.pos) && PartialFunction.cond(t) {
        case If(_, thenp, _    ) if thenp.pos.includes(near.pos) => true
        case If(_, _    , elsep) if elsep.pos.includes(near.pos) => true
        case b: Block => true
      }
      
      val insertionPoint = root.find {
        case t: Tree if isCandidateForInsertion(t) =>
          // find the smallest possible candidate
          !t.children.exists( _ exists isCandidateForInsertion)
        case _ => false
      }
      
      def refineInsertionPoint(t: Tree) = t match {
        case If(_, thenp, _    ) if thenp.pos.includes(near.pos) => thenp
        case If(_, _    , elsep) if elsep.pos.includes(near.pos) => elsep
        case t => t
      }
      
      insertionPoint map { parent => 
        (parent, refineInsertionPoint(parent))
      }
    }
    
    val (parent, child) = findBlockInsertionPosition(selection.file, selectedExpression) getOrElse {
      return Left(RefactoringError("No insertion point found."))
    }
    
    val changes = new ModificationCollector {
      
      transform(selection.file) {
        
        case t if t == parent => transform(parent) {
        
          case t if t == child =>
          
            val replacedExpression = transform(t) {
              case t: TermTree if t == selectedExpression => valRef replaces selectedExpression
            }
          
            mkBlock(newVal :: replacedExpression :: Nil) replaces t
        }
      }
    }
    
    Right(changes)
  }
}