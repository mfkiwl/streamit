/**
 * 
 */
package at.dms.kjc.spacetime;

import at.dms.kjc.common.SimulatedAnnealing;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author mgordon
 *
 */
public class AnnealedLayout extends SimulatedAnnealing implements Layout {
    /** if a tile does at least <BIG_WORKER> percentage of the bottle neck tile,
     * communication to and from it are included in the communication cost component
     * of the cost function.
     */
    private static final double BIG_WORKER = 0.9;
    
    private static final int ILLEGAL_COST = 1000000;
    /** the cost of outputing one item using the gdn versus using the static net
     for the final filter of a Trace */
    private static final int GDN_PUSH_COST = 2;
    /** the cost of issue a read or a write dram command on the tiles assigned 
     * to trace enpoints for each trace.
     */
    private static final int DRAM_ISSUE_COST = 5;
    
    private RawChip rawChip;
    private SpaceTimeSchedule spaceTime;
    private LinkedList filterList;
    private Partitioner partitioner;
    /** the number of tiles in the raw chip */
    private int numTiles;
    /** the raw tiles, so we don't have to call rawChip */
    private RawTile tiles[];
    /** used to get a random sequence of directions to try from 
     * a tile when calc'ing a new random layout for a trace
     */
    private static String[] dirStrings;
    /** the tile that has the max work at the current step */ 
    private int maxWorkTile;
    
    private HashSet fileWriters;
    private HashSet fileReaders;
    /** The order the traces will be scheduled, so sorted by bottleneck work */
    private Trace[] scheduleOrder;
    private BufferDRAMAssignment assignBuffers;
    
    public AnnealedLayout(SpaceTimeSchedule spaceTime) {
        super();
        this.spaceTime = spaceTime;
        rawChip = spaceTime.getRawChip();
        this.partitioner = spaceTime.partitioner;
        numTiles = rawChip.getTotalTiles();
        tiles = new RawTile[numTiles];
        for (int i = 0; i < numTiles; i++) 
            tiles[i] = rawChip.getTile(i);
        //get the schedule order of the graph!
        scheduleOrder = (Trace[]) spaceTime.partitioner.getTraceGraph().clone();
        Arrays.sort(scheduleOrder, new CompareTraceBNWork(spaceTime.partitioner));
        //init the buffer to dram assignment pass
        assignBuffers = new BufferDRAMAssignment();
    }

    public void run() {
        //run the simulated annealing with 2 iterations, 
        //100 attempts per iteration
        simAnnealAssign(2, 100);
        printLayoutStats();
        for (int i = 0; i < filterList.size(); i++) 
            System.out.println(filterList.get(i) + " is assigned to " + 
                    assignment.get(filterList.get(i)));
    }
    
    public RawTile getTile(FilterTraceNode filter) {
        assert assignment.containsKey(filter) : 
            "AnnealedLayout does have a mapping for " + filter;
        return (RawTile)assignment.get(filter);
    }
        
    public void setAssignment(HashMap newAssignment) {
        assignment = newAssignment;
//      reassign buffers.
        assignBuffers.run(spaceTime, this);
    }
    
    public void printLayoutStats() {
        int[] tileCosts = getTileWorks();
        
        System.out.println("Placement cost: " + placementCost(false));
        
        System.out.println("Max Work Tile of layout is " + maxWorkTile + 
                ", it has work: " + maxTileWork(tileCosts));
        
       
        for (int i = 0; i < tileCosts.length; i++) 
            System.out.println("Tile " + i + " has work " + tileCosts[i]);
        
        System.out.println("Std Dev of work: " + standardDeviation(tileCosts));
        
        assert isLegalLayout() : 
            "Simulated annealing arrived at an illegal layout.";
    }
    
    /**
     * This function will perturb the configuration by moving
     * something around
     */
    public void swapAssignment() {
        //swapAssignmentIllegal();
        //swap filters...
        swapAssignmentLegal();
        //reassign buffers.
        assignBuffers.run(spaceTime, this);
    }
    
