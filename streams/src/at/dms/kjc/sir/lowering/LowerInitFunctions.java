package at.dms.kjc.sir.lowering;

import streamit.scheduler.*;

import at.dms.util.IRPrinter;
import at.dms.util.Utils;
import at.dms.kjc.*;
import at.dms.kjc.iterator.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.lir.*;

import java.util.List;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * This class adds LIR hooks to the init functions.
 */
public class LowerInitFunctions implements StreamVisitor {

    private final Schedule schedule;

    /**
     * Construct one of these with schedule <schedule>
     */
    private LowerInitFunctions(Schedule schedule) {
	this.schedule = schedule;
    }

    /**
     * Lowers the init functions in <str>.
     */
    public static void lower(SIRIterator iter, Schedule schedule) {
	iter.accept(new LowerInitFunctions(schedule));
    }

    //
    // PRIVATE METHODS ------------------------------------------------------
    //
    
    /**
     * Registers some <children> with prologue <prologue> to be
     * inserted at the beginning of an init function.  Adds statements
     * to <prologue> that do the registering.
     */
    private void registerChildren(List children, List prologue) {
	// iterate in reverse order so they come out right
	for (ListIterator it = children.listIterator(); it.hasNext(); ) {
	    // extract child
	    SIROperator child = (SIROperator)it.next();
	    if (child instanceof SIRFileReader) {
		// get reader
		SIRFileReader reader = (SIRFileReader)child;
		// register it
		prologue.add(new LIRFileReader(LoweringConstants.
					       getStreamContext(),
					       child.getRelativeName(),
					       reader.getFileName()));
	    } else if (child instanceof SIRFileWriter) {
		// get writer
		SIRFileWriter writer = (SIRFileWriter)child;
		// register it
		prologue.add(new LIRFileWriter(LoweringConstants.
					       getStreamContext(),
					       child.getRelativeName(),
					       writer.getFileName()));
	    } else if (child instanceof SIRJoiner) {
		// get joiner
		SIRJoiner joiner = (SIRJoiner)child;
		// register joiner
		prologue.add(new LIRSetJoiner(LoweringConstants.
					      getStreamContext(),
					      joiner.getType(),
					      joiner.getWays(),
					      joiner.getWeights()));
	    } else if (child instanceof SIRSplitter) {
		// get splitter
		SIRSplitter splitter = (SIRSplitter)child;
		// register splitter
		prologue.add(new LIRSetSplitter(LoweringConstants.
						getStreamContext(),
						splitter.getType(),
						splitter.getWays(),
						splitter.getWeights()));
	    } else if (child instanceof SIRStream) {
		// register the stream
		prologue.add(new LIRSetChild(LoweringConstants.
					     getStreamContext(),
					     /* child type */
					     child.getName(),
					     /* child name */
					     child.getRelativeName()));
	    } else {
		Utils.fail("Unexpected child type: " + child.getClass());
	    }
	}
    }
				 
    /**
     * Registers the tapes in <tapePairs> with new statements in <method>.
     */
    private void registerTapes(List tapePairs, JMethodDeclaration init) {
	// go through tape pairs, declaring tapes...
	for (ListIterator it = tapePairs.listIterator(); it.hasNext(); ) {
	    // get the next pair
	    SIROperator[] pair = (SIROperator[])it.next();
	    // determine type of tape by finding a SIRStream.  We need
	    // to do this in case we're connecting a splitter or
	    // joiner, since the splitters and joiners don't know
	    // about their types.
	    Utils.assert(pair[0] instanceof SIRStream ||
			 pair[1] instanceof SIRStream,
			 "Two many-to-1 or 1-to-many operators connected? " +
			 "Can't determine type of tape in between");
	    CType tapeType;
	    // assign type to either output of source or input of
	    // sink, depending on which we can determine
	    if (pair[0] instanceof SIRStream) {
		tapeType = ((SIRStream)pair[0]).getOutputType();
	    } else {
		tapeType = ((SIRStream)pair[1]).getInputType();
	    }
	    // declare a tape from pair[0] to pair[1]
	    init.addStatement(new LIRSetTape(LoweringConstants.
					     getStreamContext(),
					     /* stream struct 1 */
					     LoweringConstants.
					     getChildStruct(pair[0]),
					     /* stream struct 2 */
					     LoweringConstants.
					     getChildStruct(pair[1]), 
					     /* type on tape */
					     tapeType,
					     /* size of buffer */
	   schedule.getBufferSizeBetween(pair[0], pair[1]).intValue()
					     ));
	}
    }

