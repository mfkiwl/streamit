package at.dms.kjc.sir.lowering;

import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.lir.*;
import at.dms.kjc.iterator.*;
import java.util.*;

/**
 * This class adds LIR hooks to work functions.
 */
public class LowerWorkFunctions implements StreamVisitor 
{
    /**
     * Lowers the work functions in <str>.
     */
    public static void lower(SIRIterator iter)
    {
        iter.accept(new LowerWorkFunctions());
    }
    
    private void addEntryExit(JMethodDeclaration method)
    {
        // add the entry node before the variable declarations, since
        // the variable declarations might include initializations
        // that read from the input tape.
	method.addStatementFirst(new LIRWorkEntry
				 (LoweringConstants.getStreamContext()));
        // append an exit node
        method.addStatement(new LIRWorkExit
                            (LoweringConstants.getStreamContext()));
        // prepend exit nodes before any return statements, too
        method.accept(new SLIRReplacingVisitor() {
                public Object visitReturnStatement(JReturnStatement self,
                                                   JExpression expr)
                {
                    JStatement[] stmts = new JStatement[2];
                    stmts[0] =
                        new LIRWorkExit(LoweringConstants.getStreamContext());
                    stmts[1] = self;
                    return new JBlock(self.getTokenReference(), stmts, null);
                }
            });
    }
    
    /**
     * PLAIN-VISITS 
     */
    
    /* visit a filter */
    public void visitFilter(SIRFilter self,
			    SIRFilterIter iter) {
	// only worry about actual SIRFilter's, not special cases like
	// FileReader's and FileWriter's
	if (!self.needsWork()) {
	    return;
	}
        // add entry/exit nodes to work function
        addEntryExit(self.getWork());
	// add entry/exit nodes to initial work function, if there is one
	if (self instanceof SIRTwoStageFilter) {
	    addEntryExit(((SIRTwoStageFilter)self).getInitWork());
	}
    }
  
    /**
     * PRE-VISITS 
     */
	    
    /* pre-visit a pipeline */
    public void preVisitPipeline(SIRPipeline self,
				 SIRPipelineIter iter) {
    }
  
    /* pre-visit a splitjoin */
    public void preVisitSplitJoin(SIRSplitJoin self,
				  SIRSplitJoinIter iter) {
    }
  
    /* pre-visit a feedbackloop */
    public void preVisitFeedbackLoop(SIRFeedbackLoop self,
				     SIRFeedbackLoopIter iter) {
    }
  
    /**
     * POST-VISITS 
     */

    /* post-visit a pipeline */
    public void postVisitPipeline(SIRPipeline self,
				 SIRPipelineIter iter) { 
    }
  
    /* post-visit a splitjoin */
    public void postVisitSplitJoin(SIRSplitJoin self,
				   SIRSplitJoinIter iter) {
    }
  
    /* post-visit a feedbackloop */
    public void postVisitFeedbackLoop(SIRFeedbackLoop self,
				      SIRFeedbackLoopIter iter) {
    }
}
