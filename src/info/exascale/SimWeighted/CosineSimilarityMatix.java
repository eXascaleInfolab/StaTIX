package info.exascale.SimWeighted;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import java.util.*;


public class CosineSimilarityMatix {
public static final String typeProperty = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>";
public static HashMap<String, Property> properties = null;  // Note: used only on datasets loading
public static HashMap<String, InstanceProperties> instsProps = null;
public static HashMap<String, Float> propsWeights = null;  // Used in similarity evaluation
static int totalOccurances = 0;  // Total number of occurrences of all properties in the input datasets (the number of triples)

// Output id mapping if required (idMapFName != null)
public CosineSimilarityMatix(String file1, String file2, String idMapFName) throws IOException
{
	readInputData(file1, idMapFName);
	propsWeights = readGtData(file2);
}

// Output id mapping if required (idMapFName != null)
public double[][] cosineSimilarity (String file1, String file2, String idMapFName) throws IOException
{
	readInputData(file1, idMapFName);
	propsWeights = readGtData(file2);
	
	return symmetricMatrixProgram();
}

static class PropertyExt {
	public String  name;
	public Property  prop; 
	
	public PropertyExt(String name) {
		this.name = name;
		this.prop = new Property();
	}
}

//function to read the first dataset
public static void readInputData(String n3DataSet, String idMapFName) throws IOException {
	TreeMap<String, InstanceProperties> instProps = new TreeMap<String, InstanceProperties>();
	TreeMap<String, PropertyExt> props = new TreeMap<String, PropertyExt>();
    BufferedReader bufferedReader = new BufferedReader(new FileReader(n3DataSet));
    BufferedWriter  idmapf = null;
    
    if (idMapFName != null)
		idmapf = new BufferedWriter(new FileWriter(idMapFName));
    
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
		instanceProperties.propertySet.add(property);
		++totalOccurances;
		
		//********************insert to the map Treemap
		//check if the propertyname was in the map before or not
		PropertyExt propext = props.get(property);
		if (propext == null) {
			propext = new PropertyExt(property);
			props.put(propext.name, propext);
		} else propext.prop.occurrences++ ;
    }
    bufferedReader.close();
    if (idmapf != null)
		idmapf.close();
		
	instsProps = new HashMap<String, InstanceProperties>(instProps.size(), 1);
	instsProps.putAll(instProps);
	instProps = null;
	
	properties = new HashMap<String, Property>(props.size(), 1);
	props.forEach((name, propx) -> {
		properties.put(name, propx.prop);
	});
//	System.out.println("List Properties for the instance <http://dbpedia.org/resource/BMW_Museum>=  "+instsProps.get("<http://dbpedia.org/resource/BMW_Museum>").propertySet);
//	System.out.println("The map with properties and number of accurances in this case for <http://www.w3.org/2002/07/owl#sameAs>= "+map.get("<http://www.w3.org/2002/07/owl#sameAs>").occurrences);
//	System.out.println(properties.get("<http://dbpedia.org/ontology/abstract>").propertyName);
//	System.out.println(properties.size());
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
	
private static TreeMap<String, InstPropsStat> loadInstanceProperties(String n3DataSet) throws IOException {
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
	
	BufferedReader  bufferedReader = new BufferedReader(new FileReader(n3DataSet));
	String  line = null;
	while ((line = bufferedReader.readLine()) != null) {
		if (line.isEmpty())
			continue;
		//lines.add(line);
		//if (ntypes % 100 ==0)
		//		System.out.println(ntypes);
		String[] s = line.split(" ");
		
		//if (s.length<3) continue;
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
			if(propstat.properties == null)
				propstat.properties = new ArrayList<String>();
			// Update all props and get the property from the existing object
			accnames.accept(s[1], allprops, propstat.properties);
		} else {
			if(propstat.types == null)
				propstat.types = new ArrayList<String>();
			accnames.accept(s[2], alltypes, propstat.types);
		}
	
	}
	bufferedReader.close();
	return instsSProps;
}

