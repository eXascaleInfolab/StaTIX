package info.exascale.SimWeighted;

import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import info.exascale.SimWeighted.NativeUtils;
import info.exascale.daoc.*;


public class main {
	static {
		System.loadLibrary("daoc");
	}

	public static final String clirev = "";  // ATTENTION: Substituted during the build
	private static final String Static = null;
	private static final boolean  tracingOn = false;  // Enable tracing
	
	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new DefaultParser();
		Options options = new Options();
		options.addOption("h", "help", false, "Show usage");
		options.addOption("g", "ground-truth", true, "The ground-truth sample (subset of the input dataset or another similar dataset with the specified type properties)");
		options.addOption("o", "output", true, "Output file, default: <inpfile>.cnl");
		options.addOption("n", "id-name", true, "Output map of the id names (<inpfile>.idm in tab separated format: <id>	<subject_name>), default: disabled");
		options.addOption("m", "multi-level", false, "Output type inference for multiple scales (hierarchy levels) besides the macro scale (top level, root)");
		options.addOption("s", "scale", true, "Scale (resolution, gamma parameter of the clustering), -1 is automatic scale inference for each cluster, >=0 is the forced static scale (<=1 for the macro clustering); default: -1");
		options.addOption("r", "reduce", false, "Reduce similarity matrix on graph construction by non-significant relations to reduce memory consumption and speedup the clustering. Recommended for large datasets or for the coarse-grained type inference (if multi-level is off)");
		options.addOption("f", "filter", false, "Filter out from the resulting clusters all subjects that do not have #type property in the input dataset, used for the type inference evaluation");
		options.addOption("v", "version", false, "Show version");
		
		HelpFormatter formatter = new HelpFormatter();
		String[] argsOpt = new String[]{"args"};
		final String appusage = main.class.getCanonicalName() + " [OPTIONS...] <inputfile.rdf>";
		final String desription = "Statistical type inference in fully automatic and semi supervised modes\nOptions:";
		final String reference = "\nSee details in https://github.com/eXascaleInfolab/StaTIX";
		
