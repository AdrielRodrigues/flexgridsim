/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package flexgridsim;

/**
 * Simply runs the simulation, as long as there are events
 * scheduled to happen.
 * 
 * @author andred
 */
public class SimulationRunner {

    /**
     * Creates a new SimulationRunner object.
     * 
     * @param cp the the simulation's control plane
     * @param events the simulation's event scheduler
     */
	public SimulationRunner(){}
	public void running(ControlPlane cp, EventScheduler events, int b) {
        Event event;
        Tracer tr = Tracer.getTracerObject();
        MyStatistics st = MyStatistics.getMyStatisticsObject();
        int a = 0;
        boolean last = false;
        while ((event = events.popEvent()) != null) {
//        	if(a % 10000 == 0)
//        		System.out.print("|");
//        	System.out.println(b + "-" + a);
        	a++;
	        tr.add(event);
	        st.addEvent(event);
            cp.newEvent(event, last);
        }
//        cp.last();
        System.out.println(st.getblocked());
    }
}