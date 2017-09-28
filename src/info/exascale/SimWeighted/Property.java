package info.exascale.SimWeighted;

import java.util.ArrayList;

// The number of property occurrences in the type
class TypePropOcr {
	String  type;
	int  propocr;
	
	TypePropOcr(String typename, int propocr) {
		this.type = typename;
		this.propocr = propocr;
	}
}

public class Property {
	//public String name;
	//public int typesNum;
	public int  occurances;
	// Types having the same instance in which the property occurs with the number of property occurrences.
	// Types are ordered by the type name.
	ArrayList<TypePropOcr>  types;  // Note: applicable only for the [semi-]supervised type inference
	
	public Property(String name)
	{
		this.occurances = 1;
		this.types = new ArrayList<TypePropOcr>();
	}
}