    /** 
     * perturb the configuration by finding a new layout for a single trace
     *
     */
    public void swapAssignmentLegal() {
        Trace[] traces = partitioner.getTraceGraph();
        Trace reassignMe = traces[getRandom(traces.length)];
        
        assert newTraceAssignment(reassignMe.getHead().getNextFilter()) :
            "Error: could not find a legal layout for " + reassignMe + " during layout";
    }
   
    
    /**
     *  
     */
    public void initialPlacement() {
       //try a random layout! nope
        //randomInitialPlacement();
        legalInitialPlacement();
        //assign buffers...
        assignBuffers.run(spaceTime, this);
    }
    
    public void setTile(FilterTraceNode node, RawTile tile) {
        assignment.put(node, tile);
    }
    
    /**
     * 
     * @param newAssign
     * @param node  call with first filterTraceNode of trace
     * @return true if we successfully laid out the trace, 
     * it should alway return true when finished recursing. 
     */
    private boolean newTraceAssignment(TraceNode node) {
        //we are finished
        if (node.isOutputTrace()) {
	    //check the assignment of the previous filter 
	    //if it is a file writer, to make sure the tile
	    //is legal
  
	    //if this filter write directly to a file, then we have
	    //to make sure that no other file writer filters are mapped to 
	    //<tile>
	    if (fileWriters.contains(node.getPrevious()) &&
		!legalFileWriterTile
		(node.getPrevious().getAsFilter(), 
		 (RawTile)assignment.get(node.getPrevious())))
		return false;;       
            return true;
	}
	

        //if we get here we are a filterTraceNode 
        FilterTraceNode filter = node.getAsFilter();
        
        if (node.getPrevious().isInputTrace()) {
            //Choose a random tile for the initial placement
            RawTile tile = null;
	    //set of tiles we have tried, no dups...
	    HashSet tilesTried = new HashSet();
	    //keep picking random tiles until we have tried all of them
            while (tilesTried.size() < numTiles) {
                tile = tiles[getRandom(numTiles)];
		tilesTried.add(tile);
                //if this is a file reader, we have to make sure that 
                //no other file readers are mapped to this tile... 
                if (fileReaders.contains(filter) && 
                        !legalFileReaderTile(filter, tile))
                    continue;
		
		setTile(filter, tile);
		if (newTraceAssignment(node.getNext()))
		    return true;
	    }
	    //if we get here, we have failed to find a layout.
	    return false;
        }
        else {
            //we are not the first tile so choose some
            RawTile prevTile = (RawTile)assignment.get(node.getPrevious());
            int dirVec = getRandom(24);
            for (int i = 0; i < 4; i++) {
                //get the next direction to try...
                RawTile tile = rawChip.getTile(prevTile, dirStrings[dirVec].charAt(i));
                //if null, then there was no tile in this direction, try another direction
                if (tile == null)
                    continue;
                
                //see if any of the previous filters of the trace were mapped to this
                //tile if so continue
                boolean prevMap = false;
                TraceNode prevNode = filter.getPrevious();
                while (assignment.containsKey(prevNode)) {
                    if (tile == assignment.get(prevNode)){
                        prevMap = true;
                        break;
                    }
                    prevNode = prevNode.getPrevious();
                }

                //try another tile
                if (prevMap)
                    continue;
                                       
                //assign this filter to the tile in this direction
                setTile(filter, tile);
                //try the assignment to the tile in this direction
                if (newTraceAssignment(node.getNext()))
                    return true; //if it worked return!
                else
                    continue; //if not, try another direction if there are more
            }
            //if we get here, we have failed
            return false;
        }
    }
 
