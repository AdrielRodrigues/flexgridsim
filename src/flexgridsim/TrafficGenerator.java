/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package flexgridsim;

import java.util.ArrayList;
import java.util.Random;

import org.w3c.dom.*;

import flexgridsim.util.Distribution;
import flexgridsim.util.WeightedGraph;

/**
 * Generates the network's traffic based on the information passed through the
 * command line arguments and the XML simulation file.
 * 
 * @author andred
 */
public class TrafficGenerator {

    private int calls;
    private double load;
    private int maxRate;
    private TrafficInfo[] callsTypesInfo;
	private double meanRate;
    private double meanHoldingTime;
    private int TotalWeight;
    private int numberCallsTypes;
    private Element xml;
    private int[] minRate;
    private double[] minSize;
    private double[] maxSize;
    private NodeList fileSizes;
    
    private int counter_regular = 0;
    private int counter_dc = 0;
    private int counter_inter = 0;

    /**
     * Creates a new TrafficGenerator object.
     * Extracts the traffic information from the XML file and takes the chosen load and
     * seed from the command line arguments.
     * 
     * @param xml file that contains all information about the simulation
     * @param forcedLoad range of offered loads for several simulations
     */
    public TrafficGenerator(Element xml, double forcedLoad) {
        int rate, cos, weight;
        double holdingTime;

        this.xml = xml;
        calls = Integer.parseInt(xml.getAttribute("calls"));
        load = forcedLoad;
        if (load == 0) {
            load = Double.parseDouble(xml.getAttribute("load"));
        }
        maxRate = Integer.parseInt(xml.getAttribute("max-rate"));

        if (Simulator.verbose) {
            System.out.println(xml.getAttribute("calls") + " calls, " + xml.getAttribute("load") + " erlangs.");
        }

        // Process calls
        NodeList callslist = xml.getElementsByTagName("calls");
        numberCallsTypes = callslist.getLength();
        if (Simulator.verbose) {
            System.out.println(Integer.toString(numberCallsTypes) + " type(s) of calls:");
        }

        callsTypesInfo = new TrafficInfo[numberCallsTypes];

        TotalWeight = 0;
        meanRate = 0;
        meanHoldingTime = 0;

        for (int i = 0; i < numberCallsTypes; i++) {
            TotalWeight += Integer.parseInt(((Element) callslist.item(i)).getAttribute("weight"));
        }

        for (int i = 0; i < numberCallsTypes; i++) {
            holdingTime = Double.parseDouble(((Element) callslist.item(i)).getAttribute("holding-time"));
            rate = Integer.parseInt(((Element) callslist.item(i)).getAttribute("rate"));
            cos = Integer.parseInt(((Element) callslist.item(i)).getAttribute("cos"));
            weight = Integer.parseInt(((Element) callslist.item(i)).getAttribute("weight"));
            meanRate += (double) rate * ((double) weight / (double) TotalWeight);
            meanHoldingTime += holdingTime * ((double) weight / (double) TotalWeight);
            callsTypesInfo[i] = new TrafficInfo(holdingTime, rate, cos, weight);
            if (Simulator.verbose) {
                System.out.println("#################################");
                System.out.println("Weight: " + Integer.toString(weight) + ".");
                System.out.println("COS: " + Integer.toString(cos) + ".");
                System.out.println("Rate: " + Integer.toString(rate) + "Mbps.");
                System.out.println("Mean holding time: " + Double.toString(holdingTime) + " seconds.");
            }
        }
    }

    /**
     * Generates the network's traffic.
     *
     * @param events EventScheduler object that will contain the simulation events
     * @param pt the network's Physical Topology
     * @param seed a number in the interval [1,25] that defines up to 25 different random simulations
     */

