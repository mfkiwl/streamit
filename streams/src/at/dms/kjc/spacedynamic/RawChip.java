package at.dms.kjc.spacedynamic;

import at.dms.util.Utils;
import at.dms.kjc.*;

/** This class represents the RawChip we are compiling to **/

public class RawChip {
    //the indices are x, y
    private RawTile[][] tiles;
    private int gXSize;
    private int gYSize;
    public static final int cacheLineBytes = 32;
    public static final int cacheLineWords = 8;
    public static final int dCacheSizeBytes = 32768;
    private IOPort[] ports;

    public RawChip(int xSize, int ySize) {
	gXSize = xSize;
	gYSize = ySize;

	tiles = new RawTile[xSize][ySize];
	for (int x = 0; x < xSize; x++)
	    for (int y = 0; y < ySize; y++)
		tiles[x][y] = new RawTile(x, y, this);
	
	ports = new IOPort[2*gXSize + 2*gYSize];
	addIOPorts();
    }

    public int getNumPorts() 
    {
	return 2*gXSize + 2*gYSize;
    }

    public IOPort getIOPort(int i) 
    {
	return ports[i];
    }

    public void connectDevice(IODevice dev, IOPort port) 
    {
	port.addDevice(dev);
	dev.connect(port);
    }
    
    
    private void addIOPorts() 
    {
	int i, index = 0;
	//add the north ioports
	for (i = 0; i < this.gXSize; i++) {
	    ports [index] = new IOPort(this, index);						
	    index ++;
	}
	//add the east ioports
	for (i = 0; i < this.gYSize; i++) {
	    ports [index] = new IOPort(this, index);
	    index ++;
	}	
	//add the south ioports
	for (i = this.gXSize - 1; i >= 0; i--) {
	    ports [index] = new IOPort(this, index);
	    index ++;
	}
	//add the west ioports
	for (i = this.gYSize - 1; i >= 0; i--) {
	    ports [index] = new IOPort(this, index);
	    index ++;
	}	
    }

    public RawTile getTile(int tileNumber) 
    {
	int y = tileNumber / gXSize;
	int x = tileNumber % gXSize;
	if (y >= gYSize) 
	    Utils.fail("out of bounds in getTile() of RawChip");
	return tiles[x][y];
    }
    
    public ComputeNode getComputeNode(int x, int y) 
    {
	assert !(x >= gXSize || y >= gYSize || x < 0 || y < 0) :
	    "out of bounds in getComputeNode() of RawChip";

	return tiles[x][y];
    }
    
    
    public RawTile getTile(int x, int y) {
	assert  !(x >= gXSize || y >= gYSize) : "out of bounds in getTile() of RawChip";
	return tiles[x][y];
    }

    public int getTotalTiles() 
    {
	return gXSize * gYSize;
    }

    public int getXSize() {
	return gXSize;
    }

    public int getYSize() {
	return gYSize;
    }

    public boolean areNeighbors(ComputeNode tile1, ComputeNode tile2) 
    {
	if (tile1 == tile2) 
	    return false;
	if (tile1.getY() == tile2.getY())
	    if (Math.abs(tile1.getX() - tile2.getX()) == 1)
		return true;
	if (tile1.getX() == tile2.getX())
	    if (Math.abs(tile1.getY() - tile2.getY()) == 1)
		return true;
	//not conntected
	return false;
    }
    
    /** get the direction in X from src to dst, -1 for west
	0 for same X, and 1 for east **/
    public int getXDir(ComputeNode src, ComputeNode dst) 
    {
	if (dst.getX() - src.getX() < 0)
	    return -1;
	if (dst.getX() - src.getX() > 0)
	    return 1;
	return 0;
    }

    /** get the direction in Y from src to dst, -1 for North
	0 for same and 1 for south. 
    **/
    public int getYDir(ComputeNode src, ComputeNode dst)  
    {
	if (dst.getY() - src.getY() < 0)
	    return -1;
	if (dst.getY() - src.getY() > 0)
	    return 1;
	return 0;
    }
    
	

    //returns "E", "N", "S", "W", or "st" if src == dst
    public String getDirection(ComputeNode from, ComputeNode to) {
	if (from == to)
	    return "st";

	
	if (from.getX() == to.getX()) {
	    int dir = from.getY() - to.getY();
	    if (dir == -1)
		return "S";
	    else if (dir == 1)
		return "N";
	    else
		Utils.fail("calling getDirection on non-neighbors");
	}
	if (from.getY() == to.getY()) {
	    int dir = from.getX() - to.getX();
	    if (dir == -1) 
		return "E";
	    else if (dir == 1)
		return "W";
	    else
		Utils.fail("calling getDirection on non-neighbors");
	}
	//System.out.println(from);
	//System.out.println("[" + from.getX() + ", " + from.getY() + "] -> [" +
	//to.getX() + ", " + to.getY() + "]");
	
	Utils.fail("calling getDirection on non-neighbors");
	return "";
    }
}