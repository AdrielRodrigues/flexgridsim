/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package flexgridsim;

import java.util.ArrayList;

import org.w3c.dom.*;

import flexgridsim.util.Distribution;

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
        int type, src, dst;
        double time = 0.0;
        int id = 0;
        int numNodes = pt.getNumNodes();
        Distribution dist1, dist2, dist3, dist4;
        dist1 = new Distribution(1, seed);
        dist2 = new Distribution(2, seed);
        dist3 = new Distribution(3, seed);
        dist4 = new Distribution(4, seed);
        
        int[] set_dc = positioningDataCenter(10);
        
        for (int j = 0; j < calls; j++) {
        		
            type = weightVector[dist1.nextInt(TotalWeight)];
            src = dst = dist2.nextInt(numNodes);
            while (src == dst) {
                dst = dist2.nextInt(numNodes);
            }
            
            double holdingTime;

            // Maybe think about how much time it takes according to the content
            holdingTime = dist4.nextExponential(callsTypesInfo[type].getHoldingTime());

            Flow newFLow = new Flow(id, src, dst, time, callsTypesInfo[type].getRate(), holdingTime, callsTypesInfo[type].getCOS(), time+(holdingTime*0.5));
//            newFLow.setDataRequest(nRequest);
            Event event;event = new FlowArrivalEvent(time, newFLow);
            time += dist3.nextExponential(meanArrivalTime);
            events.addEvent(event);
            event = new FlowDepartureEvent(time + holdingTime, id, newFLow);
            events.addEvent(event);
            id++;
    	}
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
}
