package at.dms.kjc.sir.lowering.partition.dynamicprog;

import java.util.*;
import java.io.*;

import at.dms.kjc.*;
import at.dms.util.*;
import at.dms.kjc.iterator.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.kjc.sir.lowering.fusion.*;
import at.dms.kjc.sir.lowering.fission.*;
import at.dms.kjc.sir.lowering.partition.*;

abstract class DPConfigContainer extends DPConfig {
    /**  
     * A_s[x1][x2][y1][y2][n][j] holds minimum cost of assigning
     * children (x1..x2, y1..y2) of stream s to n tiles.  <j> is 1 if
     * these children are next to a downstream joiner in the current
     * configuration; <j> is zero otherwise.  If this corresponds to a
     * filter's config, then A is null.
     */
    private int[][][][][][] A;

    /**
     * Holds the minimal SUM of the costs of the children.  This is
     * used as a tiebreaker if two different tracebacks have the same
     * A value.
     */
    private int[][][][][][] B;
    /**
     * Value to initialize A, B with.
     */
    private int NOT_MEMOIZED = -1;
    
    /**
     * The stream for this container.
     */
    protected SIRContainer cont;
    /**
     * Partitioner corresponding to this.
     */
    protected DynamicProgPartitioner partitioner;
    /**
     * Specifies the width of the y_i'th component of this
     */
    private int[] width;
    /**
     * Whether or not the i'th row of this is uniform -- that is,
     * if all of its components have the same work estimate.
     */
    boolean[] uniform;
    /**
     * Max width from i to j, for i<=j.
     */
    int maxWidth[][];
    /**
     * Whether or not there is the same width from i to j, for i<=j.
     */
    boolean sameWidth[][];
    
    /**
     * <width> and <height> represent the dimensions of the stream.
     */
    protected DPConfigContainer(SIRContainer cont, DynamicProgPartitioner partitioner, 
				int[] width, int height) {
	super(partitioner);
	this.cont = cont;
	this.partitioner = partitioner;
	this.width = width;
	// find maxWidth
	int maxWidth = -1;
	for (int i=0; i<height; i++) {
	    maxWidth = Math.max(maxWidth, width[i]);
	}
	// for simplicity, allocate the bounding box for A
	this.A = new int[maxWidth][maxWidth][height][height][partitioner.getNumTiles()+1][2];
	initArray(A);
	this.B = new int[maxWidth][maxWidth][height][height][partitioner.getNumTiles()+1][2];
	initArray(B);
	this.uniform = new boolean[height];
	initUniform();
	//maxAlias();
	initWidth();
    }

    private void initArray(int[][][][][][] arr) {
	for (int i1=0; i1<arr.length; i1++) {
	    for (int i2=0; i2<arr[0].length; i2++) {
		for (int i3=0; i3<arr[0][0].length; i3++) {
		    for (int i4=0; i4<arr[0][0][0].length; i4++) {
			for (int i5=0; i5<arr[0][0][0][0].length; i5++) {
			    for (int i6=0; i6<2; i6++) {
				arr[i1][i2][i3][i4][i5][i6] = NOT_MEMOIZED;
			    }
			}
		    }
		}
	    }
	}
    }

    private void initWidth() {
	int height=A[0][0].length;
	maxWidth = new int[height][height];
	sameWidth = new boolean[height][height];
	for (int y1=0; y1<height; y1++) {
	    int max = width[y1];
	    boolean same = true;
	    for (int y2=y1; y2<height; y2++) {
		max = Math.max(max, width[y2]);
		same = same && width[y2]==width[y1];
		maxWidth[y1][y2] = max;
		sameWidth[y1][y2] = same;
	    }
	}
    }