    /**
     * We want to try to place <filter> on <tile> but it is a file reader,
     * so we have to make sure that no other file reading filters are 
     * mapped to this tile.  If so return false.
     * 
     * @param filter
     * @param tile
     * @return
     */
    private boolean legalFileReaderTile(FilterTraceNode filter,
            RawTile tile) {
	Iterator frs = fileReaders.iterator();
        while (frs.hasNext()) {
            FilterTraceNode current = (FilterTraceNode)frs.next();
            if (current == filter) 
                continue;
            RawTile occupied = (RawTile)assignment.get(current);
            //see if we have a mapping for this file reader filter yet
            if (occupied == null)
                continue;
            //if so, see if it mapped to the tile we want, if so return false
            if (occupied == tile)
                return false;
        }
        //no file readers mapped to tile, return true
        return true;
    }
    
    /**
     * We want to try to place <filter> on <tile> but <filter> writes 
     * to a file, so we have to make sure that no other filters that writes 
     * to a file is mapped to this tile.  If there is one, return false. 
     * @param filter
     * @param tile
     * @return
     */
    private boolean legalFileWriterTile(FilterTraceNode filter, 
            RawTile tile) {
	Iterator fws = fileWriters.iterator();
	while (fws.hasNext()) {
	    FilterTraceNode current = (FilterTraceNode)fws.next();
	    if (current == filter)
		continue;
	    RawTile occupied = (RawTile)assignment.get(current);
	    //see if we have a mapping for this file reader filter yet
	    if (occupied == null)
		continue;
	    //if so, see if it mapped to the tile we want, if so return false
	    if (occupied == tile) {
                //System.out.print(tile +  " has two file writers!");
		return false;
            }
	}
	//no file writer mapped to tile, return true
	return true;
    }
    
    /**
     * For each trace, generate a random legal tile assignment 
     * as the initial layout.
     */
    private void legalInitialPlacement() {
        Trace[] traces = partitioner.getTraceGraph();
        for (int i = 0; i < traces.length; i++) {
            assert newTraceAssignment(traces[i].getHead().getNextFilter()) :
                "Error: could not find a legal layout for " + traces[i] + " during initial layout";
        }
    }
    
    /** 
     * @return The maximum work for a tile (accounting for 
     * pipeline bubbles) plus the cost of intertrace communication.
     *  
     */
    public double placementCost(boolean debug) {
   
        int[] tileCosts = getTileWorks();
        
        double workOfBottleNeckTile = (double)maxTileWork(tileCosts);
        
        double cost = workOfBottleNeckTile //+ standardDeviation(tileCosts)  
                   + (double)bigWorkersCommEstimate(tileCosts);
        
        /*
        if (!isLegalLayout())
            cost += ILLEGAL_COST;
        */
        return cost;
    }

    /** check if the layout is legal, meaning that communicating filters of a
     * trace are placed on neighboring tiles.
     * 
     * @return
     */
    private boolean isLegalLayout() {
        Iterator filters = filterList.iterator();
        
        while (filters.hasNext()) {
            FilterTraceNode filter = (FilterTraceNode)filters.next();
            //last filter of a trace, no need to check it          
            if (filter.getNext().isOutputTrace())
                continue;
            
            RawTile myTile = (RawTile)assignment.get(filter);
            RawTile upTile = 
                (RawTile)assignment.get(filter.getNext().getAsFilter());
            if (!(rawChip.areNeighbors(myTile, upTile)))
                return false;
        }
        
        return true;
    }
    
