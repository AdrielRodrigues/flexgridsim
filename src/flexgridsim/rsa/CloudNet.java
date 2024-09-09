/**
 * Inter Data-center routing EON-SDM
 */
package flexgridsim.rsa;
import java.util.ArrayList;
import org.w3c.dom.Element;

import flexgridsim.Flow;
import flexgridsim.LightPath;
import flexgridsim.Path;
import flexgridsim.PhysicalTopology;
import flexgridsim.Slot;
import flexgridsim.TrafficGenerator;
import flexgridsim.VirtualTopology;
import flexgridsim.util.KShortestPaths;
import flexgridsim.util.Modulations;
import flexgridsim.util.MultiGraph;
import flexgridsim.util.WeightedGraph;

/**
 * @author AdrielRodrigues
 *
 */

public class CloudNet implements RSA  {
	private PhysicalTopology pt;
	private VirtualTopology vt;
	private ControlPlaneForRSA cp;
	private WeightedGraph graph;

	@Override
	public void flowDeparture(Flow flow) {
		// TODO Auto-generated method stub
	}

	@Override
	public void simulationInterface(Element xml, PhysicalTopology pt,
			VirtualTopology vt, ControlPlaneForRSA cp, TrafficGenerator traffic) {
		this.pt = pt;
		this.vt = vt;
		this.cp = cp;
		this.graph = pt.getWeightedGraph();
	}

	@Override
	public void flowArrival(Flow flow) {
		long id=-1;
		int guardBand=1;
		if(pt.getGrooming())
			guardBand=0;
		
		int demandInSlots;
		Path path;

		int mod=5;
		int modulation=0;
		do{
			// Demand may be different
			demandInSlots = (int) Math.ceil(flow.getRate() / (double) Modulations.getBandwidth(mod)) + guardBand;
			
//			MultiGraph multigraph = new MultiGraph(graph, pt, demandInSlots);
			path = getKShortestPath(graph, flow.getSource(), flow.getDestination(),	demandInSlots,false);
			
//			KShortestPaths kShortestPaths = new KShortestPaths();
//			It returns a vector of nodes
//			int[][] kPaths = kShortestPaths.dijkstraKShortestPaths(graph, flow.getSource(), flow.getDestination(), 5);
//			boolean[][] spectrum = new boolean[pt.getNumCores()][pt.getNumSlots()];
			
//			path = getShortestPath(multigraph, flow.getSource(), flow.getDestination(),demandInSlots);
			if(path!=null)
				modulation=Modulations.getModulationByDistance(getPhysicalDistance(path.getLinks()));
			else
				modulation =-1;
			mod--;
		}while(mod>-1 &&modulation!=-1 && modulation!=mod+1);
		
		// Did it found a single path?
		if (path == null) {
			cp.blockFlow(flow.getID());
			return;
		}

		switch (flow.getConnectionType()) {
		case 0:		// Node to Node
			managementNode(flow, path, modulation, id);
		case 1:		// DC to DC
			managementDC(flow, path, modulation, id);
		case 2:		// Node to DC
			managementDN(flow, path, modulation, id);
		}
	}
	
//	Still working as the default, but intended to treat differentially each kind of connection
	private void managementDC (Flow flow, Path path, int modulation, long id) {
		id = vt.createLightpath(path, modulation);
		ArrayList<LightPath> lightpath = new ArrayList<LightPath>();
			
		if (id >= 0) {
			flow.setLinks(path.getLinks());
			flow.setSlotList(path.getSlotList());
			flow.setModulationLevel(modulation);
			lightpath.add(vt.getLightpath(id));
		}
		if(id<0){
			cp.blockFlow(flow.getID());
			return;
		}
			
		if(!cp.acceptFlow(flow.getID(), lightpath)) {
			vt.removeLightPath(id);
			cp.blockFlow(flow.getID());
			return;
		}	
		return;
	}
	private void managementNode (Flow flow, Path path, int modulation, long id) {
		id = vt.createLightpath(path, modulation);
		ArrayList<LightPath> lightpath = new ArrayList<LightPath>();
			
		if (id >= 0) {
			flow.setLinks(path.getLinks());
			flow.setSlotList(path.getSlotList());
			flow.setModulationLevel(modulation);
			lightpath.add(vt.getLightpath(id));
		}
		if(id<0){
			cp.blockFlow(flow.getID());
			return;
		}
			
		if(!cp.acceptFlow(flow.getID(), lightpath)) {
			vt.removeLightPath(id);
			cp.blockFlow(flow.getID());
			return;
		}	
		return;
	}
	private void managementDN (Flow flow, Path path, int modulation, long id) {
		id = vt.createLightpath(path, modulation);
		ArrayList<LightPath> lightpath = new ArrayList<LightPath>();
			
		if (id >= 0) {
			flow.setLinks(path.getLinks());
			flow.setSlotList(path.getSlotList());
			flow.setModulationLevel(modulation);
			lightpath.add(vt.getLightpath(id));
		}
		if(id<0){
			cp.blockFlow(flow.getID());
			return;
		}
			
		if(!cp.acceptFlow(flow.getID(), lightpath)) {
			vt.removeLightPath(id);
			cp.blockFlow(flow.getID());
			return;
		}	
		return;
	}