    /**
     * Initialize <uniform> array and introduce aliasing into [A] and [B] accordingly.
     */
    private void initUniform() {
	// find the uniform rows
	WorkEstimate work = partitioner.getWorkEstimate();
	for (int i=0; i<uniform.length; i++) {
	    SIRStream child1 = childConfig(0, i).getStream();
	    int work1=0;
	    // right now work estimate is stupid and only gets filter
	    // works... should be extended to deal with containers and
	    // pre-defined filters as well.
	    if (child1 instanceof SIRFilter && !(child1 instanceof SIRPredefinedFilter)) {
		work1 = work.getWork((SIRFilter)child1);
	    } else {
		continue;
	    }
	    // will try disproving this
	    uniform[i] = true;
	    search:
	    for (int j=1; j<width[i]; j++) {
		SIRStream child2 = childConfig(j, i).getStream();
		if (!(child2 instanceof SIRFilter) || (child2 instanceof SIRPredefinedFilter) || work1!=work.getWork((SIRFilter)child2)) {
		    uniform[i] = false;
		    break search;
		}
	    }
	}
	// print out the uniform rows
	/*
	for (int i=0; i<uniform.length; i++) {
	    if (uniform[i] && width[i]>1) {
		System.err.println("Found row " + i + "/" + (uniform.length-1) + " of " + cont.getName() + " to be uniform.");
	    }
	}
	*/
	// now find maximal uniform regions with the same width, and alias their memoization tables
	int low=0;
	while (low<uniform.length) {
	    while (low<uniform.length && !uniform[low]) {
		low++;
	    }
	    if (low<uniform.length) {
		int high = low;
		while (high+1<uniform.length && uniform[high+1] && (width[high+1]==width[low])) {
		    high++;
		}
		// alias low..high across the board, to point to values in 0'th column
		for (int i1=low; i1<=high; i1++) {
		    // only aliasing from i2=i1..high matters, but we'd just as well go i2=low...high
		    for (int i2=low; i2<=high; i2++) {
			for (int xWidth=0; xWidth<width[low]-1; xWidth++) {
			    for (int xStart=1; xStart<width[low]-xWidth; xStart++) {
				A[xStart][xStart+xWidth][i1][i2] = A[0][xWidth][i1][i2];
				B[xStart][xStart+xWidth][i1][i2] = B[0][xWidth][i1][i2];
			    }
			}
		    }
		}
	    }
	    low++;
	}
    }

    public static int aliases=0;
    // NOTE: this is a very unsafe hash because it takes the sum of
    // work instead of a hash of work.
    private void maxAlias() {
	// consider every rectangle [x1,y1]->[x2,y2]
	int height=A[0][0].length;
	for (int y1=0; y1<height; y1++) {
	    int rectWidth = width[y1];
	    for (int y2=y1; y2<height; y2++) {
		rectWidth = Math.min(rectWidth, width[y2]);
		for (int x1=0; x1<rectWidth; x1++) {
		    for (int x2=0; x2<rectWidth; x2++) {
			// my rectangle
			int rect1=workFor(x1,x2,y1,y2);
			if (rect1==-1) { continue; }
			// rectangle to left
			int rect3=workFor(x1-1,x2-1,y1,y2);
			if (rect1==rect3) {
			    A[x1][x2][y1][y2] = A[x1-1][x2-1][y1][y2];
			    //System.err.println("For " + cont.getName() + ", aliasing (" + x1 + ", " + y1 + ")->(" + x2 + ", " + y2 + ") left");
			    aliases++;
			    continue;
			}
			// rectangle above
			int rect2=workFor(x1,x2,y1-1,y2-1);
			if (rect1==rect3) {
			    A[x1][x2][y1][y2] = A[x1][x2][y1-1][y2-1];
			    //System.err.println("For " + cont.getName() + ", aliasing (" + x1 + ", " + y1 + ")->(" + x2 + ", " + y2 + ") up");
			    aliases++;
			    continue;
			}
		    }
		}
	    }
	}
    }

    /**
     * Return work for rectangle in range; return -1 if undefined.
     */
    private int workFor(int x1, int x2, int y1, int y2) {
	// check bounds
	if (x1<0 || y1<0 || x2<0 || y2<0 || x2<x1 || y2<y1 || x2>width[y1]-1 ) {
	    return -1;
	}
	// do sum
	int sum = 0;
	for (int x=x1; x<=x2; x++) {
	    for (int y=y1; y<=y2; y++) {
		int s = workFor(childConfig(x, y).getStream());
		// quit if we ever hit undefined
		if (s==-1) {
		    return -1;
		}  else {
		    sum+=s;
		}
	    }
	}
	return sum;
    }

    /**
     * Right now work estimate doesn't recognize some things like
     * predefined filters and containers, so wrap it like this.
     */
    private int workFor(SIRStream str) {
	try {
	    return partitioner.getWorkEstimate().getWork((SIRFilter)str);
	} catch (RuntimeException e) {
	    return -1;
	}
    }

    public SIRStream getStream() {
	return cont;
    }

    /**
     * Requires <str> is a container.
     */
    protected void setStream(SIRStream str) {
	Utils.assert(str instanceof SIRContainer);
	this.cont = (SIRContainer)str;
    }

    private void debugMessage(String str) {
	if (KjcOptions.debug) {
	    for (int i=0; i<indent; i++) { System.err.print(" "); }
	    System.err.println(str);
	}
    }

    protected DPCost get(int tileLimit, int nextToJoiner) {
	// otherwise, compute it
	return get(0, A.length-1, 0, A[0][0].length-1, tileLimit, nextToJoiner);
    }

