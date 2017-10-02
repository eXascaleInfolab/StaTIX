package info.exascale.SimWeighted;

import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.text.ParseException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.*;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;

import info.exascale.daoc.*;


class PropertyOccurrences {
	String  property;
	int  occurrences;
	
	PropertyOccurrences(String property, int occurrences) {
		// Validate arguments
		if(property == null || property.isEmpty() || occurrences <= 0)
			throw new IllegalArgumentException("The property should be specified and have positive occurrences:  "
				+ property + ", " + occurrences);
			
		this.property = property;
		this.occurrences = occurrences;
	}
}

public class main {
	static {
		System.loadLibrary("daoc");
	}

	public static final String  clirev = "";  // ATTENTION: Substituted during the build
	private static final boolean  tracingOn = false;  // Enable tracing
	public static final String  extHints = ".ipl";  // Default extension of the hints file (indicativity of the property per line)
	public static final String  extCls = ".cnl";  // Default extension of the clusters (inferred types) file (indicativity of the property per line)
	//public CosineSimilarityMatix  csmat = null;
	
	//! Update file extension to the specified one
	public static String updateFileExtension(String filename, final String ext) {
		final int iext = filename.lastIndexOf('.');
		// Consider location of the path separator to hadle files without any extension
		if(iext != -1 && iext > filename.lastIndexOf(File.separatorChar))
			filename = filename.substring(0, iext);
		return filename + ext;
	}
	
	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new DefaultParser();
		// Mutually exclusive group of the supervised options / input format
		final OptionGroup optspv = new OptionGroup();
		// Option(String opt, String longOpt, boolean hasArg, String description)
		// ground-truth or annotated, labeled
		optspv.addOption(new Option("g", "ground-truth", true, "The ground-truth sample (subset of the input dataset or another similar dataset with the specified type properties)"));
		optspv.addOption(new Option("b", "brief-hints", true, "Brief hits, possible values:\n'--'  - interactive hints \n'<filename" + extHints + ">'  - read from the file having the format: <indicativity> <property>, where indicativity E [0, 1]; 0 - the property has no any impact on the entity type, 1 - the property fully specifies the entity type, line comments starting with '#' are allowed. \n'-[<nopts=INF>]'  - automatic generation of the hints to the <inpfile_npots" + extHints + ">, where <nopts> is the number of options (>= 2), granularity that defines the input indicativity precision eps=1/(nopts*2): eps=0.167 for 3 options"));  // Center of each band is eps + eps*i, delta: +/-eps, wide: 2*eps
		
		Options options = new Options();
		options.addOption("h", "help", false, "Show usage");
		// Workflow: analyze input dataset, ask to rate potentially indicative properties (that might have huge impact)
		//options.addOption("p", "supervised", true, "Supervision hint data in the format: <indicativity>\t <property>, where indicativity E [0, 1], '#' line comments are allowed.");
		options.addOptionGroup(optspv);
		options.addOption("o", "output", true, "Output file, default: <inpfile>" + extCls);
		options.addOption("n", "id-name", true, "Output map of the id names (<inpfile>.idm in tab separated format: <id>	<subject_name>), default: disabled");
		options.addOption("m", "multi-level", false, "Output type inference for multiple scales (representative clusters from all hierarchy levels) besides the macro scale (top level, root)");
		options.addOption("s", "scale", true, "Scale (resolution, gamma parameter of the clustering), -1 is automatic scale inference for each cluster, >=0 is the forced static scale (<=1 for the macro clustering); default: -1");
		options.addOption("r", "reduce", true, "Reduce similarity matrix on graph construction by non-significant relations to reduce memory consumption and speedup the clustering. Options: a - accurate, m - mean, s - severe. Recommended for large datasets");
		options.addOption("f", "filter", false, "Filter out from the resulting clusters all subjects that do not have #type property in the input dataset, used for the type inference evaluation");
		options.addOption("v", "version", false, "Show version");
		
		HelpFormatter formatter = new HelpFormatter();
		String[] argsOpt = new String[]{"args"};
		final String appusage = main.class.getCanonicalName() + " [OPTIONS...] <inputfile.rdf>";
		final String desription = "Statistical type inference in fully automatic and semi supervised modes\nOptions:";
		final String reference = "\nSee details in https://github.com/eXascaleInfolab/StaTIX";
		