	public int getPhysicalDistance(int[] links){
		if(links!=null&& links.length>0){
			int physicalDistance = 0;
			for (int i = 0; i < links.length - 1; i++) {
				physicalDistance += pt.getLink(links[i]).getDistance();
			}
			return physicalDistance;
		}
		else
			return -1;
	}

	public  boolean disjoint(ArrayList<LightPath> lightpaths, int[] linkpath) {
		if(lightpaths!=null&& linkpath!=null && linkpath.length!=0){
			for (LightPath lightpath : lightpaths){
				for(int i = 0; i < lightpath.getLinks().length; i++){  
					for(int j = 0; j < linkpath.length; j++){  
						if( lightpath.getLinks()[i]==linkpath[j])
								return false;	
					}
				}
			}
		}		
		return true;
	}
	/**
	 * Finds, from the list of unvisited vertexes, the one with the lowest
	 * distance from the initial node.
	 * 
	 * @param dist
	 *            vector with shortest known distance from the initial node
	 * @param v
	 *            vector indicating the visited nodes
	 * @return vertex with minimum distance from initial node, or -1 if the
	 *         graph is unconnected or if no vertexes were visited yet
	 */
	public int minVertex(double[] dist, boolean[] v) {
		double x = Double.MAX_VALUE;
		int y = -1; // graph not connected, or no unvisited vertices
		for (int i = 0; i < dist.length; i++) {
			if (!v[i] && dist[i] < x) {
				y = i;
				x = dist[i];
			}
		}
		return y;
	}

	// Dijkstra's algorithm to find shortest path from s to all other nodes
		/**
		 * Msp.
		 *
		 * @param G the g
		 * @param s the s
		 * @param demand the demand
		 * @param protection true if is to protect false otherwise
		 * @return the int[]
		 */
		
	public int[] MSP(WeightedGraph G, int s, int demand) {
		final double[] dist = new double[G.size()]; // shortest known distance
													// from "s"
		final int[] pred = new int[G.size()]; // preceding node in path
		final boolean[] visited = new boolean[G.size()]; // all false initially
		for (int i = 0; i < dist.length; i++) {
			pred[i] = -1;
			dist[i] = 1000000;
		}
	
		dist[s] = 0;
		for (int i = 0; i < dist.length; i++) {
			final int next = minVertex(dist, visited);
			if (next >= 0) {
				visited[next] = true;
	
				// The shortest path to next is dist[next] and via pred[next].
				final int[] n = G.neighbors(next);
				for (int j = 0; j < n.length; j++) {
					final int v = n[j];
					final double d = dist[next] + G.getWeight(next, v);
					if (dist[v] > d) {
						dist[v] = d;
						pred[v] = next;
					}					
				}
			}
		}
		return pred;
	}

	/**
	 * Retrieves the shortest path between a source and a destination node,
	 * within a weighted graph.
	 * 
	 * @param G
	 *            the weighted graph in which the shortest path will be found
	 * @param src
	 *            the source node
	 * @param dst
	 *            the destination node
	 * @param demand
	 *            size of the demand
	 * @return the shortest path, as a vector of integers that represent node
	 *         coordinates
	 */
	public ArrayList<Integer> getShortestPath(WeightedGraph G, int src, int dst, int demand) {
		int x;
		ArrayList<Integer> path = new ArrayList<Integer>();
		final int[] pred = MSP(G, src, demand);
		if (pred == null) {
			return null;
		}
		x = dst;
	
		while (x != src) {
			path.add(0, x);
			x = pred[x];
			// No path
			if (x == -1) {
				return null;
			}				
		}
		path.add(0, src);
		
		return path;
	}