    private static int indent=0;
    protected DPCost get(int x1, int x2, int y1, int y2, int tileLimit, int nextToJoiner) {
	boolean debug = x1==0 && x2==2 && y1==3 && y2==5;// && tileLimit==5 && nextToJoiner==1;
	indent++;
	String callStr = cont.getName() + ".get(" + x1 + ", " + x2 + ", " + y1 + ", " + y2 + ")[" + tileLimit + "][" + nextToJoiner +"]";
	debugMessage("calling " + callStr); 

	Utils.assert(x1<maxWidth[y1][y2], "x1=" + x1 + " <= maxWidth[y1][y2]= " + maxWidth[y1][y2] + " with x2=" + x2 + " with y1= " + y1 + " and y2=" + y2 + " in " + cont);
	Utils.assert(x1<=x2, "x1=" + x1 + " > x2= " + x2 + " with y1= " + y1 + " and y2=" + y2 + " in " + cont);

	// if we've exceeded the width of this node, then trim down to actual width
	if (x2>maxWidth[y1][y2]-1) {
	    x2 = maxWidth[y1][y2]-1;
	}

	// if we've memoized the value before, return it
	if (A[x1][x2][y1][y2][tileLimit][nextToJoiner]!=NOT_MEMOIZED) {
	    Utils.assert(B[x1][x2][y1][y2][tileLimit][nextToJoiner]!=NOT_MEMOIZED, "Memoized A but not B.");
	    /*
	      System.err.println("Found memoized A[" + child1 + "][" + child2 + "][" + tileLimit + "] = " + 
	      A[child1][child2][tileLimit] + " for " + cont.getName());
	    */
	    indent--;
	    return new DPCost(A[x1][x2][y1][y2][tileLimit][nextToJoiner],
			      B[x1][x2][y1][y2][tileLimit][nextToJoiner]);
	}

	// if we are down to one child, then descend into child
	if (x1==x2 && y1==y2) {
	    DPCost childCost = childConfig(x1, y1).get(tileLimit, nextToJoiner); 
	    A[x1][x2][y1][y2][tileLimit][nextToJoiner] = childCost.getMaxCost();
	    B[x1][x2][y1][y2][tileLimit][nextToJoiner] = childCost.getSumCost();
	    //System.err.println("Returning " + childCost + " from descent into child.");
	    indent--;
	    return childCost;
	}

	// otherwise, if <tileLimit> is 1, then just sum the work
	// of our components
	if (tileLimit==1) {
	    // see if we have an unfusable filter in this record.  If
	    // we do and it isn't the only filter in the record, then
	    // return infinite cost
	    PartitionRecord rec = new PartitionRecord();
	    for (int y=y1; y<=y2; y++) {
		for (int x=x1; x<=Math.min(x2,width[y]-1); x++) {
		    IterFactory.createFactory().createIter(childConfig(x,y).getStream()).accept(new RecordingStreamVisitor(rec));
		}
	    }
	    int numFilters = 0;
	    boolean fusable = true;
	    for (int i=0; i<rec.size(); i++) {
		if (rec.get(i) instanceof SIRFilter) {
		    SIRFilter filter = (SIRFilter)rec.get(i);
		    numFilters++;
		    if (!isFusable(filter)) {
			fusable = false;
		    }
		}
	    }
	    if (!fusable && numFilters>1) {
		return new DPCost(Integer.MAX_VALUE/2, Integer.MAX_VALUE/2);
	    }

	    // we could divide the rectangle into 4 pieces to promote
	    // caching of the sum.  however, this makes it complicated
	    // when you're taking into account horizontal fusion
	    // overhead, because it isn't clear what the outermost
	    // horizontal fusion was (you start to count fusion of the
	    // toplevel as well as sub-pieces of the graph).  so,
	    // instead, sum the children one column at a time, so that
	    // no horizontal fusion overhead is in the children.

	    // so, there are two cases:  
	    //  1. we are a pipeline, or we are a ragged rectangle. we make a horizontal 
	    //     cut, summing the top and bottom
	    //  2. we are perfectly rectangular, in which case we sum across the 
	    //     columns (making vertical cuts)
	    
	    // the horizontal fusion overhead only gets factored in to <2>

	    // test for perfect rectangular
	    boolean rectangular = sameWidth[y1][y2];

	    int max = 0;
	    int sum = 0;
	    if (x1==x2 || !rectangular) { // case #1
		int yPivot;
		if (x1==x2) {
		    // if we have pipeline, then divide in half
		    yPivot = (int)Math.floor(((float)(y1+y2))/2.0);
		} else {
		    // if ragged, then find the first change in width and, recurse
		    yPivot=y1;
		    while (width[yPivot]==width[yPivot+1]) {
			yPivot++;
		    }
		}
		Utils.assert(yPivot<y2);
		
		DPCost cost1 = get(x1, x2, y1, yPivot, tileLimit, nextToJoiner);
		DPCost cost2 = get(x1, x2, yPivot+1, y2, tileLimit, nextToJoiner);
		max = cost1.getMaxCost() + cost2.getMaxCost();
		sum = cost1.getSumCost() + cost2.getSumCost();
	    } else { // case #2
		// sum across columns
		for (int i=x1; i<=x2; i++) {
		    DPCost cost = get(i, i, y1, y2, tileLimit, nextToJoiner);
		    max += cost.getMaxCost();
		    sum += cost.getSumCost();
		}
		int overhead = getHorizontalFusionOverhead(x1, x2, y1, y2);
		max += overhead;
		sum += overhead;
	    }

	    // since we went to down to one child, the cost is the
	    // same whether or not there is a joiner, so record both
	    // ways.
	    A[x1][x2][y1][y2][tileLimit][0] = max;
	    A[x1][x2][y1][y2][tileLimit][1] = max;
	    B[x1][x2][y1][y2][tileLimit][0] = sum;
	    B[x1][x2][y1][y2][tileLimit][1] = sum;

	    indent--;
	    return new DPCost(max, sum);
	}

	// otherwise, we're going to try making a cut... but first see
	// if there will be any tiles left after accounting for the joiner
	boolean needsJoiner = (width[y2]>1) && (nextToJoiner!=1);
	int tilesAvail = needsJoiner ? tileLimit - 1 : tileLimit;
	// if there is only one tile available, then recurse
	Utils.assert(tilesAvail>0);
	Utils.assert(tileLimit>1);
	// only force a cut if it's impossible to make a horizontal cut
	if (tilesAvail==1 && y1==y2) {
	    // must have added a joiner if you've gotten to this point
	    DPCost cost = get(x1, x2, y1, y2, tilesAvail, 1);
	    A[x1][x2][y1][y2][tileLimit][nextToJoiner] = cost.getMaxCost();
	    B[x1][x2][y1][y2][tileLimit][nextToJoiner] = cost.getSumCost();
	    indent--;
	    return cost;
	}

	// otherwise, try making a vertical cut...  see if we can do a
	// vertical cut -- first, that there are two streams to cut
	// between and that there are enough tiles to have two
	// children
	boolean tryVertical = x1<x2 && sameWidth[y1][y2] && tilesAvail>1;

	// then, if we're starting on a pipeline, and have more than
	// one row, and this is our first vertical cut, that we can
	// remove the synchronization between y1 and y2
	boolean firstVertCut = x1==0 && x2==width[y1]-1;
	if (tryVertical && y1<y2) {
	    if (cont instanceof SIRPipeline && firstVertCut) {
		// make a copy of pipeline, to see if we can remove
		// the sync.
		SIRPipeline copy = new SIRPipeline(cont.getParent(),cont.getIdent()+"_copy");
		for (int i=y1; i<=y2; i++) { copy.add(((SIRPipeline)cont).get(i)); }
		// now remove synchronization in <copy>.
		RefactorSplitJoin.removeMatchingSyncPoints(copy);
		// undo effects of adding to someone else
		cont.reclaimChildren();
		// now if we only have one splitjoin left as the
		// child, sync removal was successful, and we can do a
		// cut
		tryVertical = (copy.size()==1);
	    }
	}

	// try a vertical cut if possible.  A vertical cut will
	// necessitate a joiner at this node, if we don't already have
	// one.  This has two consequences: 1) the nextToJoiner
	// argument for children will be true (1), and 2) A tile is
	// used by the joiner.  We represent #2 by tilesAvail, the
	// number of tailes available after the joiner is taken.
	int minCost = Integer.MAX_VALUE;
	int minSum = Integer.MAX_VALUE;
	if (tryVertical) {
	    for (int xPivot=x1; xPivot<x2; xPivot++) {
		for (int tPivot=1; tPivot<tilesAvail; tPivot++) {
		    DPCost cost1 = get(x1, xPivot, y1, y2, tPivot, 1);
		    DPCost cost2 = get(xPivot+1, x2, y1, y2, tilesAvail-tPivot, 1);
		    int cost = Math.max(cost1.getMaxCost(), cost2.getMaxCost());
		    int sum = cost1.getSumCost() + cost2.getSumCost();
		    if (cost < minCost || (cost==minCost && sum < minSum)) {
			//System.err.println("possible vertical cut at x=" + xPivot + " from y=" + y1 + " to y=" + y2 + " in " + cont.getName());
			minCost = cost;
			minSum = sum;
		    }
		}
	    }
	}

	// try making horizontal cut (for splitjoin, pipeline,
	// feedbackloop).  In this case, we keep whatever joiner we
	// have for the bottom piece of the cut, but the top piece
	// will need to make its own joiner.  The arguments are thus
	// false (0) for the top, and true (1) for the bottom.
	for (int yPivot=y1; yPivot<y2; yPivot++) {
	    for (int tPivot=1; tPivot<tileLimit; tPivot++) {
		DPCost cost1 = get(x1, x2, y1, yPivot, tPivot, 0);
		DPCost cost2 = get(x1, x2, yPivot+1, y2, tileLimit-tPivot, nextToJoiner);
		int cost = Math.max(cost1.getMaxCost(), cost2.getMaxCost());
		int sum = cost1.getSumCost() + cost2.getSumCost();
		if (cost < minCost || (cost==minCost && sum < minSum)) {
		    //System.err.println("possible horizontal cut at y=" + yPivot + " from x=" + x1 + " to x=" + x2 + " in " + cont.getName());
		    minCost = cost;
		    minSum = sum;
		}
	    }
	}

	Utils.assert(minCost!=Integer.MAX_VALUE, "Failed to make cut on container: " + cont.getName());

	A[x1][x2][y1][y2][tileLimit][nextToJoiner] = minCost;
	B[x1][x2][y1][y2][tileLimit][nextToJoiner] = minSum;
	indent--;
	return new DPCost(minCost, minSum);
    }