    /**
     * Registers tapes in FeedbackLoop <str> into <init> function.
     */
    private void registerFeedbackLoopTapes(SIRFeedbackLoop str, 
					   JMethodDeclaration init) {
        // get parent context
        JExpression parentContext = LoweringConstants.getStreamContext();

	// work on BODY...
	SIRStream body = str.getBody();
	// get child context
	JExpression bodyContext = 
	    LoweringConstants.getStreamContext(LoweringConstants.
					       getChildStruct(body));
	// get the input tape size
	int bodyInputSize = schedule.getBufferSizeBetween(str.getJoiner(), 
							  body).intValue();
	// get the output tape size
	int bodyOutputSize = schedule.getBufferSizeBetween(body,
							   str.getSplitter() 
							   ).intValue();
	// register tape
	init.addStatement(new LIRSetBodyOfFeedback(parentContext,
                                                   bodyContext,
						   body.getInputType(),
						   body.getOutputType(),
						   bodyInputSize,
						   bodyOutputSize));
	// work on LOOP...
	SIRStream loop = str.getLoop();
	// get child context
	JExpression loopContext = 
	    LoweringConstants.getStreamContext(LoweringConstants.
					       getChildStruct(loop));
	// get the input tape size
	int loopInputSize = schedule.getBufferSizeBetween(str.getSplitter(), 
							  loop).intValue();
	// get the output tape size
	int loopOutputSize = schedule.getBufferSizeBetween(loop,
							   str.getJoiner() 
							   ).intValue();
	// register tape
	init.addStatement(new LIRSetLoopOfFeedback(parentContext,
                                                   loopContext,
						   loop.getInputType(),
						   loop.getOutputType(),
						   loopInputSize,
						   loopOutputSize));
    }

    /**
     * Registers tapes in SplitJoin <str> into <init> function.
     */
    private void registerSplitJoinTapes(SIRSplitJoin str, 
					JMethodDeclaration init) {
        // get parent context
        JExpression parentContext = LoweringConstants.getStreamContext();
	// go through elements
	for (int i=0; i<str.size(); i++) {
	    // get i'th child
	    SIRStream child = str.get(i);
	    // get context for child
	    JExpression childContext = 
		LoweringConstants.getStreamContext(LoweringConstants.
						   getChildStruct(child
								  ));
	    // get the input tape size
	    int inputSize = schedule.getBufferSizeBetween(str.getSplitter(), 
							  child).intValue();
	    // get the output tape size
	    int outputSize = schedule.getBufferSizeBetween(child,
							   str.getJoiner() 
							   ).intValue();
	    // register an LIR node to <init>
	    Utils.assert(child.getInputType()!=null,
			 "null child: " + child.getName() + " " +
			 child.getIdent());
	    
	    init.addStatement(new LIRSetParallelStream(parentContext,
                                                       childContext,
						       i,
						       child.getInputType(),
						       child.getOutputType(),
						       inputSize,
						       outputSize));
	}
    }

    private void doFeedbackLoopDelay(SIRFeedbackLoop str,
                                     int delay,
                                     JMethodDeclaration initPath,
                                     JMethodDeclaration init) {
        JExpression data = LoweringConstants.getDataField();
        JExpression context = LoweringConstants.getStreamContext();
        init.addStatement(new LIRSetDelay(data,
                                          context,
                                          delay,
                                          str.getInputType(),
                                          new LIRFunctionPointer(initPath)));
    }

    /**
     * Adds initialization for child streams in <str>.
     */
    private void lowerInitStatements(SIRContainer str) {
	for (int i=0; i<str.size(); i++) {
	    // get call to init function
	    JStatement initCall = lowerInitStatement(str.get(i),
						       str.getParams(i));
	    // append call to init of <str>
	    str.getInit().addStatement(initCall);
	}
    }

    /**
     * Lowers an SIRInitStatement and returns the result.
     */
    private JStatement
	lowerInitStatement(SIRStream str, List args) {
	// if the target is a special type that doesn't need
	// initializing, then just return an empty statement
	if (!str.needsInit()) {
	    return new JEmptyStatement(null, null);
	}

	// create the new argument--the reference to the child's state
	JExpression childState 
	    = LoweringConstants.getChildStruct(str);
	// create new argument list
	JExpression[] newArgs = new JExpression[args.size() + 1];
	// set new arg
	newArgs[0] = childState;
	// set rest of args
	for (int i=0; i<args.size(); i++) {
	    newArgs[i+1] = (JExpression)args.get(i);
	}
	// return method call statement
	return new JExpressionStatement(
	      null,
	      new JMethodCallExpression(/* tokref */
					null,
					/* prefix */
					null,
					/* ident */
					LoweringConstants.getInitName(str),
					/* args */
					newArgs),
	      null);
    }
    //
    // VISITOR STUFF ------------------------------------------------------
    //
    