		try {
			final CommandLine  cmd = parser.parse(options, args);
			
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
				throw new IllegalArgumentException("A single input dataset is expected with optimal parameters");

			// Check for the filtering option
			// ATTENTION: should be done before the input datasets reading
			boolean filteringOn = cmd.hasOption("f");
			String idMapFName = null;
			if(cmd.hasOption("n"))
				idMapFName = cmd.getOptionValue("n");
				
			if(cmd.hasOption("g")) {
				String gtDataset = cmd.getOptionValue("g");
				//System.out.println("Ground-truth file= "+gtDataset);
				loadDatasets(files[0], gtDataset, filteringOn, idMapFName);
			}
			else {
				String hints = cmd.hasOption("b") ? cmd.getOptionValue("b") : null;
				// Validate hints to fail early in case of issues
				if(hints != null) {
					if(hints.isEmpty())  // '' or ""
						throw new IllegalArgumentException("The hints parameter should not be empty");
					if(!hints.startsWith("-") && !Files.isReadable(Paths.get(hints))) {
						// Note: the hints are not loaded if not required for the particular dataset
						throw new IllegalArgumentException("The hints file is not readable");
						//if(!Files.exists(hints)) {
						//	// Allow absence of the specified file showing a warning, which is useful the
						//	// batch mode for the case when the hints are not necessary for this dataset
						//	System.err.println("WARNING, switching to the non-supervised mode because the hints file does not exist: " + hints);
						//	hints = null;
						//} else
						//if(!Files.isReadable(hints))
						//	throw new IllegalArgumentException("The hints file is not readable");
					}
					if(hints != "--" && hints.length() >= 2 && Integer.parseInt(hints.substring(1)) <= 1)
						throw new IllegalArgumentException("The hints number of options is out of range");
				}
				loadDataset(files[0], filteringOn, idMapFName, hints);
			}

			// Set output file
			String outpfile = null;
			if(cmd.hasOption("o")) {
				outpfile = cmd.getOptionValue("o");
			}
			else {
				outpfile = files[0];
				// Replace the extension to the clustering results
				outpfile = updateFileExtension(outpfile, extCls);  // Default extension for the output file
			}
			// Scale
			float scale = -1;
			if(cmd.hasOption("s")) {
				scale = Float.parseFloat(cmd.getOptionValue("s"));
				if(scale != -1 && scale < 0)
					throw new IllegalArgumentException("The scale parameter is out of the expected range");
			}
			// Reduction policy
			char reduction = 'n';  // None
			if(cmd.hasOption("r")) {
				String val = cmd.getOptionValue("r");
				if(!val.isEmpty()) {
					if(val.length() >= 2 || "ams".indexOf(val.charAt(0)) == -1)
						throw new IllegalArgumentException("The reduction parameter is out of the expected range");
					reduction = val.charAt(0);
				}
			}
			
			// Perform type inference
			statix(outpfile, scale, cmd.hasOption("m"), reduction, filteringOn);
		}
		catch (ParseException e) {  //  | IllegalArgumentException
			e.printStackTrace();
			formatter.printHelp(appusage, desription, options, reference);
			System.exit(1);
		}
	}
	
	
	//! Ask property hints in the interactive mode
	//!
	//! @param propsWeights  - updating property weights
	//! @param hdprops  - head properties view
	//! @param hints  - file name containing indicativity hints of the properties
	public static void askHints(HashMap<String, Float> propsWeights, ArrayList<PropertyOccurrences> hdprops, String hints) {
		
	}
	
	//! Generate hints from the specified file
	//!
	//! @param propsWeights  - updating property weights
	//! @param hdprops  - head properties view
	//! @param eps  - eps of the required indicativity precision
	//! @param hints  - file name containing indicativity hints of the properties
	public static void generateHints(HashMap<String, Float> propsWeights, ArrayList<PropertyOccurrences> hdprops, double eps, String hints) throws IOException {
		;
	}
	
	//! Load hints from the specified file
	//!
	//! @param propsWeights  - updating property weights
	//! @param hints  - file name containing indicativity hints of the properties
	public static void loadHints(HashMap<String, Float> propsWeights, String hints) throws IOException {
		final Pattern  witespace = Pattern.compile("\\s");  // Note: JAVA requires additional quoting or Pattern.quote("\s")
		try(Stream<String> stream = Files.lines(Paths.get(hints))) {
			stream.forEach(line -> {
				String[]  parts = witespace.split(line, 2);
				// Skip comments, note that the leading splitting allows line comments starting with whitespaces
				if(parts[0].startsWith("#"))
					return;
				if(parts.length != 2) {
					System.err.println("WARNING, Invalid property in the file '" + hints + "' is omitted: " + line);
					return;
				}
				propsWeights.put(parts[1], Float.parseFloat(parts[0]));
			});
		}
	}
		
	//In case that only input file is givven to the app (without Ground-TRuth dataset)all the property weights will be set = 1
	public static void loadDataset(String n3DataSet, boolean filteringOn, String idMapFName, String hints) throws IOException {
		readInputData(n3DataSet, filteringOn, idMapFName);
				
		HashMap<String, Float> propsWeights = new HashMap<String, Float>(CosineSimilarityMatix.properties.size(), 1);
		CosineSimilarityMatix.properties.forEach((propname, prop) -> {
			// The more seldom property, the higher it's weight
			propsWeights.put(propname, (float)Math.sqrt(1./prop.occurrences));
		});

		// Apply the hints for the property weights if any
		if(hints != null) {
			if(hints.startsWith("-")) {
				ArrayList<PropertyOccurrences>  props = CosineSimilarityMatix.properties.entrySet().stream()
					.map(entry -> new PropertyOccurrences(entry.getKey(), entry.getValue().occurrences))
					.collect(Collectors.toCollection(ArrayList::new));
				CosineSimilarityMatix.properties = null;
				// Sort properties by the decreasing occurrances
				props.sort((p1, p2) -> p2.occurrences-p1.occurrences);
				// Check whether the hints are required for this dataset by comparing
				// the tail following the median VS head of sqrt(size)
				final int  imed = props.size()/2;
				final double  tailWeight = props.subList(imed, props.size()).stream().mapToDouble(p -> 1./p.occurrences).sum();
				final int  eheadMax = (int)Math.round(Math.sqrt(props.size())) + 1;
				int  iehead = 0;  // End index of the head
				double  headWeight = 0;

				// Evaluate head weight and size
				for(; iehead < eheadMax && headWeight < tailWeight; ++iehead)
					headWeight += 1./props.get(iehead).occurrences;
				++iehead;  // Include the last evaluated item
				props.subList(iehead, props.size()).clear();
				props.trimToSize();
				
				if(headWeight >= tailWeight) {
					if(hints == "--") {
						// Note: Strings are immutable in Java
						String  hintsName = updateFileExtension(n3DataSet, extHints);
						askHints(propsWeights, props, hintsName);
					} else {
						String nopts = hints.substring(1);
						final double eps = nopts.isEmpty() ? 0 : 1. / (2 * Integer.parseInt(nopts));
						
						String ext = extHints;
						if(!nopts.isEmpty())
							ext = "_" + nopts + ext;
						String  hintsName = updateFileExtension(n3DataSet, ext);
						generateHints(propsWeights, props, eps, hintsName);
					}
				} else System.err.println("WARNING, the 'brief hints' are omitted because the property weights distribution is not heavy tailed in " + n3DataSet);
			} else loadHints(propsWeights, hints);
		}
		CosineSimilarityMatix.properties = null;

		if(tracingOn)
			System.out.println("Property Weight for <http://www.w3.org/2002/07/owl#sameAs> = "
				+ propsWeights.get("<http://www.w3.org/2002/07/owl#sameAs>"));
		CosineSimilarityMatix.propsWeights = propsWeights;
	}
	
	//function to read the Input Dataset and put the values in map and instanceListProperties TreeMaps
	public static void readInputData(String n3DataSet, boolean filteringOn, String idMapFName) throws IOException {
		CosineSimilarityMatix.readInputData(n3DataSet, idMapFName);
		// Set the higest bit in the entities id if the entity does not have any type properties
		// to filter out such entites from the output because they can't be evalauted
		// (essential only for the evaluation based on the ground-truth)
		if(filteringOn) {
			final int mask = 1 << 31;
			//System.out.println("mask= "+mask);

			CosineSimilarityMatix.instsProps.forEach((inst, instProps)-> {
				if (instProps.isTyped == false) {
					//instProps.id = -((int) instProps.id);  // Note: causes issues if id is not int32_t
					instProps.id = instProps.id | mask;

					if(tracingOn) {
						System.out.println("value= " + inst);
						System.out.println("value= " + instProps.id);
					}
				}
			});
		}
	}

	public static void readGtData(String n3DataSet) throws IOException {
		CosineSimilarityMatix.propsWeights = CosineSimilarityMatix.readGtData(n3DataSet);
	}

	public static Graph buildGraph() {
		Set<String> instances = CosineSimilarityMatix.instsProps.keySet();
		final int n = instances.size();
		Graph gr = new Graph(n);
		InpLinks grInpLinks  = new InpLinks();

		// Note: Java iterators are not copyable and there is not way to get iterator to the custom item,
		// so even for the symmetric matrix all iterations should be done
		int i = 0;
		for (String inst1: instances) {
			final long  sid = CosineSimilarityMatix.instsProps.get(inst1).id;  // Source node id
			int j = 0;
			for (String inst2: instances) {
				if(j > i) {
					final float  weight = (float)CosineSimilarityMatix.similarity(inst1, inst2);
					if(weight == 0)
						continue;
					final long did = CosineSimilarityMatix.instsProps.get(inst2).id;
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
		CosineSimilarityMatix.propsWeights = null;
		instances = null;
		CosineSimilarityMatix.instsProps = null;
		System.err.println("Input graph formed");
		return gr;
	}
	
	public static void statix(String outputPath, float scale, boolean multiLev, char reduction, boolean filteringOn) throws Exception {
		System.err.println("Calling the clustering lib...");
		Graph gr = buildGraph();
		OutputOptions outpopts = new OutputOptions();
		final short outpflag = (short)(multiLev
			? 0x4A  // SIMPLE | SIGNIFICANT  (0xA - SIGNIF_OWNSHIER, 0xB - SIGNIF_OWNAHIER)
			// ? 0x43  // SIMPLE | CUSTLEVS  // Note: CUSTLEVS respect clsrstep
			//? 0x45  // SIMPLE | ALLCLS
			: 0x41);  // SIMPLE | ROOT
		outpopts.setClsfmt(outpflag);
		
		// Set SignifclsOptions if required
		if(multiLev) {
			SignifclsExtoptions sgnopts = new SignifclsExtoptions();
			sgnopts.setDensdrop(0.9f);
			sgnopts.setWrstep(0.95f);
			sgnopts.setSzmin(0);  // 2
			outpopts.setSignifcls(sgnopts);
		}
		
		// Note: clsrstep, levmarg, margmin actual only for the CUSTLEVS, but strored in the same space as parameters for the multilev output in the latest versions of the DAOC
		//outpopts.setClsrstep(0.618f);  // 0.368f (e^-1); 0.618f (golden ratio)
		//outpopts.setLevmarg(daoc.toLevMargKind((short)0xff));  // LEVSTEPNUM, level steps relative to clrstep
		//outpopts.setMargmin(1);  // Omit the bottom level, start from the following one having not more than clrstep * btmlevcls clusters
		outpopts.setClsfile(outputPath);
		outpopts.setFltMembers(filteringOn);

		System.err.println("Starting the hierarchy building");
		ClusterOptions  cops = new ClusterOptions();
		cops.setGamma(scale);
		short rdcpolicy = 0;  // NONE
		switch(reduction) {
		case 'a':
			rdcpolicy = (short)0x1;  // ACCURATE
			break;
		case 'm':
			rdcpolicy = (short)0x2;  // MEAN
			break;
		case 's':
			rdcpolicy = (short)0x3;  // SEVERE
			break;
		}
		cops.setReduction(daoc.toReduction(rdcpolicy));
		Hierarchy hr = gr.buildHierarchy(cops);
		System.err.println("Starting the hierarchy output");
		hr.output(outpopts);
		System.err.println("The types inference is completed");
	}
	
	//This function first check if it is out put results from before and will delete them before running the app and then read the directory for input dataset
	public static void loadDatasets(String dataPath, String dataPath2, boolean filteringOn, String idMapFName) throws Exception {
		readInputData(dataPath, filteringOn, idMapFName);
		readGtData(dataPath2);
		CosineSimilarityMatix.properties = null;
	}
}