    /** 
     * @param bigWorkers
     * @param edge
     * @return If the edge is between bigworkers, then estimate the initial latency
     * of the intertracebuffer by calculating the manhattan distance of hte
     * edge.
     */
    private int bigWorkerDistanceLatency(HashSet bigWorkers, Edge edge) {
        
        //get the port that source is writing to
        RawTile srcTile = (RawTile)assignment.get(edge.getSrc().getPrevFilter());
        RawTile dstTile = (RawTile)assignment.get(edge.getDest().getNextFilter());
        //we only care about the tiles that do a bunch of work!
        if (bigWorkers.contains(srcTile) || bigWorkers.contains(dstTile)) {
            //System.out.println("Accouting for comm over big worker.");
            IODevice srcPort = 
                LogicalDramTileMapping.getHomeDram
                (srcTile);
            //get the por that the dest is reading from
            IODevice dstPort = 
                LogicalDramTileMapping.getHomeDram
                (dstTile);
            
            //now get the neighboring tiles of the dram ram's and find the
            //distance between them, because we send everthing over the 
            //static network...
            RawTile srcNeighbor = srcPort.getNeighboringTile();
            RawTile dstNeighbor = dstPort.getNeighboringTile();
            
            return (rawChip.manhattanDistance(srcNeighbor, dstNeighbor) + 1);
         
        }
        
        return 0;
    }    
    
    /**
     * @return An estimate of the cost of communication for this layout.
     */
    private int bigWorkersCommEstimate(int tileCosts[]) {
        int estimate = 0;
        Trace[] traces = partitioner.getTraceGraph();
        HashSet bigWorkers = new HashSet();
        
        //create a hashset of tiles that do a bunch of work...
        for (int i = 0; i < tileCosts.length; i++) {
            if ((double)tileCosts[i] / (double)tileCosts[maxWorkTile] >= 
                BIG_WORKER) {
                bigWorkers.add(rawChip.getTile(i));
            }
        }
        
        //buffer edges are assigned drams by the buffer dram assignment,
        //so we can get a fairly accurate picture of the communication
        //of the graph...
        for (int i = 0; i < traces.length; i++) {
            Trace trace = traces[i];
            Iterator edges = trace.getTail().getDestSet().iterator();
            while (edges.hasNext()) {
                Edge edge = (Edge)edges.next();
                InterTraceBuffer buf = InterTraceBuffer.getBuffer(edge);
                OutputTraceNode output = edge.getSrc();
                InputTraceNode input = edge.getDest();
                //account for the distance of the connected to/from a
                //big worker
                estimate += bigWorkerDistanceLatency(bigWorkers, edge);
                
//              if the off chip buffer does something then count its 
                //communicate if it goes through a bigWorker
                if (!OffChipBuffer.unnecessary(output)) {
                    StreamingDram srcDRAM = 
                        IntraTraceBuffer.getBuffer(output.getPrevFilter(), 
                                output).getDRAM();
                    StreamingDram dstDRAM = 
                        buf.getDRAM();
                    estimate += itemsThroughBigWorkers(bigWorkers, edge,
                            srcDRAM, dstDRAM);
                    
                }
//              if the off chip buffer does something then count its 
                //communicate if it goes through a bigWorker
                if (!OffChipBuffer.unnecessary(input)) {
                    StreamingDram srcDRAM = buf.getDRAM();
                    StreamingDram dstDRAM = 
                        IntraTraceBuffer.getBuffer(input, input.getNextFilter()).getDRAM();
                    estimate += itemsThroughBigWorkers(bigWorkers, edge,
                            srcDRAM, dstDRAM);
                    
                }
            }
        }
        return estimate;
    }
    
