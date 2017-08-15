package info.exascale.SimWeighted;


public class Property {
	public int propertyId;
	
	public String propertyName;
	public String key;
	public int noOfTypes;
	public int occurances; 
	
	public Property(int occurances, String name)
	{
		//this.propertyId = id;
		this.key=name;
		this.propertyName = name;
		//noOfTypes is for DBpedia Dataset and occurances for the evaluation dataset
		this.noOfTypes = 0;
		this.occurances= 1;
	}
	public String getKey() {
        return key;
    }

}