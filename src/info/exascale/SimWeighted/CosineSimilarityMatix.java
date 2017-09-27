package info.exascale.SimWeighted;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.stream.Collectors;


public class CosineSimilarityMatix {
public static final String typeProperty = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>";
public static TreeMap<String, Property> properties = new TreeMap<String, Property>();  // Note: used only on datasets loading
public static TreeMap<String, InstanceProperties> instancePropertiesMap = new TreeMap<String, InstanceProperties>();
public static HashMap<String, Double> weightsForEachProperty = null;  // Used in similarity evaluation
static int totalOccurances = 0;  // Total number of occurrences of all properties in the input datasets (the number of triples)

// Output id mapping if required (idMapFName != null)
public CosineSimilarityMatix(String file1, String file2, String idMapFName) throws IOException
{
	readDataSet1(file1, idMapFName);
	weightsForEachProperty = readDataSet2(file2);
}

// Output id mapping if required (idMapFName != null)
public double[][] CosineSimilarity (String file1, String file2, String idMapFName) throws IOException
{
	readDataSet1(file1, idMapFName);
	weightsForEachProperty = readDataSet2(file2);
	
	return SymmetricMatrixProgram();
}

//function to read the first dataset
public static void readDataSet1(String n3DataSet, String idMapFName) throws IOException {
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
		final int id = instancePropertiesMap.size();
		final String property = s[1];
		final boolean isTyped = property.contains(typeProperty);
		InstanceProperties instanceProperties = instancePropertiesMap.get(instancemapKey);

		if (instanceProperties == null) {
			instanceProperties = new InstanceProperties(id);
			// Note: to have the isTyped flag the even empty properties should be added to the map
			instancePropertiesMap.put(instancemapKey, instanceProperties);
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
		//check if the propertyname was in out TreeMap before or not
		Property entryProperty = properties.get(property);
		if (entryProperty == null) {
			entryProperty = new Property(property);
			properties.put(entryProperty.name, entryProperty);
		} else entryProperty.occurances++ ;
    }
    bufferedReader.close();
    if (idmapf != null)
		idmapf.close();

//	System.out.println("List Properties for the instance <http://dbpedia.org/resource/BMW_Museum>=  "+instancePropertiesMap.get("<http://dbpedia.org/resource/BMW_Museum>").propertySet);
//	System.out.println("The TreeMap Including properties and number of accurances in this case for <http://www.w3.org/2002/07/owl#sameAs>= "+map.get("<http://www.w3.org/2002/07/owl#sameAs>").occurances);
//	System.out.println(properties.get("<http://dbpedia.org/ontology/abstract>").propertyName);
//	System.out.println(properties.size());
	
}

static class InstPropsStat {
	// Note: TreeSet consumes too much
	public ArrayList<String>  properties = null;
	public int  typeCount = 0;
}
	
public static TreeMap<String, InstPropsStat> loadInstanceProperties(String n3DataSet) throws IOException {
	//TreeMap key=instanceName and the value= List Of Properties of the key.
	TreeMap<String, InstPropsStat>  instProps = new TreeMap<String, InstPropsStat>();
	TreeSet<String>  allprops = new TreeSet<String>();  // All properties of the second dataset
	BufferedReader bufferedReader = new BufferedReader(new FileReader(n3DataSet));
	String line = null;
	while ((line = bufferedReader.readLine()) != null) {
		if (line.isEmpty())
			continue;
		//lines.add(line);
		//if (typeCount % 100 ==0)
		//		System.out.println(typeCount);
		String[] s = line.split(" ");
		
		//if (s.length<3) continue;
		boolean isTyped = false;
		if (s[1].contains(CosineSimilarityMatix.typeProperty)) {
			isTyped = true;
		}
		
		final String instance = s[0];
		InstPropsStat propstat = instProps.get(instance);
		if (propstat == null) {
			propstat = new InstPropsStat();
			instProps.put(instance, propstat);
		}
		if (!isTyped) {
			if (propstat.properties == null)
				propstat.properties = new ArrayList<String>();
			// Update all props and get the property from the existing object
			String propname = s[1];
			if(!allprops.add(propname))
				propname = allprops.tailSet(propname).first();
			int pos = 0;
			if(!propstat.properties.isEmpty()) {
				pos = Collections.binarySearch(propstat.properties, propname);
				if(pos < 0)
					pos = -pos - 1;
			}
			propstat.properties.add(pos, propname);
		} else ++propstat.typeCount;
	
	}
	bufferedReader.close();
	allprops = null;
	return instProps;
}

// Cut negative numbers to zero
private static int cutneg(int a)
{
	return a >= 0 ? a : 0;
}

public static HashMap<String, Double> readDataSet2(String n3DataSet) throws IOException {
	// Instance (subject): InstPropsStat
	TreeMap<String, InstPropsStat> mapInstanceProperties = loadInstanceProperties(n3DataSet);
	
	// Third HashMap including the Property name from the First MapTree(properties) and totalNumber of types that it in GT [DBpedia]
	HashMap<String, Integer> propertiesTypesNum = new HashMap<String, Integer>(properties.size(), 1);
	int ntypesGT = 0;
	Iterator mapIt = properties.entrySet().iterator();
	final int linSearchLim = 9;  // Max number of items when linear search is faster that binary search
	
	while(mapIt.hasNext()) {
		int value = 0;  // The number of types in the property
		Map.Entry<String, Property> entry = (Entry<String, Property>) mapIt.next();
		final String propname = entry.getKey();
		// System.out.println(String.format(" ************* Property : %s **************", entry.getValue().key));
		Iterator mapInstPropIt = mapInstanceProperties.entrySet().iterator();
		
		while(mapInstPropIt.hasNext()) {
			Map.Entry<String, InstPropsStat> instPropsEntry = (Entry<String, InstPropsStat>) mapInstPropIt.next();
			InstPropsStat propstat = instPropsEntry.getValue();
			if (propstat == null || propstat.properties == null)
				continue;
			if(propstat.properties.size() <= linSearchLim ? propstat.properties.contains(propname)
			: propstat.properties.get(cutneg(Collections.binarySearch(propstat.properties, propname))) == propname) {
				value += propstat.typeCount;
				//if (nt>0) System.out.println(String.format("%s : %d", instPropsEntry.getKey(), nt));
			}
		}
		//System.out.println("" +value) ;
		propertiesTypesNum.put(propname, value);
		ntypesGT += value;
		//mapIt.remove();
	}
	
	mapInstanceProperties.clear();
	
	//******************************************************************PropertyWeighCalculation********************************************************
	HashMap<String, Double> weightPerProperty = new HashMap<String, Double>(properties.size(), 1);
	double propertyWeight = 0;
	int foundProps = 0;
	ArrayList<String> notFoundProps = new ArrayList<String>();

	for (Property prop: properties.values()) {
		final double  propTypesNum = propertiesTypesNum.get(prop.name);
		if(propTypesNum != 0) {
			foundProps++;
			final double occurPropi= prop.occurances;
			// Note: ntypesGT+1 to avoid 0, resulting values E (0, ~64-500), typically ~1 for almost full match
			// Note: it would be beneficial to omit equivalent types (having the same members) before applying this formula
			propertyWeight = (1./ntypesGT - Math.log(propTypesNum/ntypesGT));
				// The more seldom property, the more it is indicative
				//* Math.sqrt(1./occurPropi);
			weightPerProperty.put(prop.name, propertyWeight);
		} else notFoundProps.add(prop.name);
	}
	double wmed = 1.;
	if(!weightPerProperty.isEmpty()) {
		ArrayList<Double> propWeights = weightPerProperty.values().stream()
			.sorted().collect(Collectors.toCollection(ArrayList::new));
		wmed = propWeights.get(propWeights.size() / 2);
		// Trace assigned property weights
		System.out.println("readDataSet2(), " + propWeights.size() + " pweights [" + propWeights.get(0)
			 + ", " + propWeights.get(propWeights.size()-1) + "], mean: " + wmed);
	}
	//// Find the median and normalize to the median
	//Collections.sort(propWeights);  // Note: even for propTypesNum/(ntypesGT+1) -> 0  wmax < 64
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

		TreeSet<String> instance1Properties = instancePropertiesMap.get(instance1).propertySet;
		TreeSet<String> instance2Properties = instancePropertiesMap.get(instance2).propertySet;
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
			Double weight = weightsForEachProperty.get(prop1);
			if(weight == null)
				continue;
			weight *= weight;
			inst1TotWeight += weight;
			if(instance2Properties.contains(prop1))
				powerCommon += weight;
		}
		inst1TotWeight = Math.sqrt(inst1TotWeight);

		for(String prop2: instance2Properties) {
			Double weight = weightsForEachProperty.get(prop2);
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
	Set<String> instances = instancePropertiesMap.keySet();
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