    /**
     * Assuming a uniform with across y1...y2 of this, return estimate
     * of overhead of fusing the contents.
     */
    private int getHorizontalFusionOverhead(int x1, int x2, int y1, int y2) {
	// on the cluster backend, there is no horizontal fusion
	if (KjcOptions.cluster!=-1 && !KjcOptions.fusion) {
	    return 0;
	}
	int overhead = 0;
	// for filters, add cost estimate according to their
	// rates; otherwise, add generic cost estimate...
	// do input filters
	for (int i=x1; i<=x2; i++) {
	    DPConfig config = childConfig(i,y1);
	    if (config instanceof DPConfigFilter) {
		// add input rate
		SIRFilter filter = (SIRFilter)config.getStream();
		overhead += (filter.getPopInt() * 
			     partitioner.getWorkEstimate().getReps(filter) * 
			     DynamicProgPartitioner.HORIZONTAL_FILTER_OVERHEAD_FACTOR);
	    } else {
		// add generic rate
		overhead += DynamicProgPartitioner.HORIZONTAL_CONTAINER_OVERHEAD;
	    }
	}
	// do output filters
	for (int i=x1; i<=x2; i++) {
	    DPConfig config = childConfig(i,y2);
	    if (config instanceof DPConfigFilter) {
		// add input rate
		SIRFilter filter = (SIRFilter)config.getStream();
		overhead += filter.getPushInt() * 
		    partitioner.getWorkEstimate().getReps(filter) * 
		    DynamicProgPartitioner.HORIZONTAL_FILTER_OVERHEAD_FACTOR;
	    } else {
		// add generic rate
		overhead += DynamicProgPartitioner.HORIZONTAL_CONTAINER_OVERHEAD;
	    }
	}
	return overhead;
    }