    /**
     * @param bigWorkers
     * @param edge
     * @param src
     * @param dst
     * @return The number of items that travel over inter-trace-buffer
     * edge with src dram and dst dram 
     * and the pass through a tile in the <bigWorkers> tile set,
     * but you don't care about this if the tile must use the gdn for 
     * input and output intra trace.
     */
    private int itemsThroughBigWorkers(HashSet bigWorker, Edge edge, 
            StreamingDram src, StreamingDram dst) {
        int items = 0;
        Iterator route = Router.getRoute(src, dst).iterator();
        HashSet accountedFor = new HashSet();
        while (route.hasNext()) {
            ComputeNode hop = (ComputeNode)route.next();
            if (hop instanceof RawTile && 
                    bigWorker.contains(hop) &&  
                    !LogicalDramTileMapping.mustUseGdn((RawTile)hop)) {
                items +=  edge.steadyItems();
                accountedFor.add(hop);
            }
        }
        //now we if the src or dst owner tiles is a big worker and 
        //it is gdn, we should account for the total time of the edge also
        //because the dram command will have to wait until it is completed
        RawTile srcIssuer = LogicalDramTileMapping.getOwnerTile(src);
        if (bigWorker.contains(srcIssuer) &&
                LogicalDramTileMapping.mustUseGdn(srcIssuer)
                && !accountedFor.contains(srcIssuer))
            items += edge.steadyItems();
        
        RawTile dstIssuer = LogicalDramTileMapping.getOwnerTile(dst);
        if (bigWorker.contains(dstIssuer) &&
                LogicalDramTileMapping.mustUseGdn(dstIssuer) &&
                !accountedFor.contains(dstIssuer))
            items += edge.steadyItems();
        
        
        return items;
    }
    
    /**
     * @return The maximum amount of work that is performed on a tile
     * using a formula that accounts for startup costs of filter (pipeline lag).
     * Also, if the filter outputs using the gdn account 
     * for the added cost of sending an item.
     */
    private int maxTileWork(int[] tileCosts) {
        
        //find the max
        int max = 0;
        
        for (int i = 0; i < tileCosts.length; i++) {
            if (tileCosts[i] > max) {
                max = tileCosts[i];
                maxWorkTile = i;
            }
        }
        //return the work that the max tile is estimated to do
        return max;
    }
        
    private int[] getTileWorks() {
        int[] tileCosts = new int[rawChip.getTotalTiles()];
        
        for (int i = 0; i < scheduleOrder.length; i++) {
            FilterTraceNode node = scheduleOrder[i].getFilterNodes()[0];
            RawTile tile = (RawTile)assignment.get(node);
            //the cost of the previous node, not used for first filter 
            int prevStart = tileCosts[tile.getTileNumber()];
                    
            //the first filter does not have to account for startup lag
            tileCosts[tile.getTileNumber()] += 
                partitioner.getFilterWorkSteadyMult(node);

            //now cycle thru the rest of the nodes...
            for (int f = 1; f < scheduleOrder[i].getFilterNodes().length; f++) {
                node = scheduleOrder[i].getFilterNodes()[f];
                //get this node's assignment;
                tile = (RawTile)assignment.get(node);
                //accounting for pipeling lag, when is the earliest I can start?
                
                //offset is the offset between the last filter's start time
                //and this tile's current work load, if it is positive,
                //this tile is free after the last time, so we should,
                //account for this time in the pipeline lag
                //if negative, this tile is free before the last tile was free
                //so it does not help, so set to zero
                int offSet = tileCosts[tile.getTileNumber()] - 
                   prevStart;
                
                offSet = Math.max(0, offSet);
                
                //now when is the earliest I can start in relation to the last tile
                //and the work of this tile
                int pipeLag = 
                    Math.max((partitioner.getFilterStartupCost(node) - offSet),
                            0);
                
                //set the prev start to when I started, for the next iteration
                prevStart = tileCosts[tile.getTileNumber()];
                //the new work for this tile, is my work + my startup cost...
                tileCosts[tile.getTileNumber()] += (pipeLag + 
                    partitioner.getFilterWorkSteadyMult(node));

               
            }
            //account for the cost of issuing its load dram commands 
            RawTile inputTile = 
                (RawTile)assignment.get(scheduleOrder[i].getHead().getNextFilter());
            tileCosts[inputTile.getTileNumber()]+= DRAM_ISSUE_COST;
                        
            //account for the
            //cost of sending an item over the gdn if it uses it...
            RawTile outputTile = 
                (RawTile)assignment.get(scheduleOrder[i].getTail().getPrevFilter());
            if (LogicalDramTileMapping.mustUseGdn(outputTile)) {
                tileCosts[outputTile.getTileNumber()] += (node.getFilter().getPushInt() * 
                        node.getFilter().getSteadyMult() * 
                        GDN_PUSH_COST);
            }
            
            //account for the cost of issuing its store dram command
            tileCosts[outputTile.getTileNumber()]+= DRAM_ISSUE_COST;
        }
         
        return tileCosts;
    }
    
