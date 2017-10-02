package info.exascale.statix;

import java.util.TreeSet;

public class InstanceProperties {
	public int id;  //!< Instance (subject) id
	public boolean isTyped = false;  //!< The instance is typped
	
	// Note: the set will be empty if the instance has only the #type properties, but this is a very rare usecase
	public TreeSet<String> properties = new TreeSet<String>();
	
	 
	public InstanceProperties(int id, String property)
	{
		if(property == null || property.isEmpty())
			throw new IllegalArgumentException("The property is empty for id: " + id);
		
		this.id = id;
		properties.add(property);
	}
	
	public InstanceProperties(int id)
	{
		this.id=id;
	}
}