    /**
     * Returns whether or not it is okay to fuse <filter> with others.
     */
    private boolean isFusable(SIRFilter filter) {
	// don't fuse message receivers because they have a lot of communication overhead
	SIRPortal[] portal = SIRPortal.getPortalsWithReceiver(filter);
	if (portal.length>=1) {
	    return false;
	}
	// don't fuse file readers or file writers
	if (filter instanceof SIRFileReader || filter instanceof SIRFileWriter) {
	    return false;
	}
	return true;
    }

    /**
     * Traceback function.
     */
    public SIRStream traceback(LinkedList partitions, PartitionRecord curPartition, int tileLimit, int nextToJoiner, SIRStream str) {
	SIRStream result = traceback(partitions, curPartition, 0, A.length-1, 0, A[0][0].length-1, tileLimit, nextToJoiner, str);
	// if the whole container is assigned to one tile, record it
	// as such.
	if (tileLimit==1) {
	    IterFactory.createFactory().createIter(cont).accept(new RecordingStreamVisitor(curPartition));
	} 
	return result;
    }
	
    /**
     * Traceback helper function.
     */
    protected SIRStream traceback(LinkedList partitions, PartitionRecord curPartition,
				  int x1, int x2, int y1, int y2, int tileLimit, int nextToJoiner, SIRStream str) {
	indent++;
	String callStr = cont.getName() + ".traceback(" + x1 + ", " + x2 + ", " + y1 + ", " + y2 + ")[" + tileLimit + "][" + nextToJoiner +"]";
	debugMessage("calling " + callStr); 

	Utils.assert(x1<maxWidth[y1][y2], "x1=" + x1 + " <= maxWidth[y1][y2]= " + maxWidth[y1][y2] + " with x2=" + x2 + " with y1= " + y1 + " and y2=" + y2 + " in " + cont);
	Utils.assert(x1<=x2, "x1=" + x1 + " > x2= " + x2 + " with y1= " + y1 + " and y2=" + y2 + " in " + cont);
	// if we've exceeded the width of this node, then trim down to actual width
	if (x2>maxWidth[y1][y2]-1) {
	    x2 = maxWidth[y1][y2]-1;
	}

	// if we're down to one node, then descend into it
	if (x1==x2 && y1==y2) {
	    SIRStream child = childConfig(x1, y1).traceback(partitions, curPartition, tileLimit, nextToJoiner, childConfig(x1, y1).getStream());
	    indent--;
	    return child;
	}
	
	// if we only have one tile left, return fusion transform with
	// children fused first
	if (tileLimit==1) {
	    // everything goes in this partition
	    for (int y=y1; y<=y2; y++) {
		for (int x=x1; x<=Math.min(x2,width[y]-1); x++) {
		    IterFactory.createFactory().createIter(childConfig(x,y).getStream()).accept(new RecordingStreamVisitor(curPartition));
		}
	    }
	    if (!DynamicProgPartitioner.transformOnTraceback) {
		return str;
	    } else {
		// fuse everything.
		
		//  This wrapper business is a mess.  Could probably be
		//  simplified -- just moving legacy code out of end of
		//  FuseAll, being sure to preserve functionality.
		SIRPipeline wrapper = SIRContainer.makeWrapper(str);
		wrapper.reclaimChildren();
		SIRPipeline wrapper2 = FuseAll.fuse(str);
		Lifter.eliminatePipe(wrapper2);
		Lifter.lift(wrapper);
		// make sure we've fused
		Utils.assert(wrapper.size()==1 && wrapper.get(0) instanceof SIRFilter, "Wrapper contains " + wrapper.size() + " entries, with get(0)==" + wrapper.get(0));
		// return child
		Lifter.eliminatePipe(wrapper);
		SIRStream result = wrapper.get(0);
		indent--;
		return result;
	    }
	}

	// otherwise, we're going to try making a cut... but first see
	// if there will be any tiles left after accounting for the joiner
	boolean needsJoiner = (width[y2]>1) && (nextToJoiner!=1);
	int tilesAvail = needsJoiner ? tileLimit - 1 : tileLimit;
	// if there is only one tile available, then recurse
	Utils.assert(tilesAvail>0);
	Utils.assert(tileLimit>1);
	if (tilesAvail==1 && y1==y2) {
	    // must have added a joiner if you've gotten to this point
	    SIRStream result = traceback(partitions, curPartition, x1, x2, y1, y2, tilesAvail, 1, str);
	    indent--;
	    return result;
	}

	SIRSplitJoin verticalObj = getVerticalObj(x1, x2, y1, y2, tilesAvail, str);

	if (verticalObj!=null) {
	    // otherwise, see if we made a vertical cut (breaking into
	    // left/right pieces).  As with get procedure, pass
	    // nextToJoiner as true and adjust tileLimit around the call.
	    for (int xPivot=x1; xPivot<x2; xPivot++) {
		for (int tPivot=1; tPivot<tilesAvail; tPivot++) {
		    DPCost cost1 = get(x1, xPivot, y1, y2, tPivot, 1);
		    DPCost cost2 = get(xPivot+1, x2, y1, y2, tilesAvail-tPivot, 1);
		    int cost = Math.max(cost1.getMaxCost(), cost2.getMaxCost());
		    int sum = cost1.getSumCost() + cost2.getSumCost();
		    if (cost==A[x1][x2][y1][y2][tileLimit][nextToJoiner] &&
			sum ==B[x1][x2][y1][y2][tileLimit][nextToJoiner]) {
			// there's a division at this <xPivot>.  We'll
			// return result of a vertical cut
			debugMessage("splitting vertical range " + x1 + "-" + x2 + " into " + x1 + "-" + xPivot + "(" + get(x1, xPivot, y1, y2, tPivot, 0).getMaxCost() + ")" + 
				     " and " + (xPivot+1) + "-" + x2 + "(" + get(xPivot+1, x2, y1, y2, tileLimit-tPivot, nextToJoiner).getMaxCost() + ")");
			int[] arr = { 1 + (xPivot-x1), x2-xPivot };
			PartitionGroup pg = PartitionGroup.createFromArray(arr);
			// do the vertical cut
			SIRSplitJoin sj = RefactorSplitJoin.addHierarchicalChildren((SIRSplitJoin)verticalObj, pg);

			// recurse left and right.
			SIRStream left = traceback(partitions, curPartition, x1, xPivot, y1, y2, tPivot, 1, sj.get(0));
			// mark that we have a partition here
			curPartition = new PartitionRecord();
			partitions.add(curPartition);
			SIRStream right = traceback(partitions, curPartition, xPivot+1, x2, y1, y2, tilesAvail-tPivot, 1, sj.get(1));

			// mutate ourselves if we haven't been mutated already
			sj.set(0, left);
			sj.set(1, right);

			// all done
			indent--;
			return sj;
		    }
		}
	    }
	}

	// otherwise, see if we made a horizontal cut (breaking into
	// top/bottom pieces).  Pass nextToJoiner to bottom child
	// since it will share our joiner if we have one, but 0 to top
	// child since it will need its own.
	for (int yPivot=y1; yPivot<y2; yPivot++) {
	    for (int tPivot=1; tPivot<tileLimit; tPivot++) {
		DPCost cost1 = get(x1, x2, y1, yPivot, tPivot, 0);
		DPCost cost2 = get(x1, x2, yPivot+1, y2, tileLimit-tPivot, nextToJoiner);
		// in this case the cost is the sum since we have a pipeline
		int cost = Math.max(cost1.getMaxCost(), cost2.getMaxCost());
		int sum = cost1.getSumCost() + cost2.getSumCost();
		if (cost==A[x1][x2][y1][y2][tileLimit][nextToJoiner] &&
		    sum ==B[x1][x2][y1][y2][tileLimit][nextToJoiner]) {
		    debugMessage("splitting horizontal range " + y1 + "-" + y2 + " into " + y1 + "-" + yPivot + "(" + get(x1, x2, y1, yPivot, tPivot, 0).getMaxCost() + ")" + 
				 " and " + (yPivot+1) + "-" + y2 + "(" + get(x1, x2, yPivot+1, y2, tileLimit-tPivot, nextToJoiner).getMaxCost() + ")");
		    // there's a division at this <yPivot>.  We'll
		    // return result of a horizontal cut.
		    int[] arr = { 1 + (yPivot-y1), y2-yPivot };
		    PartitionGroup pg = PartitionGroup.createFromArray(arr);

		    SIRContainer result;
		    // might have either pipeline or splitjoin at this point...
		    if (str instanceof SIRSplitJoin) {
			result = RefactorSplitJoin.addSyncPoints((SIRSplitJoin)str, pg);
		    } else if (str instanceof SIRPipeline) {
			result = RefactorPipeline.addHierarchicalChildren((SIRPipeline)str, pg);
		    } else if (str instanceof SIRFeedbackLoop) {
			// if we have a feedbackloop, then factored is
			// just the original, since it will have only
			// two children
			result = (SIRContainer)str;
		    } else {
			result = null;
			Utils.fail("Unrecognized stream type: " + str);
		    }

		    // recurse left and right
		    SIRStream left = traceback(partitions, curPartition, x1, x2, y1, yPivot, tPivot, 0, result.get(0));
		    // mark that we have a partition here
		    curPartition = new PartitionRecord();
		    partitions.add(curPartition);
		    SIRStream right = traceback(partitions, curPartition, x1, x2, yPivot+1, y2, tileLimit-tPivot, nextToJoiner, result.get(1));

		    // mutate ourselves if we haven't been mutated yet
		    result.set(0, left);
		    result.set(1, right);

		    // all done
		    indent--;
		    return result;
		}
	    }
	}
	
	// if we make it this far, then we didn't find our traceback
	Utils.fail("Didn't find traceback for A[" + x1 + "][" + x2 + "][" + y1 + "][" + y2 + "][" + tileLimit + "][" + nextToJoiner + "]" + 
		   " with verticalObj=" + verticalObj + " and A=" + A[x1][x2][y1][y2][tileLimit][nextToJoiner] + " and B=" + B[x1][x2][y1][y2][tileLimit][nextToJoiner]);
	return null;
    }

