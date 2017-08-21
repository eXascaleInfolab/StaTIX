package info.exascale.SimWeighted;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class InstanceProperties {
	public int id;
	public boolean isTyped = false;
	private static final boolean isDbg = false;
	
	// Note: the set will be empty if the instance has only the #type properties, but this is a very rare usecase
	public TreeSet<String> propertySet = new TreeSet<String>();
	
	 
	public InstanceProperties(int id, String property) throws Exception
	{
		//this.propertyId = id;
		if(isDbg && (property == null || property.isEmpty()))
			throw new IllegalArgumentException("The property is blank for id: " + id);
		
		this.id=id;
		propertySet.add(property);
	}
	
	public InstanceProperties(int id)
	{
		this.id=id;
	}
}
