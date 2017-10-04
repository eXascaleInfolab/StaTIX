package info.exascale.statix;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.*;


public class CosineSimilarityMatix {
	public static final String  typeProperty = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>";
	public HashMap<String, Float>  propsWeights = null;  // Used in similarity evaluation
	private HashMap<String, InstanceProperties>  instsProps = null;  // Instance Properties statistics, required to build the input graph for the clustering
	private int  propsocrs = 0;  // Total number of occurrences of all properties in the input datasets (the number of triples)


	public CosineSimilarityMatix()  {}

	// Output id mapping if required (idMapFName != null)
	public CosineSimilarityMatix(String inpfname, String lblfname, String idMapFName) throws IOException {
		HashMap<String, Integer>  propsocrs = loadInputData(inpfname, false, idMapFName);
		loadGtData(lblfname, propsocrs);
	}
	
	//! Unique entity instances (subjects)
	public Set<String> instances()  { return instsProps != null ? instsProps.keySet() : null; }
	
	//! Return instance (subject) id by it's name
	public int instanceId(String instance)  { return instsProps.get(instance).id; }
	
	// Output id mapping if required (idMapFName != null)
	public double[][] cosineSimilarity(String inpfname, String lblfname, String idMapFName) throws IOException {
		HashMap<String, Integer>  propsocrs = loadInputData(inpfname, false, idMapFName);
		loadGtData(lblfname, propsocrs);
		return symmetricMatrixProgram();
	}

	static class PropertyExt {
		public String  name;
		public int  ocrs;  // The number of occurrences
		
		public PropertyExt(String name) {
			this.name = name;
			this.ocrs = 1;
		}
	}

	//! Load input dataset
	//! 
	//! @param n3DataSet  - file name of the N3/quad RDF dataset to be loaded
	//! @param filteringOn  - filter out non-typed instances from the output by inverting their ids,
	//! 	useful for the benchmarking working with ground-truth files
	//! @param idMapFName  - optional file name to output mapping of the instance id to the name (RDF subjects)
	//! @return properties  - loaded properties statistics (occurrences)
	public HashMap<String, Integer> loadInputData(String n3DataSet, boolean filteringOn, String idMapFName) throws IOException {
		TreeMap<String, InstanceProperties> instProps = new TreeMap<String, InstanceProperties>();
		TreeMap<String, PropertyExt> props = new TreeMap<String, PropertyExt>();
		int  ocrs = 0;  // Total number of the occurences of all properties
		
		try(
			BufferedReader  bufferedReader = Files.newBufferedReader(Paths.get(n3DataSet)); // new BufferedReader(new FileReader(n3DataSet));
			BufferedWriter  idmapf = idMapFName != null ? Files.newBufferedWriter(Paths.get(idMapFName)) : null;  // new BufferedWriter(new FileWriter(idMapFName))
		) {    
			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
				if (line.isEmpty())
					continue;
				//lines.add(line);
				String[] s = line.split(" ");
				//if (s.length<3) continue;
				final String instancemapKey = s[0];
				final int id = instProps.size();
				final String property = s[1];
				final boolean isTyped = property.contains(typeProperty);
				InstanceProperties instanceProperties = instProps.get(instancemapKey);

				if (instanceProperties == null) {
					instanceProperties = new InstanceProperties(id);
					// Note: to have the isTyped flag the even empty properties should be added to the map
					instProps.put(instancemapKey, instanceProperties);
					// Form id to instance name mapping
					if (idmapf != null)
						idmapf.write(id + "\t" + instancemapKey + "\n");
				}
				// Do not add #type property
				if (isTyped) {
					instanceProperties.isTyped = true;
					continue;
				}
				instanceProperties.properties.add(property);
				++ocrs;
				
				//********************insert to the map Treemap
				//check if the propertyname was in the map before or not
				PropertyExt propext = props.get(property);
				if (propext == null) {
					propext = new PropertyExt(property);
					props.put(propext.name, propext);
				} else ++propext.ocrs;
			}
		}
		// Save total number of occurrences to the attribute
		this.propsocrs = ocrs;
		
		// Save the resulting instances properties as an attribute
		this.instsProps = new HashMap<String, InstanceProperties>(instProps.size(), 1);
		this.instsProps.putAll(instProps);
		instProps = null;
		
		//System.out.println("List Properties for the instance <http://dbpedia.org/resource/BMW_Museum>=  "+instsProps.get("<http://dbpedia.org/resource/BMW_Museum>").properties);
		//System.out.println("The map with properties and number of accurances in this case for <http://www.w3.org/2002/07/owl#sameAs>= "+map.get("<http://www.w3.org/2002/07/owl#sameAs>").occurrences);
		//System.out.println(properties.get("<http://dbpedia.org/ontology/abstract>").propertyName);
		//System.out.println(properties.size());

