package info.exascale.SimWeighted;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class InstancePropertiesIsTyped {
	public int id;
	public boolean isTyped;
	
	public TreeSet<String> propertySet=new TreeSet<String>();
	
	 
	
	public InstancePropertiesIsTyped(String properties, boolean typevalue,int id)
	{
		//this.propertyId = id;
		
		if (properties!= "")propertySet.add(properties);
		this.isTyped = typevalue;
		this.id=id;
	}
	

}