    /**
     * Return a splitjoin that a vertical cut could be performed on.
     * If it is impossible to perform a vertical cut, returns null.
     */
    private SIRSplitJoin getVerticalObj(int x1, int x2, int y1, int y2, int tilesAvail, SIRStream str) {
	// see if we can do a vertical cut -- first, that there
	// are two streams to cut between
	boolean tryVertical = x1<x2 && sameWidth[y1][y2] && tilesAvail>1;
	
	// get the object we're doing vertical cuts on, and try to
	// remove any synchronization
	SIRSplitJoin verticalObj = null;
	if (tryVertical) {
	    if (str instanceof SIRPipeline) {
		// make a copy of our pipeline, since we're about
		// to split across it.  Don't worry about parent
		// field of children, since they seem to be
		// switched around a lot during partitioning (and
		// restored after)
		SIRPipeline copy = new SIRPipeline(str.getParent(), str.getIdent()+"_copy");
		for (int i=0; i<((SIRPipeline)str).size(); i++) { copy.add(((SIRPipeline)str).get(i)); }
		// now remove synchronization in <copy>.
		RefactorSplitJoin.removeMatchingSyncPoints(copy);
		// now if we only have one splitjoin left as the child, we can do a cut
		if (copy.size()==1) {
		    verticalObj = (SIRSplitJoin)copy.get(0);
		} else {
		    // otherwise can't do a cut
		    tryVertical = false;
		}
	    } else if (str instanceof SIRSplitJoin) {
		verticalObj = (SIRSplitJoin)str;
	    } else {
		Utils.fail("Didn't expect " + str.getClass() + " as object of vertical cut.");
	    }    
	}
	Utils.assert(!(tryVertical && verticalObj==null));
	
	// return obj if it's possible to do vert cut
	if (tryVertical) {
	    return verticalObj;
	} else {
	    return null;
	}
    }

