package flexgridsim.rsa;

import flexgridsim.FlexGridLink;
import flexgridsim.Flow;
import flexgridsim.LightPath;
import flexgridsim.Path;
import flexgridsim.PhysicalTopology;
import flexgridsim.Slot;
import flexgridsim.TrafficGenerator;
import flexgridsim.VirtualTopology;
import flexgridsim.util.KShortestPaths;
import flexgridsim.util.Modulations;
import flexgridsim.util.WeightedGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Collections;
import org.w3c.dom.Element;

//Mesmo algoritmo do EEMPR com alterações no formato de modulação escolhido e nos limites aceitáveis para o Crosstalk
//feitos nos métodos getXTThreshold e trocado o método getModulationByHosseini por getModulationHosseiniModified
public class EEMPR_modified implements RSA {
    private PhysicalTopology pt;
    private VirtualTopology vt;
    private ControlPlaneForRSA cp;
    private WeightedGraph graph;



    @Override
    public void simulationInterface(Element xml, PhysicalTopology pt, VirtualTopology vt,
            ControlPlaneForRSA cp, TrafficGenerator traffic) {
        this.pt = pt;
        this.vt = vt;
        this.cp = cp;
        this.graph = pt.getWeightedGraph();
    }

    public EEMPR_modified() {

    }
    //Parâmetros físicos
    private double k_coupling = 4e-4; 
    private double r_bending = 0.05;
    private double beta = 4e6;
    private double lambda_pitch = 4e-5;

    @Override
    public void flowDeparture (Flow flow) {

    }
    //O artigo diz que devemos pegar os K menores caminhos (Sugere K = 3) e ir tentando alocar neles, no primeiro que conseguir
    //retorna o caminho em questão e utiliza
    
    public void flowArrival(Flow flow) {
        KShortestPaths kShortespaths = new KShortestPaths();
        int [][] menores_caminhos = kShortespaths.dijkstraKShortestPaths(graph, flow.getSource(), flow.getDestination(), 3);

        if (menores_caminhos == null || menores_caminhos.length == 0) {
            cp.blockFlow(flow.getID());
            return;
        }
        for (int i = 0; i < menores_caminhos.length; i++) { 
            int[] caminho_candidato = menores_caminhos[i];
            if (caminho_candidato.length < 2) {
                continue;
            }

            int[] links = new int [caminho_candidato.length - 1];
            int distance = 0;

            for(int j = 0; j < caminho_candidato.length - 1; j++) {
                int linkID = pt.getLink(caminho_candidato[j], caminho_candidato[j+1]).getID();
                links[j] = linkID;
                distance += pt.getLink(linkID).getWeight();
            }
            //Como é o arquivo modificado, alterei a modulação para ser mais conservador
            int modulation = Modulations.getModulationByHosseiniModified(distance);
            int bitsPerSymbol = Modulations.bitsPerSymbol(modulation);

            double rate = flow.getRate();

            double slotcapacity = pt.getSlotCapacity() * bitsPerSymbol;
            if (slotcapacity == 0) slotcapacity = 12.5;

            int req_slots = (int) Math.ceil(rate / slotcapacity);

            int guardband = pt.getGrooming() ? 0 : 1;

            List<Gap> lista_gaps = findGaps(links, req_slots);

            // 5. Classificar Gaps
            List<Gap> exactFit = new ArrayList<>();
            List<Gap> bigGaps = new ArrayList<>();
            List<Gap> smallGaps = new ArrayList<>();

            int demand = req_slots + guardband;

            for (Gap gap : lista_gaps) {
                if (gap.size == demand) exactFit.add(gap);
                else if (gap.size > demand) bigGaps.add(gap);
                else smallGaps.add(gap);
            }
            // Ordenações conforme Algoritmo 1 (Linhas 71-77)
            // Exact: ordem crescente de core
            exactFit.sort(Comparator.comparingInt((Gap g) -> g.core).thenComparingInt(g -> g.initial_slot));
            
            // Big e Small: ordem decrescente de tamanho
            Comparator<Gap> sizeDesc = Comparator.comparingInt((Gap g) -> g.size).reversed()
                                                 .thenComparingInt(g -> g.core)
                                                 .thenComparingInt(g -> g.initial_slot);
            bigGaps.sort(sizeDesc);
            smallGaps.sort(sizeDesc);

            
            // Estratégia A: Exact Fit
            for (Gap gap : exactFit) {
                //Checa se o crosstalk está abaixo do permitido em todas as estratégias
                if (checkCrosstalk(gap, links, modulation, demand)) {
                    if (establishConnection(flow, links, Collections.singletonList(gap), modulation)) {
                        return;
                    }    
                }
            }

            // Estratégia B: Big Gaps
            for (Gap gap : bigGaps) {
                if (checkCrosstalk(gap, links, modulation, demand)) {
                    Gap actual_gap = new Gap(gap.core, gap.initial_slot, demand);
                    if (establishConnection(flow, links, Collections.singletonList(actual_gap), modulation)) {
                        return;
                    }
                }
            }


            // Estratégia C: Multipath (Small Gaps), o artigo sugere utilizar small gaps para suprir a demanda
            List<Gap> subpaths = new ArrayList<>();
            int slotsmissing = req_slots;

            for(Gap gap: smallGaps) {
                
                int capacityofgap = gap.size - guardband;

                if(capacityofgap > 0) {
                    int slotsusing = Math.min(slotsmissing, capacityofgap);
                    int physicalSize = slotsusing + guardband;
                    Gap candidate_subpath = new Gap(gap.core, gap.initial_slot, physicalSize);

                    if(checkCrosstalk(candidate_subpath, links, modulation, physicalSize)) {
                        subpaths.add(candidate_subpath);
                        slotsmissing -= slotsusing;

                        if (slotsmissing <= 0) {
                            break;
                        }
                    }
                    
                }
            }

            if (slotsmissing < 0 || !subpaths.isEmpty()) {
                if (establishConnection(flow, links, subpaths, modulation)) {
                    return;
                }
            }
        }
        cp.blockFlow(flow.getID());
    }
     

