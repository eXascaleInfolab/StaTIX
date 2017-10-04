package info.exascale.statix;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.File;
import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.*;

import info.exascale.daoc.*;


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
	public static String daocSwigRevision()  { return daoc.swigRevision(); }
	
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
	protected void askHints(HashMap<String, Float> propsWeights, String[] hdprops, String hints) throws IOException {
		Console  cons = System.console();
		if(cons == null)
			throw new IOException("System console is not available");
		// Read the number of marks / evalaution granularity
		final int  marksDfl = 10;
		String  marks = cons.readLine("Input evaluation range for each property, natural number >= 2 [%d]: ", marksDfl);
		final int  nmarks = marks != null ? Integer.parseInt(marks): marksDfl;
		if(nmarks < 2)
			throw new IllegalArgumentException("The number of marks is too small: " + nmarks);
		final float eps = 0.5f/nmarks;  // Input accuracy
		
		//char skip = '';
	
		// Read properties indicativity
		cons.printf("Input significance of the properties in the range 1 .. %d for at most %d properties. Leave the input empty (just 'enter') to skip the property evaluation or in case the property is absolutely insignificant. Use 'q' to quit early, 'p' to update the previous evaluation\n", nmarks, hdprops.length);
		HashMap<String, Float>  pweights = new HashMap<String, Float>(hdprops.length, 1);
		int i = 0;  // Index of the evaluating property
		int skips = 0;  // The number of skipped properties
		while(i < hdprops.length) {
			String  val = cons.readLine("%s: ", hdprops[i]);
			// Check control values
			if(val == "q")
				break;  // Quit
			else if(val == null) {
				++skips;
				++i;  // Take next
				continue;
			} else if(val == "p") {
				// Repeat previous input if possible
				if(i > 0)
					--i;
				continue;
			} else {
				// Convert the input significance value to the probability
				final int  mark = Integer.parseInt(val);
				if(mark <= 0 || mark > nmarks) {
					System.err.println("WARNING, the specified property significance is out of the range 1 .. " + nmarks + ": " + mark + ". Correct the specified value.");
					continue;  // Reinput againg
				}
				pweights.put(hdprops[i], eps + (float)(mark - 1) / nmarks);
			}
			++i;
		}
		cons.printf("Supervision completed: %d properties are evalauted and %d skipped of %d candidates\n", i - skips, skips, hdprops.length);
		
		String  hintsName = updateFileExtension(hints, "_" + nmarks + extHints);
		// Note: the weights are updated considering eps
		saveHints(pweights, (double)eps, hintsName);
		propsWeights.putAll(pweights);
	}
	
	//! Save properties weights (hints) to the specified file
	//!
	//! @param propsWeights  - saving properties weights
	//! @param eps  - eps of the required indicativity precision
	//! @param hints  - file name containing indicativity hints of the properties
	protected void saveHints(HashMap<String, Float> propsWeights, double eps, String hints) throws IOException {
		if(propsWeights.isEmpty()) {
			System.err.println("WARNING, the hints output is omitted: propsWeights are empty");
			return;
		}
		
		try(BufferedWriter  writer = Files.newBufferedWriter(Paths.get(hints))) {
			// Output file header
			writer.write("#/ Properties: " + propsWeights.size() + "\n");
			propsWeights.replaceAll((prop, weight) -> {
				try {
					if(eps > 0)
						weight += (float)Math.IEEEremainder((double)weight, eps);
					writer.write(weight + '\t' + prop + '\n');
				} catch(IOException err) {
					throw new UncheckedIOException(err);
				}
				return weight;
			});
		} catch(UncheckedIOException err) {
			throw new IOException(err);
		}
		System.out.println(propsWeights.size() + " property weights (significance) are saved to the: " + hints);
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

	static class PropertyOccurrences {
		String  property;  //!< Property name
		int  occurrences;  //!< Total number of occurrences of the property
		
		PropertyOccurrences(String property, int occurrences) {
			// Validate arguments
			if(property == null || property.isEmpty() || occurrences <= 0)
				throw new IllegalArgumentException("The property should be specified and have positive occurrences:  "
					+ property + ", " + occurrences);
				
			this.property = property;
			this.occurrences = occurrences;
		}
	}
	
	//In case that only input file is givven to the app (without Ground-TRuth dataset)all the property weights will be set = 1
	public void loadDataset(String n3DataSet, boolean filteringOn, String idMapFName, String hints) throws IOException {
		HashMap<String, Integer>  propsocrs = csmat.loadInputData(n3DataSet, filteringOn, idMapFName);
		
		if(propsocrs.isEmpty()) {
			System.err.println("WARNING, there are not any properties to be processed in the input dataset: " + n3DataSet);
			System.exit(0);
		}
				
		HashMap<String, Float> propsWeights = new HashMap<String, Float>(propsocrs.size(), 1);
		propsocrs.forEach((propname, ocrs) -> {
			// The more seldom property, the higher it's weight
			propsWeights.put(propname, (float)Math.sqrt(1./ocrs));
		});

		// Apply the hints for the property weights if any
		if(hints != null) {
			if(hints.startsWith("-")) {
				ArrayList<PropertyOccurrences>  props = propsocrs.entrySet().stream()
					.map(entry -> new PropertyOccurrences(entry.getKey(), entry.getValue()))
					.collect(Collectors.toCollection(ArrayList::new));
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
				
				// Check for the first significant weight drop if any
				int  iewdrop = 0;
				int  ocrlast = props.get(iewdrop++).occurrences;  // The number of property occurrences
				for(; iewdrop < iehead; ++iewdrop) {
					int ocr = props.get(iewdrop).occurrences;
					if((ocrlast - ocr) * 2 >= ocrlast)
						break;
					ocrlast = ocr;
				}
				
				// Trace the indexes
				System.out.println("Head size: " + iewdrop + " (from " + iehead + " of " + props.size());
				// Reduce the properties to be supervised
				iehead = iewdrop;
				props.subList(iehead, props.size()).clear();
				props.trimToSize();
				
				if(iehead < eheadMax) {
					if(hints == "--") {
						// Note: Strings are immutable in Java
						String  hintsName = updateFileExtension(n3DataSet, extHints);
						// Note: the weights are updated considering eps
						askHints(propsWeights, props.stream().map(psocrs -> psocrs.property).toArray(String[]::new), hintsName);
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
						// Evaluate property weights using prelabeld data
						HashMap<String, Integer>  targProps = new HashMap<String, Integer>(props.size(), 1);
						props.stream().forEach(psocrs -> {
							targProps.put(psocrs.property, psocrs.occurrences);
						});
						props = null;
						csmat.loadGtData(n3DataSet, targProps);
						// Update propsWeights with the supervised weights of targProps
						propsWeights.putAll(csmat.propsWeights);
						// Note: the weights are updated considering eps
						saveHints(propsWeights, eps, hintsName);
					}
				} else System.err.println("WARNING, the 'brief hints' are omitted because the property weights distribution is not the heavy tailed in " + n3DataSet);
			} else loadHints(propsWeights, hints);
		}

		if(tracingOn)
			System.out.println("Property Weight for <http://www.w3.org/2002/07/owl#sameAs> = "
				+ propsWeights.get("<http://www.w3.org/2002/07/owl#sameAs>"));
		// Save propsWeights to the attribute
		csmat.propsWeights = propsWeights;
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
		HashMap<String, Integer>  propsocrs = csmat.loadInputData(inpfname, filteringOn, idMapFName);
		csmat.loadGtData(lblfname, propsocrs);
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
