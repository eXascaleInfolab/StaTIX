package info.exascale.SimWeighted;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;


public class CosineSimilarityMatix {
public static final String typeProperty = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>";
public static HashMap<String, Property> properties = null;  // Note: used only on datasets loading
public static HashMap<String, InstanceProperties> instsProps = null;
public static HashMap<String, Double> propsWeights = null;  // Used in similarity evaluation
static int totalOccurances = 0;  // Total number of occurrences of all properties in the input datasets (the number of triples)

// Output id mapping if required (idMapFName != null)
public CosineSimilarityMatix(String file1, String file2, String idMapFName) throws IOException
{
	readInputData(file1, idMapFName);
	propsWeights = readGtData(file2);
}

// Output id mapping if required (idMapFName != null)
public double[][] CosineSimilarity (String file1, String file2, String idMapFName) throws IOException
{
	readInputData(file1, idMapFName);
	propsWeights = readGtData(file2);
	
	return SymmetricMatrixProgram();
}

static class PropertyExt {
	public String  name;
	public Property  prop; 
	
	public PropertyExt(String name) {
		this.name = name;
		this.prop = new Property(name);
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
		} else propext.prop.occurances++ ;
    }
    bufferedReader.close();
    if (idmapf != null)
		idmapf.close();
		
	instsProps = new HashMap<String, InstanceProperties>(instProps.size(), 1);
	instsProps.putAll(instProps);
	instProps.clear();
	
	properties = new HashMap<String, Property>(props.size(), 1);
	props.forEach((name, propx) -> {
		properties.put(name, propx.prop);
	});
	props.clear();
//	System.out.println("List Properties for the instance <http://dbpedia.org/resource/BMW_Museum>=  "+instsProps.get("<http://dbpedia.org/resource/BMW_Museum>").propertySet);
//	System.out.println("The map with properties and number of accurances in this case for <http://www.w3.org/2002/07/owl#sameAs>= "+map.get("<http://www.w3.org/2002/07/owl#sameAs>").occurances);
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
			if(pos < 0)
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
	allprops = null;
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

public static HashMap<String, Double> readGtData(String n3DataSet) throws IOException {
	// Instance (subject): InstPropsStat
	TreeMap<String, InstPropsStat> instPStats = loadInstanceProperties(n3DataSet);
	
	// Third HashMap including the Property name from the First MapTree(properties) and totalNumber of types that it in GT [DBpedia]
	HashMap<String, Integer>  propNTypes = new HashMap<String, Integer>(properties.size(), 1);
	final ValWrapper<Integer>  typesnum = new ValWrapper<Integer>(0);
	
	instPStats.forEach((inst, propstat) -> {
		if (propstat.properties != null) {
			int matched = 0;
			for(String propname: propstat.properties) {
				if(!properties.containsKey(propname))
					continue;
				propNTypes.put(propname, propNTypes.getOrDefault(propname, 0) + propstat.types.size());
				++matched;
			}
			typesnum.val += propstat.types.size() * matched;
		}
	});
	instPStats.clear();
	// The number of type occurances in GT from the properties existing in the input dataset
	final int ntypesGT = typesnum.val;
	
	//******************************************************************PropertyWeighCalculation********************************************************
	HashMap<String, Double> weightPerProperty = new HashMap<String, Double>(properties.size(), 1);
	ArrayList<String> notFoundProps = new ArrayList<String>();

	//System.err.println("readGtData(), propNTypes: " + (propNTypes != null ? propNTypes.size() : "null")
	//	+ ", properties: " + (properties != null ? properties.size() : "null"));
	properties.forEach((propname, prop) -> {
		final double  ntypesProp = propNTypes.getOrDefault(propname, 0);
		if(ntypesProp != 0) {
			// Note: ntypesGT+1 to avoid 0, resulting values E (0, ~64-500), typically ~1 for almost full match
			// Note: it would be beneficial to omit equivalent types (having the same members) before applying this formula
			//assert ntypesProp <= ntypesGT;
			double propertyWeight = 1./ntypesGT - Math.log(ntypesProp/ntypesGT);
			weightPerProperty.put(propname, propertyWeight);
		} else notFoundProps.add(propname);
	});
	double wmed = 1.;
	if(!weightPerProperty.isEmpty()) {
		ArrayList<Double> propWeights = weightPerProperty.values().stream()
			.sorted().collect(Collectors.toCollection(ArrayList::new));
		wmed = propWeights.get(propWeights.size() / 2);
		// Trace assigned property weights
		System.out.println("readGtData(), " + propWeights.size() + " pweights [" + propWeights.get(0)
			 + ", " + propWeights.get(propWeights.size()-1) + "], mean: " + wmed + "; ntypesGT: " + ntypesGT);
		weightPerProperty.replaceAll((name, weight) -> {
			return Math.sqrt(Math.sqrt(1./properties.get(name).occurances) * weight);
		});
	}
	//// Find the median and normalize to the median
	//Collections.sort(propWeights);  // Note: even for ntypesProp/(ntypesGT+1) -> 0  wmax < 64
	//final int pwsize = propWeights.size();
	//final double wmed = pwsize >= 1 ? propWeights.get(pwsize / 2) : 1.;
	//for (int i = 0; i < pwsize; ++i)
	//	propWeights.set(i, propWeights.get(i) / wmed);
	
	// Set remained weights as the geometric mean of the initially expeted value and evaluated mean
	for (String prop: notFoundProps)
		weightPerProperty.put(prop, Math.sqrt(Math.sqrt(1./properties.get(prop).occurances) * wmed));
	
	return weightPerProperty;
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
			//tempTreeSet.clear();
		}
		
		if(instance1Properties.isEmpty() || instance2Properties.isEmpty()) {
			if(instance1Properties.isEmpty() && instance2Properties.isEmpty())
				return 1;
			return 0;
		}

		for(String prop1: instance1Properties) {
			Double weight = propsWeights.get(prop1);
			if(weight == null)
				continue;
			weight *= weight;
			inst1TotWeight += weight;
			if(instance2Properties.contains(prop1))
				powerCommon += weight;
		}
		inst1TotWeight = Math.sqrt(inst1TotWeight);

		for(String prop2: instance2Properties) {
			Double weight = propsWeights.get(prop2);
			if(weight == null)
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


public static double[][] SymmetricMatrixProgram()
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