// Cut negative numbers to zero
private static int cutneg(int a)
{
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

public static HashMap<String, Float> readGtData(String n3DataSet) throws IOException {
	// Instance (subject): InstPropsStat
	TreeMap<String, InstPropsStat> instPStats = loadInstanceProperties(n3DataSet);
	// The estimated number of types is square root of the number of properties
	// Note: this hasmap will be resized is resizable, so use load factor < 1
	final HashMap<String, TypeStat>  typesStats = new HashMap<String, TypeStat>((int)Math.sqrt(properties.size()), 0.85f);
	final ValWrapper<Integer> instsNum = new ValWrapper<Integer>(0);

	// Fill for each property in the input dataset accumulate types with occurrences and , 
	instPStats.forEach((inst, propstat) -> {
		propstat.properties.trimToSize();
		//propstat.types.trimToSize();  // Many types myght be null
		if (propstat.properties != null && propstat.types != null) {
			int matched = 0;  // The number of properties mathced 
			for(String propname: propstat.properties) {
				Property  prop = properties.get(propname);
				if(prop == null)
					continue;
				if(prop.types != null) {
					// Add new types to the property
					for(String tname: propstat.types) {
						int pos = Collections.binarySearch(prop.types, tname);
						if(pos >= 0)
							continue;  // Such type already present
						// New item
						pos = -pos - 1;
						prop.types.add(pos, new TypePropOcr(tname));
					}
				} else {
					prop.types = propstat.types.stream().map((tname) -> new TypePropOcr(tname))
						.collect(Collectors.toCollection(ArrayList::new));
					assert !(propstat.types.isEmpty() || prop.types.isEmpty()): "Prop types should exist and be be assigned";
				}
				++matched;
			}
			// Skip instances that do not have any relation to the  properties of the input dataset
			if(matched == 0)
				return;
			// Update properties occurrences in types
			for(String tname: propstat.types) {
				TypeStat tstat = typesStats.get(tname);
				if(tstat == null) {
					tstat = new TypeStat();
					typesStats.put(tname, tstat);
				}
				tstat.ocrprops += matched;
				++tstat.numinsts;
			}
			++instsNum.val;
		}
	});
	instPStats = null;
	
	//******************************************************************PropertyWeighCalculation********************************************************
	HashMap<String, Float> propertiesWeights = new HashMap<String, Float>(properties.size(), 1);
	ArrayList<String> notFoundProps = new ArrayList<String>();

	//System.err.println("readGtData(), propNTypes: " + (propNTypes != null ? propNTypes.size() : "null")
	//	+ ", properties: " + (properties != null ? properties.size() : "null"));
	properties.forEach((propname, prop) -> {
		if(prop.types == null) {
			notFoundProps.add(propname);
			return;
		}
		assert !prop.types.isEmpty(): "Property types should exist (name: " + propname
			+ ", propocr: " + prop.occurrences + ", types: " + prop.types;
		prop.types.trimToSize();
		// Evaluate property weight
		// Note: the types size is not important here, it will be captured by the similarity matrix,
		// each type impcats equally on the accumulated significance / indicativity / weight of the property
		final double  mulInsts = 1./instsNum.val;  // Instances multiplier
		double weight = 0;
		for(TypePropOcr tpo: prop.types) {
			TypeStat tstat = typesStats.get(tpo.type);
			// sqrt to have not too small values, but it causes bias to 1 boosting the smallest values the most,
			// i.e. decrease impact of the most frequent properties in the type (category, etc.)
			weight += Math.sqrt((double)tpo.propocr / tstat.ocrprops)  // Prop frequency in the type
				/ (1. - Math.log(tstat.numinsts*mulInsts));  // Inferse log IDF  (size of the type in instances)
		}
		weight /= prop.types.size();
		assert !Double.isNaN(weight): "Property weight should be valid";
		propertiesWeights.put(propname, (float)weight);
		prop.types = null;
	});
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
		System.out.println("readGtData(), " + propWeights.size() + " pweights [" + propWeights.get(0)
			 + ", " + propWeights.get(propWeights.size()-1) + "], mean: " + wmed + "; ntypesGT: " + ntypesGT);
		// For the property weights consider also initially estimated weight
		//propertiesWeights.replaceAll((name, weight) -> {
		//	return Math.sqrt(Math.sqrt(1./properties.get(name).occurrences) * weight);
		//});
	}

	//// Find the median and normalize to the median
	//Collections.sort(propWeights);  // Note: even for ntypesProp/(ntypesGT+1) -> 0  wmax < 64
	//final int pwsize = propWeights.size();
	//final double wmed = pwsize >= 1 ? propWeights.get(pwsize / 2) : 1.;
	//for (int i = 0; i < pwsize; ++i)
	//	propWeights.set(i, propWeights.get(i) / wmed);
	
	// Set remained weights as the geometric mean of the initially expeted value and evaluated mean
	for (String prop: notFoundProps)
		propertiesWeights.put(prop, (float)Math.sqrt(Math.sqrt(1./properties.get(prop).occurrences) * wmed));
	
	return propertiesWeights;
}
		
//*********************************************Calculating Cosin Similarity****************************************************************
 public static double similarity(String instance1, String instance2) {
		if (instance1 == instance2)
			return 1;
		double inst1TotWeight = 0;
		double inst2TotWeight = 0;
		double powerCommon =0;

		TreeSet<String> instance1Properties = instsProps.get(instance1).propertySet;
		TreeSet<String> instance2Properties = instsProps.get(instance2).propertySet;
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


public static double[][] symmetricMatrixProgram()
{
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