		try {
			CommandLine cmd = parser.parse(options, args);			
			// Check for the help option
			if(cmd.hasOption("h")) {
				formatter.printHelp(appusage, desription, options, reference);
				System.exit(0);
			}
			
			// Check for the version
			if(cmd.hasOption("v")) {
				// Convert <revision>(<date>)[+] to the pure revision + date
				String clirevPure = clirev;
				String clirevTime = "";
				final int ibdate = clirevPure.indexOf('(');
				if(ibdate >= 0) {
					final int iedate = clirevPure.indexOf(')');
					clirevTime = clirev.substring(ibdate + 1, iedate);
					clirevPure = clirev.substring(0, ibdate) + clirev.substring(iedate + 1);
				}
				
				System.out.println("r-" + daoc.libBuild().rev() + "." + clirevPure);
				System.out.println("= Client Build =\nRevision: " + clirevPure + "\nTime: " + clirevTime);
				System.out.println("= Library Build =\n" + daoc.libBuild().summary());
				System.exit(0);
			}
			
			String[] files = cmd.getArgs();
			if(files.length != 1)
				throw new IllegalArgumentException("The argument is invalid");

			// Check for the filtering option
			// ATTENTION: should be done before the input datasets reading
			boolean filteringOn = cmd.hasOption("f");
			String idMapFName = null;
			if(cmd.hasOption("n"))
				idMapFName = cmd.getOptionValue("n");

			if(cmd.hasOption("g")) {
				String gtDataset = cmd.getOptionValue("g");
				//System.out.println("Ground-truth file= "+gtDataset);
				LoadDatasets(files[0], gtDataset, filteringOn, idMapFName);
			}
			else {
				//System.out.println("Input file= "+args[0]);
				LoadDataset(files[0], filteringOn, idMapFName);
			}

			// Set output file
			String outpfile = null;
			if(cmd.hasOption("o")) {
				outpfile = cmd.getOptionValue("o");
			}
			else {
				outpfile = files[0];
				// Replace the extension to the clustering results
				final int iext = outpfile.lastIndexOf('.');
				if(iext != -1)
					outpfile = outpfile.substring(0, iext);
				outpfile += ".cnl";  // Default extension for the output file
			}
			// Scale
			float scale = -1;
			if(cmd.hasOption("s")) {
				scale = Float.parseFloat(cmd.getOptionValue("s"));
				if(scale != -1 && scale < 0)
					throw new IllegalArgumentException("The scale parameter is out of the expected range");
			}
			
			// Perform type inference
			Statix(outpfile, scale, cmd.hasOption("m"), cmd.hasOption("r"), filteringOn);
		}
		catch (ParseException e) {  //  | IllegalArgumentException
			e.printStackTrace();
			formatter.printHelp(appusage, desription, options, reference);
			System.exit(1);
		}
	}
		
	//In case that only input file is givven to the app (without Ground-TRuth dataset)all the property weights will be set = 1
	public static void LoadDataset(String N3DataSet, boolean filteringOn, String idMapFName) throws IOException {
		readDataSet1(N3DataSet, filteringOn, idMapFName);
		
		HashMap<String, Double> weightPerProperty = new HashMap<String, Double>(CosineSimilarityMatix.properties.size(), 1);
		Iterator propIt = CosineSimilarityMatix.properties.entrySet().iterator();
		while(propIt.hasNext()) {
			Map.Entry<String, Property> entry = (Entry<String, Property>) propIt.next();

			try {
				weightPerProperty.put(entry.getKey(),(double)1);
			}
			catch (Exception exeption) {
				throw exeption;
			}
		}
		CosineSimilarityMatix.properties = null;
		if(tracingOn)
			System.out.println("Property Weight for <http://www.w3.org/2002/07/owl#sameAs> = " + weightPerProperty.get("<http://www.w3.org/2002/07/owl#sameAs>"));
		CosineSimilarityMatix.weightsForEachProperty = weightPerProperty;
	}
	
	//function to read the Input Dataset and put the values in map and instanceListProperties TreeMaps
	public static void readDataSet1(String N3DataSet, boolean filteringOn, String idMapFName) throws IOException {
		CosineSimilarityMatix.readDataSet1(N3DataSet, idMapFName);
		
		// setBitwise for id
		if(filteringOn) {
			Iterator insIt = CosineSimilarityMatix.instanceListPropertiesTreeMap.entrySet().iterator();
			final int mask = 1 << 31;
			//System.out.println("mask= "+mask);

			while(insIt.hasNext()) {
				Map.Entry<String, InstanceProperties> entry = (Entry<String, InstanceProperties>) insIt.next();
				if (entry.getValue().isTyped == false) {
					//entry.getValue().id = -((int) entry.getValue().id);  // Note: causes issues if id is not int32_t
					entry.getValue().id = entry.getValue().id | mask;

					if(tracingOn) {
						System.out.println("value= "+entry.getKey());
						System.out.println("value= "+entry.getValue().id);
					}
				}
			}
		}
	}

	public static void readDataSet2(String N3DataSet) throws IOException {
		CosineSimilarityMatix.weightsForEachProperty = CosineSimilarityMatix.readDataSet2(N3DataSet);
	}

	public static Graph buildGraph(final short reduction) {
		Set<String> instances = CosineSimilarityMatix.instanceListPropertiesTreeMap.keySet();
		final int n = instances.size();
		Graph gr = new Graph(n, daoc.toReduction(reduction));  // To be implemented: Graph(n, reduced);
		InpLinks grInpLinks  = new InpLinks();

		// Note: Java iterators are not copyable and there is not way to get iterator to the custom item,
		// so even for the symmetric matrix all iterations should be done
		int i = 0;
		for (String inst1: instances) {
			final long  sid = CosineSimilarityMatix.instanceListPropertiesTreeMap.get(inst1).id;  // Source node id
			int j = 0;
			for (String inst2: instances) {
				if(j > i) {
					final float  weight = (float)CosineSimilarityMatix.similarity(inst1, inst2);
					if(weight == 0)
						continue;
					final long did = CosineSimilarityMatix.instanceListPropertiesTreeMap.get(inst2).id;
					//System.out.print(" " + did + ":" + weight);
					//if(weight <= 0 || Float.isNaN(weight))
					//	throw new IllegalArgumentException("Weight for #(" + inst1 + ", " + inst2 + ") is out of range: " + weight);

					grInpLinks.add(new InpLink(did, weight));
				}
				++j;
			}
			//System.out.println();
			gr.addNodeAndEdges(sid,grInpLinks);
			grInpLinks.clear();
			++i;
		}
		grInpLinks = null;
		CosineSimilarityMatix.weightsForEachProperty = null;
		instances = null;
		CosineSimilarityMatix.instanceListPropertiesTreeMap = null;
		System.err.println("Input graph formed");
		return gr;
	}
	
	public static void Statix(String outputPath, float scale, boolean multiLev, boolean reduced, boolean filteringOn) throws Exception {
		System.err.println("Calling the clustering lib...");
		short reduction = (short)(reduced
			? 0x2  // MEAN
			: 0);  // NONE
		Graph gr = buildGraph(reduction);
		reduction |= 0x10;  // SKIP_NODES as already reduced in the graph
		OutputOptions outpopts = new OutputOptions();
		final short outpflag = (short)(multiLev
			? 0x43  // CUSTLEVS | SIMPLE  // Note: CUSTLEVS respect clsrstep
			//? 0x45  // ALLCLS | SIMPLE
			: 0x41);  // ROOT | SIMPLE
		outpopts.setClsfmt(outpflag);
		outpopts.setClsrstep(0.618f);  // 0.368f (e^-1); 0.618f (golden ratio)
		outpopts.setLevmarg(daoc.toLevMargKind((short)0xff));  // LEVSTEPNUM, relative to clrstep
		outpopts.setMargmin(1);  // Omit the bottom level, start from the following one having not more than clrstep * btmlevcls clusters
		outpopts.setClsfile(outputPath);
		outpopts.setFltMembers(filteringOn);

		System.err.println("Starting the hierarchy building");
		ClusterOptions  cops = new ClusterOptions();
		cops.setGamma(scale);
		//reduction |= 0x10;  // SKIP_NODES, because they are already reduced from the graph
		cops.setReduction(daoc.toReduction(reduction));
		Hierarchy hr = gr.buildHierarchy(cops);
		System.err.println("Starting the hierarchy output");
		hr.output(outpopts);
		System.err.println("The types inference is completed");
	}
	
	//This function first check if it is out put results from before and will delete them before running the app and then read the directory for input dataset
	public static void LoadDatasets(String dataPath, String dataPath2, boolean filteringOn, String idMapFName) throws Exception {
		readDataSet1(dataPath, filteringOn, idMapFName);
		readDataSet2(dataPath2);
		CosineSimilarityMatix.properties = null;
	}
}
