package info.exascale.SimWeighted;


public class Property {
	public String name;
	//public int typesNum;
	public int occurances; 
	
	public Property(String name)
	{
		this.name = name;
		////typesNum is for DBpedia Dataset and occurances for the evaluation dataset
		//this.typesNum = 0;
		this.occurances = 1;
	}
}