		// Set the higest bit in the entities id if the entity does not have any type properties
		// to filter out such entites from the output because they can't be evalauted
		// (essential only for the evaluation based on the ground-truth)
		if(filteringOn) {
			final int mask = 1 << 31;
			//System.out.println("mask= "+mask);

			this.instsProps.values().forEach(instps -> {
				if (instps.isTyped == false) {
					//instProps.id = -((int) instProps.id);  // Note: causes issues if id is not int32_t
					instps.id = instps.id | mask;
					// Trace resulting id
					//System.out.println("value= " + inst);
					//System.out.println("value= " + instps.id);
				}
			});
		}

		HashMap<String, Integer> propsocrs = new HashMap<String, Integer>(props.size(), 1);
		props.forEach((name, propx) -> {
			propsocrs.put(name, propx.ocrs);
		});
		return propsocrs;
	}

	static class InstPropsStat {
		// Note: TreeSet consumes too much
		public ArrayList<String>  properties = null;
		public ArrayList<String>  types = null;
		//public int  ntypes = 0;
	}

	@FunctionalInterface
	public interface TriConsumer<T1, T2, T3> {
		void accept(T1 t1, T2 t2, T3 t3);
	}
		
	//! Load enities statistics (of subjects and their properties) from the labeled dataset
	//!
	//! @param n3DataSet  - RDF dataset in N3/quad format containing the type information
	//! @param props  - target properties to be accunted, null means all available properties
	private static TreeMap<String, InstPropsStat> loadInstanceProperties(String n3DataSet, Set<String> props) throws IOException {
		// instanceName, i.e. subject: InstPropsStat
		TreeMap<String, InstPropsStat>  instsSProps = new TreeMap<String, InstPropsStat>();
		TreeSet<String>  allprops = new TreeSet<String>();  // All properties of the second dataset
		TreeSet<String>  alltypes = new TreeSet<String>();  // All types of the second dataset
		
		// Accumulate names
		TriConsumer<String, TreeSet<String>, ArrayList<String>> accnames = (name, allnames, names) -> {
			if(!allnames.add(name))
				name = allnames.tailSet(name).first();
			int pos = 0;
			if(!names.isEmpty()) {
				pos = Collections.binarySearch(names, name);
				if(pos >= 0)  // The item is present already
					return;
				// New item
				pos = -pos - 1;
			}
			names.add(pos, name);
		};
		
		try(Stream<String> stream = Files.lines(Paths.get(n3DataSet))) {
			stream.forEach(line -> {
				if (line.isEmpty())
					return;
				//lines.add(line);
				//if (ntypes % 100 ==0)
				//		System.out.println(ntypes);
				String[] s = line.split(" ");
				
				//if (s.length<3) return;
				boolean isType = false;  // Type property
				if (s[1].contains(CosineSimilarityMatix.typeProperty))
					isType = true;
				
				final String instance = s[0];
				InstPropsStat propstat = instsSProps.get(instance);
				if (propstat == null) {
					propstat = new InstPropsStat();
					instsSProps.put(instance, propstat);
				}
				if(!isType) {
					// Consider only the specified properties
					if(props != null && !props.contains(s[1]))
						return;
					if(propstat.properties == null)
						propstat.properties = new ArrayList<String>();
					// Update all props and get the property from the existing object
					accnames.accept(s[1], allprops, propstat.properties);
				} else {
					if(propstat.types == null)
						propstat.types = new ArrayList<String>();
					// Consider concrete types (objects)
					accnames.accept(s[2], alltypes, propstat.types);
				}
			});
		}
		return instsSProps;
	}

	// Cut negative numbers to zero
	private static int cutneg(int a) {
		return a >= 0 ? a : 0;
	}

	static class ValWrapper<Val> {
		public Val  val;
		
		public ValWrapper(Val val) {
			this.val = val;
		}
	}

	static class TypeStat {
		// Note: initialized to 0 by default
		public int  ocrprops;  // The number of properties in all instances having this type
		public int  numinsts;  // The number of instances (subects) having this type
	}

	// The number of property occurrences in the type
	static class TypePropOcr implements Comparable<String> {
		String  type;
		int  propocr;
		
		TypePropOcr(String typename, int propocr) {
			this.type = typename;
			this.propocr = propocr;
		}
		
		TypePropOcr(String typename) {
			this.type = typename;
			this.propocr = 1;
		}
		
		public int compareTo(String typename) {
			return type.compareTo(typename);
		}
	}

	//! Evaluate properties weights loading the labeled dataset
	//!
	//! @param n3DataSet  - RDF dataset in N3/quad format containing the type information
	//! @param propsocrs  - properties and their occurrences from the input dataset, whose weight should be evalauted
	//! @param purePropStat  - evaluate instances statistics for all or only for the specified properties
	public void loadGtData(String n3DataSet, HashMap<String, Integer> propsocrs, final boolean purePropStat) throws IOException {
		// Instance (subject): InstPropsStat
		// Note: properties.keySet() has sense to supply only for the huge GT datasets like DBPedia, not for the prelabled samples
		TreeMap<String, InstPropsStat> instPStats = loadInstanceProperties(n3DataSet, purePropStat ? propsocrs.keySet() : null);  // != null ? targProps : properties.keySet());
		// The estimated number of types is square root of the number of properties
		// Note: this hasmap will be resized is resizable, so use load factor < 1
		final HashMap<String, TypeStat>  typesStats = new HashMap<String, TypeStat>((int)Math.sqrt(propsocrs.size()), 0.85f);
		final ValWrapper<Integer> instsNum = new ValWrapper<Integer>(0);
		final HashMap<String, ArrayList<TypePropOcr>>  propsTypes = new HashMap<String, ArrayList<TypePropOcr>>(propsocrs.size(), 1);
		propsocrs.keySet().forEach(prop -> {
			propsTypes.put(prop, null);
		});

		// For each property in the input dataset accumulate types with occurrences
		instPStats.forEach((inst, propstat) -> {
			// Skip instances that do not have any relation to the  properties of the input dataset
			// or do not have types information
			if(propstat.properties == null || propstat.types == null)
				return;
			// Note: anyway instPStats is released after this function
			//propstat.properties.trimToSize();
			//propstat.types.trimToSize();
			for(String propname: propstat.properties) {
				// Skip non-target properties
				if(!purePropStat && !propsTypes.containsKey(propname))
					continue;
				assert propsTypes.containsKey(propname): "The property should be present in the input dataset";
				// Types are ordered by the type name.
				ArrayList<TypePropOcr>  ptocrs = propsTypes.get(propname);
				if(ptocrs != null) {
					// Add new types to the property
					for(String tname: propstat.types) {
						int pos = Collections.binarySearch(ptocrs, tname);
						if(pos >= 0)
							continue;  // Such type already present
						// New item
						pos = -pos - 1;
						ptocrs.add(pos, new TypePropOcr(tname));
					}
				} else {
					ptocrs = propstat.types.stream().map((tname) -> new TypePropOcr(tname))
						.collect(Collectors.toCollection(ArrayList::new));
					assert !(propstat.types.isEmpty() || ptocrs.isEmpty()): "Prop types should exist and be be assigned";
				}
			}
			// Update properties occurrences in types
			for(String tname: propstat.types) {
				TypeStat tstat = typesStats.get(tname);
				if(tstat == null) {
					tstat = new TypeStat();
					typesStats.put(tname, tstat);
				}
				tstat.ocrprops += propstat.properties.size();
				++tstat.numinsts;
			}
			++instsNum.val;
		});
		instPStats = null;
		
		// PropertyWeighCalculation --------------------------------------------
		final HashMap<String, Float> propertiesWeights = new HashMap<String, Float>(propsocrs.size(), 1);
		final ArrayList<String> notFoundProps = new ArrayList<String>();

		//System.err.println("loadGtData(), propNTypes: " + (propNTypes != null ? propNTypes.size() : "null")
		//	+ ", properties: " + (propsocrs != null ? propsocrs.size() : "null"));
		propsTypes.forEach((propname, ptocrs) -> {
			if(ptocrs == null) {
				notFoundProps.add(propname);
				return;
			}
			//assert !ptocrs.isEmpty(): "Property types should exist (name: " + propname
			//	+ ", propocrs: " + ocrs + ", types: " + ptocrs;
			//ptocrs.trimToSize();
			// Evaluate property weight
			// Note: the types size is not important here, it will be captured by the similarity matrix,
			// each type impcats equally on the accumulated significance / indicativity / weight of the property
			final double  mulInsts = 1./instsNum.val;  // Instances multiplier
			double weight = 0;
			for(TypePropOcr ptype: ptocrs) {
				TypeStat tstat = typesStats.get(ptype.type);
				// sqrt to have not too small values, but it causes bias to 1 boosting the smallest values the most,
				// i.e. decrease impact of the most frequent properties in the type (category, etc.)
				weight += Math.sqrt((double)ptype.propocr / tstat.ocrprops)  // Prop frequency in the type
					/ (1. - Math.log(tstat.numinsts*mulInsts));  // Inverse log IDF (size of the type in instances)
			}
			weight /= ptocrs.size();
			assert !Double.isNaN(weight): "Property weight should be valid";
			propertiesWeights.put(propname, (float)weight);
		});
		propsTypes.clear();
		notFoundProps.trimToSize();
		final int  ntypesGT = typesStats.size();
		typesStats.clear();
		
		//System.out.print("propertiesWeights: ");
		//for(double w: propertiesWeights.values())
		//	System.out.print(" " + w);
		//System.out.println("");
		
		// Evaluate the median weight
		float wmed = 1;
		if(!propertiesWeights.isEmpty()) {
			ArrayList<Float> propWeights = propertiesWeights.values().stream()
				.sorted().collect(Collectors.toCollection(ArrayList::new));
			wmed = propWeights.get(propWeights.size() / 2);
			// Trace assigned property weights
			System.out.println("loadGtData(), " + propWeights.size() + " pweights [" + propWeights.get(0)
				 + ", " + propWeights.get(propWeights.size()-1) + "], mean: " + wmed + "; ntypesGT: " + ntypesGT);
			// For the property weights consider also initially estimated weight
			//propertiesWeights.replaceAll((name, weight) -> {
			//	return Math.sqrt(Math.sqrt(1./propsocrs.get(name)) * weight);
			//});
		}
		
		// Set remained weights as the geometric mean of the initially expeted value and evaluated mean
		for(String prop: notFoundProps)
			propertiesWeights.put(prop, (float)Math.sqrt(Math.sqrt(1./propsocrs.get(prop)) * wmed));
		
		// Note: anyway superwised weights E [0, 1]
		//// Normalize all weights to the median to have meanfull and uniform interpretation of sqrt and ^2 operations
		////final int  norm = wmed;
		//propertiesWeights.replaceAll((prop, weight) -> weight / wmed);

		// Save the resulting properties weights as an attribute
		this.propsWeights = propertiesWeights;
	}

	public void loadGtData(String n3DataSet, HashMap<String, Integer> propsocrs) throws IOException {
		loadGtData(n3DataSet, propsocrs, true);
	}

	//*********************************************Calculating Cosin Similarity****************************************************************
	 public double similarity(String instance1, String instance2) {
			if (instance1 == instance2)
				return 1;
			double inst1TotWeight = 0;
			double inst2TotWeight = 0;
			double powerCommon =0;

			TreeSet<String> instance1Properties = instsProps.get(instance1).properties;
			TreeSet<String> instance2Properties = instsProps.get(instance2).properties;
			if (instance1Properties.size() > instance2Properties.size()) {
				TreeSet<String> tempTreeSet = instance1Properties;
				instance1Properties = instance2Properties;
				instance2Properties = tempTreeSet;
			}
			
			if(instance1Properties.isEmpty() || instance2Properties.isEmpty()) {
				if(instance1Properties.isEmpty() && instance2Properties.isEmpty())
					return 1;
				return 0;
			}

			for(String prop1: instance1Properties) {
				double weight = (double)propsWeights.getOrDefault(prop1, 0.f);
				if(weight == 0)
					continue;
				weight *= weight;
				inst1TotWeight += weight;
				if(instance2Properties.contains(prop1))
					powerCommon += weight;
			}
			inst1TotWeight = Math.sqrt(inst1TotWeight);

			for(String prop2: instance2Properties) {
				double weight = (double)propsWeights.getOrDefault(prop2, 0.f);
				if(weight == 0)
					continue;
				weight *= weight;
				inst2TotWeight += weight;
			}
			inst2TotWeight = Math.sqrt(inst2TotWeight);
			//   System.out.print(powerlist);
			final double similarity = powerCommon / (inst1TotWeight * inst2TotWeight);

			//  System.out.println("Results: "+instance1+" "+instance2+" "+powerCommon+" /{ "+instance1TotalWeight1+" * "+instance1TotalWeight2+" } ");
			//if(tracingOn) {
			//	FileWriter fw = new FileWriter("./outputfile.txt");
			//	BufferedWriter output = new BufferedWriter(fw);
			//	output.write( "Results: "+instance1+" "+instance2+" "+powerCommon+" /{ "+instance1TotalWeight1+" * "+instance1TotalWeight2+" } ");
			//	output.flush();
			//}
			return similarity;
	 }


	public double[][] symmetricMatrixProgram() {
		Set<String> instances = instsProps.keySet();
		final int n = instances.size();
		double matrix[][] = new double[n][n];
	 
		int i = 0;
		int j = 0;
		// Note: Java iterators are not copyable and there is not way to get iterator to the custom item,
		// so even for the symmetric matrix all iterations should be done
		for (String inst1: instances) {
			for (String inst2: instances) {
				if(j > i) {
					matrix[i][j] = similarity(inst1, inst2);
					//if(tracingOn)
					//	System.out.print(matrix[i][j] + " ");
				} else if (j < i)
					matrix[i][j] = matrix[j][i];
				else matrix[i][j] = 1;
				++j;
			}
			//if(tracingOn)
			//	System.out.println();
			++i;
		}
		
		return matrix;
	}
}