    //Método para encontrar os gaps e listar eles, de modo que o algoritmo entenda onde pode trabalhar
    private List<Gap> findGaps(int[] links, int req_slots) {
        List<Gap> gaps = new ArrayList<>();
        int numCores = pt.getNumCores();
        int numSlots = pt.getNumSlots();

        for (int c = 0; c < numCores; c++) {
            int start = -1;
            int size = 0;
            
            for (int s = 0; s < numSlots; s++) {
                boolean isFreeinAllLinks = true;

                // Verifica continuidade em todos os links do caminho
                for(int linkID: links) {
                    if(!pt.getLink(linkID).getSpectrum(c, s)) {
                        isFreeinAllLinks = false;
                        break;
                    }
                }

                if(isFreeinAllLinks) {
                    if (size == 0) start = s;
                    size++;
                } else {
                    // Fim de um gap contíguo
                    if (size > 0) {
                        gaps.add(new Gap(c, start, size));
                    }
                    size = 0;
                    start = -1;
                }
            }
            // Verifica se o gap vai até o último slot do espectro
            if (size > 0) {
                gaps.add(new Gap(c, start, size));
            }
        }
        return gaps;  
    }
    
    //Métodozinho só pra estabelecr a conexão
    private boolean establishConnection(Flow flow, int[] links, List<Gap> gaps, int modulation) {
        ArrayList<LightPath> lightpaths = new ArrayList<>();

        for (Gap gap : gaps) {
            ArrayList<Slot> slots = new ArrayList<>();
            int start = gap.initial_slot;
            int end = gap.initial_slot + gap.size - 1;

            for (int linkID : links) {  
                for (int s = start; s <= end; s++) {
                    slots.add(new Slot(gap.core, s, linkID));
                }
            }
            
            Path path = new Path(links, slots);
            long id = vt.createLightpath(path, modulation);
            
            if (id >= 0) {
                lightpaths.add(vt.getLightpath(id));
            } else {
                for(LightPath lp : lightpaths) {
                    vt.removeLightPath(lp.getID());
                }
                return false;
            }
        }

        flow.setLinks(links);
        ArrayList<Slot> allSlots = new ArrayList<>();
        for(LightPath lps : lightpaths) {
            allSlots.addAll(lps.getSlotList());
        }
        flow.setSlotList(allSlots);
        flow.setModulationLevel(modulation);

        
        //Tenta aceitar no cp
        if (cp.acceptFlow(flow.getID(), lightpaths)) {
            return true;
        } else {
            for(LightPath lp : lightpaths) {
                vt.removeLightPath(lp.getID());
            }
            return false;
        }
    } 



    private boolean checkCrosstalk(Gap gap, int[] links, int modulation, int limit_size) {
        double threshold = getXtThreshold(modulation);
        int checkUntil = Math.min(gap.size, limit_size);

        for(int s = gap.initial_slot; s < gap.initial_slot + checkUntil; s++) {
            double accumulatedXT = 0;

            for(int linkID: links) {
                FlexGridLink link = (FlexGridLink) pt.getLink(linkID);
                double length = pt.getLink(linkID).getWeight() * 1000; //Distancia lá é dada em km

                int n = link.getActiveNeighbor(gap.core, s);

                if (n > 0) {
                    double h = (2 * Math.pow(k_coupling, 2) * r_bending) / (beta * lambda_pitch);

                    double numerator = n - n * Math.exp(-(n + 1) * h * length);
                    double denominator = 1 + n * Math.exp(-(n + 1) * h * length);
                    double xtLinear = numerator / denominator;
                    
                    accumulatedXT += xtLinear;
                }

            }

            if (accumulatedXT > 0) {
                double xtDB = 10 * Math.log10(accumulatedXT);
                if (xtDB >= threshold) {
                    return false;
                }
            }

        }
        return true;
        //Aqui retorna se o XT está abaixo do permitido, se sim, procede, 
    }
    
    private double getXtThreshold(int modFormat) {
        if (modFormat == 5) return -37.0; //Para 64QAM, requer baixo ruído
        if (modFormat == 4) return -34.0; //32QAM 
        if (modFormat == 3) return -31.79; //16QAM
        if (modFormat == 2) return -28.77; //8QAM
        if (modFormat == 1) return -25.76; //QPSK
        return -15.0; // BPSK tolerância alta
    }

        private class Gap {
        int core;
        int initial_slot;
        int size;

        public Gap(int core, int initial_slot, int size){
            this.core = core;
            this.initial_slot = initial_slot;
            this.size = size;
        }
    }
}