    /**
     * Prints the array of memoized values of this.
     */
    public void printArray() {
	String msg = "Printing array for " + getStream().getIdent() + " --------------------------";
	System.err.println(msg);
	for (int i1=0; i1<A.length; i1++) {
	    for (int i2=0; i2<A[0].length; i2++) {
		for (int i3=0; i3<A[0][0].length; i3++) {
		    for (int i4=0; i4<A[0][0][0].length; i4++) {
			System.err.println();
			for (int i5=0; i5<A[0][0][0][0].length; i5++) {
			    for (int i6=0; i6<2; i6++) {
				System.err.println(getStream().getIdent() + "[" + i1 + "][" + i2 + "][" + i3 + "][" + i4 + "][" + 
						   i5 + "][" + i6 + "] = " + A[i1][i2][i3][i4][i5][i6]);
				/*
				System.err.println("SUM" + getStream().getIdent() + "[" + i1 + "][" + i2 + "][" + i3 + "][" + i4 + "][" + 
						   i5 + "][" + i6 + "] = " + B[i1][i2][i3][i4][i5][i6]);
				*/
			    }
			}
		    }
		}
	    }
	}
	for (int i=0; i<msg.length(); i++) {
	    System.err.print("-");
	}
	System.err.println();
	}

    /**
     * Returns config for child at index <x, y>
     */
    protected abstract DPConfig childConfig(int x, int y);
}

