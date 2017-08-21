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

public class CosineSimilarityMatix {
public static final String typeProperty = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>";
public static TreeMap<String, Property> properties = new TreeMap<String, Property>();
public static TreeMap<String, InstanceProperties> instanceListPropertiesTreeMap = new TreeMap<String, InstanceProperties>();
public static HashMap<String, Double> weightsForEachProperty = null;
static int totalOccurances = 0; 

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
public static void readDataSet1(String N3DataSet, String idMapFName) throws IOException {
    BufferedReader bufferedReader = new BufferedReader(new FileReader(N3DataSet));
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
		final int id = instanceListPropertiesTreeMap.size();
		final String property = s[1];
		final boolean isTyped = property.contains(typeProperty);
		InstanceProperties instanceProperties = instanceListPropertiesTreeMap.get(instancemapKey);

		if (instanceProperties == null) {
			instanceProperties = new InstanceProperties(id);
			// Note: to have the isTyped flag the even empty properties should be added to the map
			instanceListPropertiesTreeMap.put(instancemapKey, instanceProperties);
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

//	System.out.println("List Properties for the instance <http://dbpedia.org/resource/BMW_Museum>=  "+instanceListPropertiesTreeMap.get("<http://dbpedia.org/resource/BMW_Museum>").propertySet);
//	System.out.println("The TreeMap Including properties and number of accurances in this case for <http://www.w3.org/2002/07/owl#sameAs>= "+map.get("<http://www.w3.org/2002/07/owl#sameAs>").occurances);
//	System.out.println(properties.get("<http://dbpedia.org/ontology/abstract>").propertyName);
//	System.out.println(properties.size());
	
}

static class InstPropsStat {
	public TreeSet<String>  properties = null;  // new TreeSet<String>()
	public int  typeCount = 0;
}
	
public static HashMap<String, Double> readDataSet2(String N3DataSet) throws IOException {
	
	//TreeMap key=instanceName and the value= List Of Properties of the key.
	TreeMap<String, InstPropsStat> mapInstanceProperties = new TreeMap<String, InstPropsStat>();
	FileReader fileReader = new FileReader(N3DataSet);
	BufferedReader bufferedReader = new BufferedReader(fileReader);
	int typeCount = 0;
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
			typeCount++;
		}
		
		final String instance = s[0];
		InstPropsStat propstat = mapInstanceProperties.get(instance);
		if (propstat == null) {
			propstat = new InstPropsStat();
			mapInstanceProperties.put(instance, propstat);
		}
		if (!isTyped) {
			if (propstat.properties == null)
				propstat.properties = new TreeSet<String>();
			propstat.properties.add(s[1]);
		} else ++propstat.typeCount;
	
	}
	bufferedReader.close();
	
	
	//Third HashMap including the Property name from the First MapTree(properties) and totalNumber of types that it in DBpedia***********************************************
	
	HashMap<String, Integer> propertiesTypesNum = new HashMap<String, Integer>(properties.size(), 1);
	int ntypesDBP = 0;
	Iterator mapIt = properties.entrySet().iterator();
	
	while(mapIt.hasNext()) {
		int value = 0;
		Map.Entry<String, Property> entry = (Entry<String, Property>) mapIt.next();
		// System.out.println(String.format(" ************* Property : %s **************", entry.getValue().key));
		Iterator mapInstPropIt = mapInstanceProperties.entrySet().iterator();
		
		while(mapInstPropIt.hasNext()) {
			Map.Entry<String, InstPropsStat> instPropsEntry = (Entry<String, InstPropsStat>) mapInstPropIt.next();
			InstPropsStat propstat = instPropsEntry.getValue();
			if (propstat != null && propstat.properties != null && propstat.properties.contains(entry.getKey())) {
				value += propstat.typeCount;
				//if (nt>0) System.out.println(String.format("%s : %d", instPropsEntry.getKey(), nt));
			}
		}
		//System.out.println("" +value) ;
		propertiesTypesNum.put(entry.getKey(), value);
		ntypesDBP += value;
		//mapIt.remove();
	}
	
	mapInstanceProperties.clear();
	
	//******************************************************************PropertyWeighCalculation********************************************************
	HashMap<String, Double> weightPerProperty = new HashMap<String, Double>(properties.size(), 1);
	double propertyWeight = 0, totalWeight = 0;
	int foundProps = 0;
	Iterator propIt = properties.entrySet().iterator();
	ArrayList<String> notFoundProps = new ArrayList<String>();
	// At least 10% of props usually belong to the typed instances
	ArrayList<Double> propWeights = new ArrayList<Double>(properties.size() / 10);

	while(propIt.hasNext()) {
		Map.Entry<String, Property> entry = (Entry<String, Property>) propIt.next();
		final String  propName = entry.getKey();
		final Integer  propsNum = propertiesTypesNum.get(propName);
		if(propsNum != 0) {
			foundProps++;
			final double no_type_Propi = propsNum;
			final double no_occerances_Propi= entry.getValue().occurances;
			propertyWeight = (-(Math.log(no_type_Propi/(ntypesDBP+1)))/(Math.log(2)))*(Math.sqrt(no_occerances_Propi/totalOccurances));

			totalWeight += propertyWeight;
			weightPerProperty.put(propName, propertyWeight);
			
			propWeights.add(propertyWeight);
		} else notFoundProps.add(propName);
	}
	Collections.sort(propWeights);
	//Calculating the Median
	double median = !propWeights.isEmpty() ? propWeights.get(propWeights.size() / 2) : 1;
	
	for (String prop: notFoundProps)
		weightPerProperty.put(prop, median);
	
	return weightPerProperty;
}
		
//*********************************************Calculating Cosin Similarity****************************************************************
 public static double similarity(String instance1, String instance2) {
		double instance1TotalWeight1 = 0;
		double instance1TotalWeight2 = 0;

		double powerWeight1=0;
		double powerWeight2=0;
		double powerCommon =0;
		TreeSet<String> instance1Properties= instanceListPropertiesTreeMap.get(instance1).propertySet;
		TreeSet<String> instance2Properties= instanceListPropertiesTreeMap.get(instance2).propertySet;
		int sizeListProperty1=instance1Properties.size();
		int sizeListProperty2=instance2Properties.size();

		if (sizeListProperty1>sizeListProperty2) {
			TreeSet<String> tempTreeSet = instance1Properties;
			
			instance1Properties = instance2Properties;
			instance2Properties = tempTreeSet;
			//tempTreeSet.clear();
		}
		
		String lastInstance1Prop;
		if((instance1Properties.isEmpty()) && (instance2Properties.isEmpty()))
			return 1;

		for(String prop1: instance1Properties) {
			double weight = 0;
			if (weightsForEachProperty.get(prop1) != null)
				weight= weightsForEachProperty.get(prop1);
			powerWeight1 += weight*weight;

			boolean found = false;
			for(String prop2: instance2Properties) {
				if (prop2.contains(prop1)) {
					found = true;
					break;
				}
			}

			if (found)
				powerCommon += weight*weight;
		}

		instance1TotalWeight1 = Math.sqrt(powerWeight1);

		for(String prop2: instance2Properties) {
			if (weightsForEachProperty.get(prop2) != null) {
				final double weight = weightsForEachProperty.get(prop2);
				powerWeight2 += weight*weight;
			}
		}
		instance1TotalWeight2= Math.sqrt(powerWeight2);
		//   System.out.print(powerlist);
		final double similarity = powerCommon / (instance1TotalWeight1 * instance1TotalWeight2);

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
	Set<String> instances = instanceListPropertiesTreeMap.keySet();
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
