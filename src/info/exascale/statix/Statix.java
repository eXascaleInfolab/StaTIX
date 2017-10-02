package info.exascale.statix;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.*;

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


public class Statix {
	static {
		System.loadLibrary("daoc");
	}
	
	public static final String  extHints = ".ipl";  // Default extension of the hints file (indicativity of the property per line)
	public static final String  extCls = ".cnl";  // Default extension of the clusters (inferred types) file (indicativity of the property per line)
	
	private static final boolean  tracingOn = false;  // Enable tracing
	private CosineSimilarityMatix  csmat = new CosineSimilarityMatix();
	
	
	public static String daocRevision()  { return daoc.libBuild().rev(); }
	public static String daocBuildInfo()  { return daoc.libBuild().summary(); }
	
	//! Update file extension to the specified one
	public static String updateFileExtension(String filename, final String ext) {
		final int iext = filename.lastIndexOf('.');
		// Consider location of the path separator to hadle files without any extension
		if(iext != -1 && iext > filename.lastIndexOf(File.separatorChar))
			filename = filename.substring(0, iext);
		return filename + ext;
	}
	
	
	//! Ask property hints in the interactive mode
	//!
	//! @param propsWeights  - updating property weights
	//! @param hdprops  - head properties view
	//! @param hints  - file name containing indicativity hints of the properties
	protected void askHints(HashMap<String, Float> propsWeights, ArrayList<PropertyOccurrences> hdprops, String hints) {
		
	}
	
	//! Generate hints from the specified file
	//!
	//! @param propsWeights  - updating property weights
	//! @param hdprops  - head properties view
	//! @param eps  - eps of the required indicativity precision
	//! @param hints  - file name containing indicativity hints of the properties
	protected void generateHints(HashMap<String, Float> propsWeights, ArrayList<PropertyOccurrences> hdprops, double eps, String hints) throws IOException {
		;
	}
	
	//! Load hints from the specified file
	//!
	//! @param propsWeights  - updating property weights
	//! @param hints  - file name containing indicativity hints of the properties
	protected void loadHints(HashMap<String, Float> propsWeights, String hints) throws IOException {
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
	public void loadDataset(String n3DataSet, boolean filteringOn, String idMapFName, String hints) throws IOException {
		HashMap<String, Property>  properties = csmat.loadInputData(n3DataSet, filteringOn, idMapFName);
				
		HashMap<String, Float> propsWeights = new HashMap<String, Float>(properties.size(), 1);
		properties.forEach((propname, prop) -> {
			// The more seldom property, the higher it's weight
			propsWeights.put(propname, (float)Math.sqrt(1./prop.occurrences));
		});

		// Apply the hints for the property weights if any
		if(hints != null) {
			if(hints.startsWith("-")) {
				ArrayList<PropertyOccurrences>  props = properties.entrySet().stream()
					.map(entry -> new PropertyOccurrences(entry.getKey(), entry.getValue().occurrences))
					.collect(Collectors.toCollection(ArrayList::new));
				properties = null;  // Properties are not required any more
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
						String nopts = hints.substring(1);  // The number of marks (options)
						final int optsNum = Integer.parseInt(nopts);
						if(optsNum <= 0)
							throw new IllegalArgumentException("The number of marks is too small");
						final double eps = nopts.isEmpty() ? 0 : 1. / (2 * optsNum);
						
						String ext = extHints;
						if(!nopts.isEmpty())
							ext = "_" + nopts + ext;
						String  hintsName = updateFileExtension(n3DataSet, ext);
						generateHints(propsWeights, props, eps, hintsName);
					}
				} else System.err.println("WARNING, the 'brief hints' are omitted because the property weights distribution is not heavy tailed in " + n3DataSet);
			} else loadHints(propsWeights, hints);
		}

		if(tracingOn)
			System.out.println("Property Weight for <http://www.w3.org/2002/07/owl#sameAs> = "
				+ propsWeights.get("<http://www.w3.org/2002/07/owl#sameAs>"));
		csmat.setPropsWeights(propsWeights);
	}

	//This function first check if it is out put results from before and will delete them before running the app and then read the directory for input dataset
	//! Load input and labeled (supervised) datasets
	//! 
	//! @param inpfname  - file name of the N3/quad RDF dataset to be loaded
	//! @param lblfname  - file name of the labeled N3/quad RDF dataset to be loaded
	//! @param filteringOn  - filter out non-typed instances from the output by inverting their ids,
	//! 	useful for the benchmarking working with ground-truth files
	//! @param idMapFName  - optional file name to output mapping of the instance id to the name (RDF subjects)
	public void loadDatasets(String inpfname, String lblfname, boolean filteringOn, String idMapFName) throws Exception {
		HashMap<String, Property>  properties = csmat.loadInputData(inpfname, filteringOn, idMapFName);
		csmat.loadGtData(lblfname, properties);
	}

	protected Graph buildGraph() {
		final Set<String>  instances = csmat.instances();
		final int instsNum = instances.size();
		Graph gr = new Graph(instsNum);
		InpLinks grInpLinks  = new InpLinks();

		// Note: Java iterators are not copyable and there is not way to get iterator to the custom item,
		// so even for the symmetric matrix all iterations should be done
		int i = 0;
		for (String inst1: instances) {
			final long  sid = csmat.instanceId(inst1);  // Source node id
			int j = 0;
			for (String inst2: instances) {
				if(j > i) {
					final float  weight = (float)csmat.similarity(inst1, inst2);
					if(weight == 0)
						continue;
					final long did = csmat.instanceId(inst2);
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

		System.err.println("The input graph is formed");
		return gr;
	}
	
	public void cluster(String outputPath, float scale, boolean multiLev, char reduction, boolean filteringOn) throws Exception {
		System.err.println("Calling the clustering lib...");
		Graph gr = buildGraph();
		// Cosin similarity matrix is not required any more, release it
		csmat = null;
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
}
