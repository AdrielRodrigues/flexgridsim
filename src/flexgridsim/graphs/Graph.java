package flexgridsim.graphs;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Define the methods all graphs must have.
 *
 * @author pedrom, adriel
 */
public class Graph {

	private String name;
	private String dotsFileName;
	private DataSet dataSet;
	private Path folderPath;
	private Path fullpath;

	/**
	 * Instantiates a new graph.
	 *
	 * @param name the name of the graph
	 * @param dotsFileName the output dots file name
	 * @param dataSetDimension the data set dimension
	 */
	public Graph(String name, String dotsFileName,
			int dataSetDimension, String sim_folder) {
		super();
		this.name = name;
		this.dotsFileName = dotsFileName;
		this.dataSet = new DataSet(dataSetDimension);
		
		this.folderPath = Paths.get("output", sim_folder);
        this.fullpath = folderPath.resolve(this.dotsFileName);
		
//		this.folderPath = "output";
//		this.fullpath = Paths.get(this.folderPath, this.dotsFileName);
        
        try {
            Files.createDirectories(folderPath);
        } catch (IOException e) {
            System.err.println("Não foi possível criar o diretório: " + folderPath.toAbsolutePath());
        }
	}

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Write dots to file.
	 */
	// TODO enable the simulator to create the path if it does not exist
	public void writeDotsToFile() {
		FileWriter fStream;
		try {
			fStream = new FileWriter(fullpath.toString(), true);
			fStream.append(dataSet.dotToString()+System.getProperty("line.separator"));
			fStream.close();
		} catch (IOException e) {
			System.out.println("Error writing the graph file");
		} catch (IndexOutOfBoundsException e){
			System.out.println("Não calculou valor");
		}
	}
	

	/**
	 * Gets the data set.
	 *
	 * @return the data set
	 */
	public DataSet getDataSet() {
		return dataSet;
	}
}