/**
 * Records all filters, splitters, and joiners in a given stream into
 * a partition record.
 */
class RecordingStreamVisitor extends EmptyStreamVisitor {
    private final PartitionRecord curPartition;

    public RecordingStreamVisitor(PartitionRecord _curPartition) {
	this.curPartition = _curPartition;
    }
    
    /**
     * This is called before all visits to a stream structure (Filter,
     * Pipeline, SplitJoin, FeedbackLoop)
     */
    public void preVisitStream(SIRStream self,
			       SIRIterator iter) {
	// add the stream
	if (self instanceof SIRContainer) {
	    // containers
	    if (!curPartition.contains(self)) {
		curPartition.add((SIRContainer)self);
	    }
	} else {
	    // filters
	    if (!curPartition.contains(self)) {
		curPartition.add(self, -1);
	    }
	}
	// also add splitters, joiners
	if (self instanceof SIRSplitJoin) {
	    SIRSplitter splitter = ((SIRSplitJoin)self).getSplitter();
	    if (!curPartition.contains(splitter)) {
		curPartition.add(splitter, -1);
	    }

	    SIRJoiner joiner = ((SIRSplitJoin)self).getJoiner();
	    if (!curPartition.contains(joiner)) {
		curPartition.add(joiner, -1);
	    }
	}
	if (self instanceof SIRFeedbackLoop) {
	    SIRSplitter splitter = ((SIRFeedbackLoop)self).getSplitter();
	    if (!curPartition.contains(splitter)) {
		curPartition.add(splitter, -1);
	    }

	    SIRJoiner joiner = ((SIRFeedbackLoop)self).getJoiner();
	    if (!curPartition.contains(joiner)) {
		curPartition.add(joiner, -1);
	    }
	}
    }
}
