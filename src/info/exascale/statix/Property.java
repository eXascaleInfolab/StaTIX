package info.exascale.statix;

import java.util.ArrayList;

// The number of property occurrences in the type
class TypePropOcr implements Comparable<String> {
	String  type;
	int  propocr;
	
	TypePropOcr(String typename, int propocr) {
		this.type = typename;
		this.propocr = propocr;
	}
	
	TypePropOcr(String typename) {
		this.type = typename;
		this.propocr = 1;
	}
	
	public int compareTo(String typename) {
		return type.compareTo(typename);
	}
}

public class Property {
	//public String name;
	//public int typesNum;
	public int  occurrences;
	// Types having the same instance in which the property occurs with the number of property occurrences.
	// Types are ordered by the type name.
	// TODO: potentially move it to the inherited class to save space for the nonsupervised type inference
	ArrayList<TypePropOcr>  types;  // Note: applicable only for the [semi-]supervised type inference
	
	public Property()
	{
		this.occurrences = 1;
		this.types = null;
	}
}