    /**
     * visit a filter 
     */
    public void visitFilter(SIRFilter self,
			    SIRFilterIter iter) {
	// only worry about actual SIRFilter's, not special cases like
	// FileReader's and FileWriter's
	if (!self.needsWork()) {
	    return;
	}
	
	JMethodDeclaration init = self.getInit();
	// set stream type to filter
	init.addStatement(new LIRSetStreamType(LoweringConstants.
					       getStreamContext(),
					       LIRStreamType.LIR_FILTER));
	// set push count
	init.addStatement(new LIRSetPush(LoweringConstants.
					 getStreamContext(),
					 self.getPushInt()));
	// set peek count
	init.addStatement(new LIRSetPeek(LoweringConstants.
					 getStreamContext(),
					 self.getPeekInt()));
	// set pop count
	init.addStatement(new LIRSetPop(LoweringConstants.
					getStreamContext(),
					self.getPopInt()));

	// set work function
	init.addStatement(new LIRSetWork(LoweringConstants.
					 getStreamContext(),
					 new LIRFunctionPointer(
					 self.getWork().getName())));
					 /*
					 LoweringConstants.
					 getWorkName(self))));*/
    }

    /**
     * Visits an SIRContainer to lower its init function.
     */
    private void visitContainer(SIRContainer self,
				JMethodDeclaration init) {
	// translate init statements to function calls with context.
	// this is modifying <init> without adding/removing extra
	// stuff.
	lowerInitStatements(self);

	// now add some things to the init function... 

	// first, a prologue that comes before the original statements
	// in the init function.  It is a list of statements.
	List prologue = new LinkedList();

	// start building up the prologoue, in order...
	// first, set stream type to pipeline
	prologue.add(new LIRSetStreamType(LoweringConstants.
					  getStreamContext(),
					  self.getStreamType()));

	// set work function, if there is one
	if (self.getWork()!=null) {
	    prologue.add(new LIRSetWork(LoweringConstants.
					getStreamContext(),
					new LIRFunctionPointer(
					   self.getWork().getName())));
	}

	// register children
	registerChildren(self.getChildren(), prologue);

	// add the prologue to the init function, but AFTER any
	// variable declarations.  so find where the last variableDecl
	// is...
	int count=0;
	ListIterator it = init.getStatementIterator();
	while (it.hasNext() && 
	       it.next() instanceof JVariableDeclarationStatement) {
	    count++;
	}
	init.addAllStatements(count, prologue);
    }
	
    /* pre-visit a pipeline */
    public void preVisitPipeline(SIRPipeline self,
				 SIRPipelineIter iter) {
	// do standard container stuff
	visitContainer(self, self.getInit());
	// register tapes between children in init function
	registerTapes(self.getTapePairs(), self.getInit());
    }

    /* pre-visit a splitjoin */
    public void preVisitSplitJoin(SIRSplitJoin self,
				  SIRSplitJoinIter iter) {
	// do standard container stuff
	visitContainer(self, self.getInit());
	// register tapes
	registerSplitJoinTapes(self, self.getInit());
    }

    /* pre-visit a feedbackloop */
    public void preVisitFeedbackLoop(SIRFeedbackLoop self,
				     SIRFeedbackLoopIter iter) {
	// do standard container stuff
	visitContainer(self, self.getInit());
	// register tapes
	registerFeedbackLoopTapes(self, self.getInit());
        // set up the delay function
        doFeedbackLoopDelay(self, self.getDelayInt(), self.getInitPath(), self.getInit());
    }

    /* post-visit a pipeline */
    public void postVisitPipeline(SIRPipeline self,
				  SIRPipelineIter iter) {
	// do nothing -- all work is in preVisit
    }

    /* post-visit a splitjoin */
    public void postVisitSplitJoin(SIRSplitJoin self,
				   SIRSplitJoinIter iter) {
	// do nothing -- all work is in preVisit
    }

    /* post-visit a feedbackloop */
    public void postVisitFeedbackLoop(SIRFeedbackLoop self,
				      SIRFeedbackLoopIter iter) {
	// do nothing -- all work is in preVisit
    }
}