	public Path getShortestPath(MultiGraph multigraph, int src, int dst, int demand) {
		int links[];
		int nowSlot=-1;
		int nowCore=-1;;
		ArrayList<Integer> path = new ArrayList<Integer>();
		ArrayList<Slot> channel = new ArrayList<Slot>();
		for (int j = 0; j < multigraph.getNumMultiedges();j++) {	//Num cores
			for (int i = 0; i < multigraph.getNumEdges(); i++) {	//Num slots left
				ArrayList<Integer> nowpath = new ArrayList<Integer>();
				nowpath=getShortestPath(multigraph.getGraph(i,j),src,dst, demand);
				if(nowpath==null||nowpath.size()<2){
					continue;
				}else{
					if(nowpath.size()<path.size()||path.isEmpty()){
						path=nowpath;
						nowSlot=i;
						nowCore =j;
					}
				}
				
			}
		}
		if(path.size()<2){
			return null;
		}
		links = new int[path.size() - 1];

		for (int j = 0; j < path.size() - 1; j++) {
			links[j] = pt.getLink(path.get(j), path.get(j + 1)).getID();
		}
		for (int l = 0; l < links.length; l++) {
			for (int j = nowSlot; j < nowSlot+demand; j++) {
				channel.add(new Slot(nowCore, j, links[l] ));
			}
		}
		return new Path(links, channel);
	}
	
	public Path getKShortestPath(WeightedGraph G,int src, int dst, int demand, boolean overlap){
		KShortestPaths kShortestPaths = new KShortestPaths();
		int[][] kPaths = kShortestPaths.dijkstraKShortestPaths(G, src, dst, 3);
		if(kPaths==null)
			return null;
		int []links;
		ArrayList<Slot> channel = new ArrayList<Slot>();
		
		for (int i = 0; i < kPaths.length; i++) {
			if (kPaths[i].length > 1){
				links = new int[kPaths[i].length - 1];
				for (int j = 0; j < kPaths[i].length - 1; j++) {
					links[j] = pt.getLink(kPaths[i][j], kPaths[i][j + 1]).getID();
				}
				channel=getSimilarSlotsInLinks(links,overlap, demand);
				if(channel!=null){
					return new Path(links, channel);
				}	
			} else {
				continue;
			}
		}
		return null;
	}
	public  ArrayList<Slot> getSimilarSlotsInLinks(int []links, boolean sharing, int demandInSlots) {
		ArrayList<Slot> channel = new ArrayList<Slot>();
		int firstSlot;
		int lastSlot;
		int core;
		for (int i = 0; i < pt.getNumSlots()-demandInSlots; i++) {
			firstSlot = i;
			lastSlot = i + demandInSlots - 1;
			core=usingSameCore(firstSlot, lastSlot, links, sharing);
		
			if(core!=-1){
				for (int j = firstSlot; j <= lastSlot; j++) {
					for (int l = 0; l < links.length; l++) {
						channel.add(new Slot(core, j, links[l] ));
					}
				}
				return channel;	
			}//else{@todo}
	
		}	
		return null;
	}
	public int usingSameCore(int firstSlot, int lastSlot, int links[], boolean sharing){
		for (int core=0; core < pt.getNumCores(); core ++){
			if(freeSlotInAllLinks(links, firstSlot, lastSlot, core, sharing)){
				return core;
			}
		}
		return -1;
	}

	public boolean freeSlotInAllLinks(int links[], int firstSlot, int lastSlot, int core, boolean sharing) {
		for (int j = 0; j < links.length; j++) {
			if (sharing==false){
				for (int h = firstSlot; h <= lastSlot; h++) {
					if(!pt.getLink(links[j]).getSpectrum(core, h)){
						return false;
					}
				}
			}
			if (sharing==true){
				for (int h = firstSlot; h <= lastSlot; h++) {
					if(!pt.getLink(links[j]).getSpectrumS(core, h)){
						return false;
					}
				}
			}
		}
		return true;
	}
	
}
