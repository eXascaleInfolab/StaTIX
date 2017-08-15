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

	private static final String Static = null;
	static TreeMap<String, Property> map = new TreeMap<String, Property>();
	static TreeMap<String, InstancePropertiesIsTyped> instanceListPropertiesTreeMap = new TreeMap<String, InstancePropertiesIsTyped>();
	static HashMap<String, Double> WeightsForEachProperty = new HashMap<String, Double>();
	static List<String> listOfInstances = new ArrayList<String>();
	static int noTotalOccurances = 0; 
	private static final boolean  tracingOn = false;  // Enable tracing
	
	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new DefaultParser();
		Options options = new Options();
		options.addOption("g", "ground-truth", true, "The ground-truth dataset");
		options.addOption("o", "output", true, "Output file");
		options.addOption("a", "all-scales", false, "Fine-grained type inference on all scales besides the macro scale");
		options.addOption("h", "help", false, "Show usage");
		
		HelpFormatter formatter = new HelpFormatter();
		String[] argsOpt = new String[]{"args"};
		final String appusage = main.class.getCanonicalName() + " [OPTIONS...] <InputDataPath>";
		
		try {
			CommandLine cmd = parser.parse(options, args);
			// Check for the help option
			if(cmd.hasOption("h")) {
				formatter.printHelp(appusage, options);
				System.exit(0);
			}
			
			String[] files = cmd.getArgs();
			if(files.length != 1)
				throw new IllegalArgumentException("The argument is invalid");

			if(cmd.hasOption("g")) {
				String gtDataset = cmd.getOptionValue("g");
				//System.out.println("Ground-truth file= "+gtDataset);
				LoadDatasets(files[0], gtDataset);
			}
			else {
				//System.out.println("Input file= "+args[0]);
				LoadDataset(files[0]);
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
			
			// Perform type inference
			Statix(outpfile, cmd.hasOption("a"));
		}
		catch (ParseException | IllegalArgumentException e) {
			e.printStackTrace();
			formatter.printHelp(appusage, options);
			System.exit(1);
		}
	}
	
	public static HashMap<String, Double> PropertyWeights (String file1, String file2) throws IOException {
		readDataSet1(file1);
		
		return readDataSet2(file2);
	}
	
	
	//In case that only input file is givven to the app (without Ground-TRuth dataset)all the property weights will be set = 1
	public static void LoadDataset(String N3DataSet) throws IOException {
		readDataSet1(N3DataSet);
		
		HashMap<String, Double> weightPerProperty = new HashMap<String, Double>();
		Iterator propIt = map.entrySet().iterator();
		while(propIt.hasNext()) {
			Map.Entry<String, Property> entry = (Entry<String, Property>) propIt.next();

			try {
				weightPerProperty.put(entry.getKey(),(double)1);
			}
			catch (Exception exeption) {
				throw exeption;
			}
		}	
		//System.out.println("Property Weight for <http://www.w3.org/2002/07/owl#sameAs> = " + weightPerProperty.get("<http://www.w3.org/2002/07/owl#sameAs>"));
		WeightsForEachProperty = weightPerProperty;
	}
	
	//function to read the Input Dataset and put the values in map and instanceListProperties TreeMaps
	public static void readDataSet1(String N3DataSet) throws IOException {
		FileReader fileReader = new FileReader(N3DataSet);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		//List<String> lines = new ArrayList<String>();
		String line = null;
		int id=-1;
		while ((line = bufferedReader.readLine()) != null) {
			//lines.add(line);
			String[] s = line.split(" ");
			if (s.length<3) continue;
			String instancemapKey = s[0];
			if (!listOfInstances.contains(instancemapKey)) listOfInstances.add(instancemapKey);
			if (s[1].contains("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")) {
				InstancePropertiesIsTyped instanceProperties = null;
				if (instanceListPropertiesTreeMap.get(instancemapKey)== null) {
					instanceProperties = new InstancePropertiesIsTyped("", true,++id);
				}
				else if (instanceListPropertiesTreeMap.get(instancemapKey)!=null) {
					instanceProperties = instanceListPropertiesTreeMap.get(instancemapKey);
					instanceProperties.isTyped = true;
				}
				instanceListPropertiesTreeMap.put(instancemapKey,instanceProperties);
				continue;
			}
			
			noTotalOccurances++;
			//insert to the instanceListProperties Treemap
			InstancePropertiesIsTyped instanceProperties = null;
			
			if (instanceListPropertiesTreeMap.get(instancemapKey)== null) {
				instanceProperties = new InstancePropertiesIsTyped(s[1], false, ++id);
			}
			else if (instanceListPropertiesTreeMap.get(instancemapKey)!=null) {
				instanceProperties = instanceListPropertiesTreeMap.get(instancemapKey);
				instanceProperties.propertySet.add(s[1]);
			}
			instanceListPropertiesTreeMap.put(instancemapKey,instanceProperties);

			//********************insert to the map Treemap
			String mapkey = s[1];
			//check if the propertyname was in out TreeMap before or not
			if (map.get(mapkey)== null) {
				Property entryProperty = new Property(1, mapkey);
				map.put(entryProperty.getKey(),entryProperty);
			}
			else {
				Property value=map.get(mapkey);
				value.occurances++ ;
				map.put(mapkey,value);
			}    	
			
			
		}
		bufferedReader.close();
		//setBitwise for id
			Iterator insIt = instanceListPropertiesTreeMap.entrySet().iterator();
			
			while(insIt.hasNext()) {
				int value = 0;
				
				Map.Entry<String, InstancePropertiesIsTyped> entry = (Entry<String, InstancePropertiesIsTyped>) insIt.next();
				if (entry.getValue().isTyped == false) {
					value= entry.getValue().id;
					int mask = 1 << 31;
					value |= mask;
					entry.getValue().id = value;
					System.out.println("mask= "+mask);
					System.out.println("value= "+entry.getKey());

					System.out.println("value= "+entry.getValue().id);
				}
			}
		
	}
	
	public static HashMap<String, Double> readDataSet2(String N3DataSet) throws IOException {
		
		//TreeMap key=instanceName and the value= List Of Properties of the key.
		TreeMap<String, List<String>> mapInstanceProperties = new TreeMap<String, List<String>>();
		//TreeMap key=instanceName and the value= number of the types  that instance has
		TreeMap<String, Integer> mapInstanceNoOfTypes = new TreeMap<String, Integer>();
		FileReader fileReader = new FileReader(N3DataSet);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		//List<String> lines = new ArrayList<String>();
		int typeCount = 0;
		String line = null;
		while ((line = bufferedReader.readLine()) != null) {
			//lines.add(line);
			//if (typeCount % 100 ==0)
			//		System.out.println(typeCount);
			String[] s = line.split(" ");
			
			if (s.length<3) continue;
			if (s[1].contains("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>")) typeCount++;
			
			String mapkey = s[0];
			mapInstanceNoOfTypes.put(mapkey, mapInstanceNoOfTypes.get(mapkey)==null?0:(mapInstanceNoOfTypes.get(mapkey)));		

			List<String> lisOfPropertiesPerInstance = new ArrayList<String>();

			if (mapInstanceProperties.get(mapkey)!= null)
				lisOfPropertiesPerInstance=mapInstanceProperties.get(mapkey);

			if (s[1].contains("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>"))
				mapInstanceNoOfTypes.put(mapkey, mapInstanceNoOfTypes.get(mapkey)==null?1:(mapInstanceNoOfTypes.get(mapkey)+1));		
			else if (map.get(s[1])!=null && !lisOfPropertiesPerInstance.contains(s[1]))
				lisOfPropertiesPerInstance.add(s[1]);
			mapInstanceProperties.put(mapkey,lisOfPropertiesPerInstance);
		
		}
		bufferedReader.close();
		
		
		//Third HashMap including the Property name from the First MapTree(map) and totalNumber of types that it in DBpedia***********************************************
		
		HashMap<String, Integer> noTypesPerProperties = new HashMap<String, Integer>(map.size());
		
		int ntypesDBP = 0;
		Iterator mapIt = map.entrySet().iterator();
		
		while(mapIt.hasNext())
		{
			int value = 0;
			Map.Entry<String, Property> entry = (Entry<String, Property>) mapIt.next();
			// System.out.println(String.format(" ************* Property : %s **************", entry.getValue().key));
			Iterator mapInstPropIt = mapInstanceProperties.entrySet().iterator();
			
			try {
				while(mapInstPropIt.hasNext()) {
					Map.Entry<String, List<String>> instPropsEntry = (Entry<String, List<String>>) mapInstPropIt.next();
					try {
						if (instPropsEntry.getValue()!=null && instPropsEntry.getValue().contains(entry.getKey())) {
							int nt = mapInstanceNoOfTypes.get(instPropsEntry.getKey())==null
								? 0 : (mapInstanceNoOfTypes.get(instPropsEntry.getKey())==null
									? 0 : mapInstanceNoOfTypes.get(instPropsEntry.getKey()));
							value += nt;
							//if (nt>0) System.out.println(String.format("%s : %d", instPropsEntry.getKey(), nt));
						}
					}
					catch (Exception ex) {
						throw ex;
					}
					//mapInstPropIt.remove();
				}
				//System.out.println("" +value) ;
				noTypesPerProperties.put(entry.getKey(), value);
				ntypesDBP += value;
				//mapIt.remove();
			}
			catch (Exception exeption) {
				throw exeption;
			}
		}
		
		mapInstanceProperties.clear();
		mapInstanceNoOfTypes.clear();
		
		//******************************************************************PropertyWeighCalculation********************************************************
		HashMap<String, Double> weightPerProperty = new HashMap<String, Double>();
		double propertyWeight = 0, totalWeight = 0;
		int foundProps = 0;
		Iterator propIt = map.entrySet().iterator();
		ArrayList<String> notFoundProps = new ArrayList<String>();
		ArrayList<Double> properties = new ArrayList<Double>();

		while(propIt.hasNext()) {
			Map.Entry<String, Property> entry = (Entry<String, Property>) propIt.next();
			try {
				if(noTypesPerProperties.get(entry.getKey())!=0) {
					foundProps++;
					double no_type_Propi =noTypesPerProperties.get(entry.getKey());
					double no_occerances_Propi= entry.getValue().occurances;
					propertyWeight = (-(Math.log(no_type_Propi/(ntypesDBP+1)))/(Math.log(2)))*(Math.sqrt(no_occerances_Propi/noTotalOccurances));

					totalWeight += propertyWeight;
					weightPerProperty.put(entry.getKey(), propertyWeight);
					
					properties.add(propertyWeight);
					Collections.sort(properties);
					
				} else notFoundProps.add(entry.getKey());
				

			}
			catch (Exception exeption) {
				throw exeption;
			}
		}
		//Calculating the Median
		double median;
		int middle = properties.size()/2;
		if (properties.size()%2 == 1)
			median= properties.get(middle);
		else
			median= (properties.get(middle-1) + properties.get(middle)) / 2.0;
		
		for (String prop: notFoundProps)
			weightPerProperty.put(prop, median);
		
		return weightPerProperty;
	}
	
		
	//*********************************************Calculating Cosin Similarity****************************************************************
	 public static double similarity(String instance1, String instance2) throws Exception {
			double instance1TotalWeight1 = 0;
			double instance1TotalWeight2 = 0;

			double powerWeight1=0;
			double powerWeight2=0;
			double powerCommon =0;
			TreeSet<String> instance1Properties= instanceListPropertiesTreeMap.get(instance1).propertySet;
			TreeSet<String> instance2Properties= instanceListPropertiesTreeMap.get(instance2).propertySet;
			int sizeListProperty1=instance1Properties.size();
			int sizeListProperty2=instance2Properties.size();
			ArrayList<Double> powerlist = new ArrayList<Double>();

			if (sizeListProperty1>sizeListProperty2) {
				TreeSet<String> tempTreeSet = instance1Properties;
				
				instance1Properties = instance2Properties;
				instance2Properties = tempTreeSet;
				//tempTreeSet.clear();
			}
			
			String lastInstance1Prop;
			try {
				if((instance1Properties.isEmpty())&& (instance2Properties.isEmpty()))
					return 1;

				for(String prop1: instance1Properties) {
					double weight = 0;
					if (WeightsForEachProperty.get(prop1)!=null)
					weight= WeightsForEachProperty.get(prop1);
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
					double weight = 0;
					if (WeightsForEachProperty.get(prop2)!=null)
					weight= WeightsForEachProperty.get(prop2);

					powerWeight2 += weight*weight;
				}
				instance1TotalWeight2= Math.sqrt(powerWeight2);
				//   System.out.print(powerlist);
				double similarity = (double) powerCommon / ((double) instance1TotalWeight1*instance1TotalWeight2);

				//  System.out.println("Results: "+instance1+" "+instance2+" "+powerCommon+" /{ "+instance1TotalWeight1+" * "+instance1TotalWeight2+" } ");
				if(tracingOn) {
					FileWriter fw = new FileWriter("./outputfile.txt");
					BufferedWriter output = new BufferedWriter(fw);
					output.write( "Results: "+instance1+" "+instance2+" "+powerCommon+" /{ "+instance1TotalWeight1+" * "+instance1TotalWeight2+" } ");
					output.flush();
				}
				return similarity;
			}
			catch(Exception ex) {
				throw ex;
			}
	 }
	
	public static void Statix(String outputPath, boolean fineGrained) throws Exception {
		System.err.println("Calling the clustering lib...");
		int n = instanceListPropertiesTreeMap.size();
		Graph gr= new Graph(n);
		InpLinks grInpLinks  = new InpLinks ();

		for (int i = 0; i < n; i++) {
			String instance1 = listOfInstances.get(i);
			long  sid = instanceListPropertiesTreeMap.get(instance1).id;  // Source node id
			//System.out.print(sid + "> ");
			for (int j = i; j < n; j++) {
				String instance2= listOfInstances.get(j);
				double  weight = similarity(instance1, instance2);
				long did = instanceListPropertiesTreeMap.get(instance2).id;
				//System.out.print(" " + did + ":" + weight);

				grInpLinks.add(new InpLink(did, (float)weight));
			}
			//System.out.println();
			gr.addNodeAndEdges(sid,grInpLinks);
			grInpLinks.clear();
		}
		grInpLinks = null;
		System.err.println("Input graph formed");
		//clustring and output
		OutputOptions outpopts = new OutputOptions();
		final short outpflag = (short)(fineGrained
			? 0x45  // ALLCLS | SIMPLE
			: 0x41);  // ROOT | SIMPLE
		outpopts.setClsfmt(outpflag);
		outpopts.setClsrstep(0.618f);
		outpopts.setClsfile(outputPath);
		outpopts.setFltMembers(true);

		System.err.println("Starting the hierarchy building");
		Hierarchy hr = gr.buildHierarchy();
		System.err.println("Starting the hierarchy output");
		hr.output(outpopts);
		System.err.println("The types inference is completed");
	}
	
	//Readlines Function is used to read the input file line by line 
	public static String[] readLines(String filename) throws IOException {
		FileReader fileReader = new FileReader(filename);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		List<String> lines = new ArrayList<String>();
		String line = null;
		while ((line = bufferedReader.readLine()) != null) {
			lines.add(line);
		}
		bufferedReader.close();
		return lines.toArray(new String[lines.size()]);
	}
		
	//This function first check if it is out put results from before and will delete them before running the app and then read the directory for input dataset
	public static void LoadDatasets(String dataPath, String dataPath2) throws Exception {
		readDataSet1(dataPath);
		WeightsForEachProperty = readDataSet2(dataPath2);
	}
}