    /**    
     * initalize the simulated annealing.
     */
    public void initialize() {
        //generate the startup cost of each filter...         
        partitioner.calculateStartupCosts();
        
        //create the filter list
        filterList = new LinkedList();
        Iterator traceNodes = 
            Util.traceNodeTraversal(partitioner.getTraceGraph());
        while (traceNodes.hasNext()) {
            TraceNode node = (TraceNode)traceNodes.next();
            //add filters to the list of things to assign to tiles,
            //but don't add file readers/writers... they will
            //"occupy" the tile of their neighbor stream...
            if (node.isFilterTrace() && 
                    !(node.getAsFilter().isFileInput() ||
                            node.getAsFilter().isFileOutput()))  {
                filterList.add(node);
            }
        }
        
        fileReaders = new HashSet();
        fileWriters = new HashSet();
        
        //set up the file readers and file writers list
        //these are filters the read directly from a file
        //or write directly to a file (not the file readers and writers themselves).
        for (int i = 0; i < partitioner.io.length; i++) {
            Trace trace = partitioner.io[i];
            if (trace.getHead().isFileOutput()) {
                assert trace.getHead().getSourceSet().size() == 1 :
                    "We don't support joined file writers yet.";
                fileWriters.add(trace.getHead().getSingleEdge().getSrc().getPrevFilter());
            }
            else if (trace.getTail().isFileInput()) {
                assert trace.getTail().getDestSet().size() == 1 :
                    "We don't support split file readers right now.";
                fileReaders.add(trace.getTail().getSingleEdge().getDest().getNextFilter());
            }
            else 
                assert false : "Trace in io[] is not a file trace.";
         }

    }

    
    
    /**
     * @param vals
     * @return The standard deviation of the values in <vals>.
     */
    public static double standardDeviation(int[] vals) {
        double squaredSum = 0.0;
        double sum = 0.0;
        
        for (int i = 0; i < vals.length; i++) { 
            squaredSum += ((double)vals[i] * (double)vals[i]);
            sum += (double)vals[i];
        }
        
        double mean  = sum / (double) vals.length;
        
        
        double stdDev = Math.sqrt((squaredSum / (double)vals.length) - (mean * mean));
        assert stdDev >= 0.0 : squaredSum + " " + mean + " " + vals.length + " " + 
        stdDev;
        return stdDev;
    }
    
    static {
        dirStrings = new String[24];
        dirStrings[0] = "NESW";
        dirStrings[1] = "NEWS";
        dirStrings[2] = "NSEW";
        dirStrings[3] = "NSWE";
        dirStrings[4] = "NWSE";
        dirStrings[5] = "NWES";
        dirStrings[6] = "ENSW";
        dirStrings[7] = "ENWS";
        dirStrings[8] = "ESNW";
        dirStrings[9] = "ESWN";
        dirStrings[10] = "EWNS";
        dirStrings[11] = "EWSN";
        dirStrings[12] = "SNEW";
        dirStrings[13] = "SNWE";
        dirStrings[14] = "SENW";
        dirStrings[15] = "SEWN";
        dirStrings[16] = "SWEN";
        dirStrings[17] = "SWNE";
        dirStrings[18] = "WNES";
        dirStrings[19] = "WNSE";
        dirStrings[20] = "WENS";
        dirStrings[21] = "WESN";
        dirStrings[22] = "WSEN";
        dirStrings[23] = "WSNE";
    }
}