	public void generateTraffic(PhysicalTopology pt, EventScheduler events, int seed) {
		
		boolean isDataCenterNetwork = true;

        // Compute the weight vector
        int[] weightVector = new int[TotalWeight];
        int aux = 0;
        for (int i = 0; i < numberCallsTypes; i++) {
            for (int j = 0; j < callsTypesInfo[i].getWeight(); j++) {
                weightVector[aux] = i;
                aux++;
            }
        }

        /* Compute the arrival time
         *
         * load = meanArrivalRate x holdingTime x bw/maxRate
         * 1/meanArrivalRate = (holdingTime x bw/maxRate)/load
         * meanArrivalTime = (holdingTime x bw/maxRate)/load
         */
        double meanArrivalTime = (meanHoldingTime * (meanRate / (double) maxRate)) / load;

        //Generate events
        int type, src = 0, dst = 0;
        double time = 0.0;
        int id = 0;
        int numNodes = pt.getNumNodes();
        Distribution dist1, dist2, dist3, dist4;
        dist1 = new Distribution(1, seed);
        dist2 = new Distribution(2, seed);
        dist3 = new Distribution(3, seed);
        dist4 = new Distribution(4, seed);

        int numDataCenters = 2;
        int[] dataCenterNodes = positioningDC(pt.getWeightedGraph(), numDataCenters);
//        System.out.println("DATACENTERS = " + dataCenterNodes[0] + " " + dataCenterNodes[1]);
        
        for (int j = 0; j < calls; j++) {

        	int connectionType = 0;
        	int nRequest = 0;
        	
        	type = weightVector[dist1.nextInt(TotalWeight)];
            
        	double probability = dist2.nextDouble();

        	if (probability <= 0.1) {
        		// DC to DC
        		src = dst = dataCenterNodes[dist2.nextInt(numDataCenters)];
        		while (src == dst) {
        			int index = dist2.nextInt(numDataCenters);
        			dst = dataCenterNodes[index];
        			connectionType = 2;
        		}
        	}
        	if (probability > 0.1 & probability <= 0.3) {
        		// Inter
        		src = dst = dist2.nextInt(numNodes);
        		if ((src == dataCenterNodes[0]) || (src == dataCenterNodes[1])) {
        			while (src == dst) {
        				dst = dist2.nextInt(numNodes);
        			}        			
        		} else {
        			int index = dist2.nextInt(numDataCenters);
        			dst = dataCenterNodes[index];
        		}
                connectionType = 1;
        	}
        	if (probability > 0.3 & probability <= 1) {
        		// Should comprehend all requests]
        		 src = dst = dist2.nextInt(numNodes);
                 while (src == dst) {
                     dst = dist2.nextInt(numNodes);
                 }
                 connectionType = 0;
        	}
        	
//        	src = dst = dist2.nextInt(numNodes);
//            while (src == dst) {
//            	if ((isDataCenterNetwork) && (dist2.nextDouble() <= 0.3)) { // DC Probability
//            		int index = dist2.nextInt(numDataCenters);
//            		dst = dataCenterNodes[index];
//            		connectionType = 1; // DC involved
//            	} else {
//            		dst = dist2.nextInt(numNodes);
//            	}
//            }
            
//            type = weightVector[dist1.nextInt(TotalWeight)];
//            src = dst = dist2.nextInt(numNodes);
//            
//            while (src == dst) {
//            	int index = dist2.nextInt(numDataCenters);
//            	dst = dataCenterNodes[index];
//            	connectionType = 1; // DC involved
//            }
            
            if (connectionType == 0) {
            	this.counter_regular += 1;
            } else if (connectionType == 1) {
            	this.counter_inter += 1;
            } else if (connectionType == 2) {
            	this.counter_dc += 1;
            }
            
            // MANUALLY ADDING holdingTime differentiation for DataCenter and Regular Flows
            double holdingTime, baseHoldingTime, scaledHoldingTime;
            if (connectionType == 2){
            	baseHoldingTime= 5.0;
            } else {
            	baseHoldingTime = 1.0;
            }
            	
            // Maybe think about how much time it takes according to the content
//            scaledHoldingTime = callsTypesInfo[type].getHoldingTime();
//            scaledHoldingTime = baseHoldingTime * (callsTypesInfo[type].getRate() / 25);
            scaledHoldingTime = baseHoldingTime;
//            System.out.println(scaledHoldingTime);
            
            holdingTime = dist4.nextExponential(scaledHoldingTime);

            Flow newFlow = new Flow(id, src, dst, time, callsTypesInfo[type].getRate(), holdingTime, callsTypesInfo[type].getCOS(), time+(holdingTime*0.5));
            newFlow.setDataRequest(nRequest);
            newFlow.setConnectionType(connectionType);
            Event event;
            event = new FlowArrivalEvent(time, newFlow);
            time += dist3.nextExponential(meanArrivalTime);
            events.addEvent(event);
            event = new FlowDepartureEvent(time + holdingTime, id, newFlow);
            events.addEvent(event);
            id++;
    	}
//        System.out.println("DC = " + this.counter_dc + "\nInter = " + this.counter_inter + "\nRegular = " + this.counter_regular + "\n");
    }
    
    /**
     * Gets the calls type info.
     *
     * @return the calls type info
     */

    public TrafficInfo[] getCallsTypeInfo() {
		return callsTypesInfo;
	}
    
    private int[] positioningDataCenter(int nodes) {
    	// TODO implements the positioning algorithm
    	int[] dc_set = {5, 13};
    	return dc_set;
    }
    
    public int[] positioningDC (WeightedGraph g, int k) {
		double[][] distanceMatrix = new double [g.getNumNodes()][g.getNumNodes()];
		for (int i = 0; i < g.getNumNodes(); i++) {
			for (int j = 0; j < g.getNumNodes(); j++) {
				distanceMatrix[i][j] = g.getWeight(i, j);
			}
		}
		
        int n = distanceMatrix.length;
        Random random = new Random();

        // Initialize centroids (datacenter positions) randomly
        int[] centroids = new int[k];
        for (int i = 0; i < k; i++) {
            centroids[i] = random.nextInt(n);
        }

        int[] labels = new int[n];
        boolean changed;

        do {
            changed = false;

            // Assign each node to the nearest centroid
            for (int i = 0; i < n; i++) {
                double minDistance = Double.MAX_VALUE;
                int closestCentroid = -1;

                for (int j = 0; j < k; j++) {
                    double distance = distanceMatrix[i][centroids[j]];
                    if (distance < minDistance) {
                        minDistance = distance;
                        closestCentroid = centroids[j];
                    }
                }

                if (labels[i] != closestCentroid) {
                    labels[i] = closestCentroid;
                    changed = true;
                }
            }

            // Update centroids
            for (int j = 0; j < k; j++) {
                double minSumDistance = Double.MAX_VALUE;
                int newCentroid = centroids[j];

                for (int i = 0; i < n; i++) {
                    double sumDistance = 0;
                    for (int l = 0; l < n; l++) {
                        if (labels[l] == centroids[j]) {
                            sumDistance += distanceMatrix[i][l];
                        }
                    }

                    if (sumDistance < minSumDistance) {
                        minSumDistance = sumDistance;
                        newCentroid = i;
                    }
                }

                centroids[j] = newCentroid;
            }

        } while (changed);

        return centroids;
    }
}
