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
	public void running(ControlPlane cp, EventScheduler events, int seed) {
        Event event;
        Tracer tr = Tracer.getTracerObject();
        MyStatistics st = MyStatistics.getMyStatisticsObject();
        boolean last = false;
        int a = 0;
        while ((event = events.popEvent()) != null) {
	        tr.add(event);
	        st.addEvent(event);
            cp.newEvent(event, last);
            //System.out.println(a++);
        }
//        cp.last();
        System.out.println(st.getblocked());
    }
}