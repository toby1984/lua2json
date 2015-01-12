package de.codesourcery.luaparser;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class Item
{
	public final String name;
	public final List<ItemAndAmount> requirements = new ArrayList<>();

	public Item(String name) {
		this.name = name;
	}

	public boolean requires(Item otherItem)
	{
		for ( final ItemAndAmount req : requirements ) {
			if ( req.item.equals( otherItem ) ) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean equals(Object obj)
	{
		return obj instanceof Item && ((Item) obj).name.equals( this.name );
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		return "\""+name+"\" requires "+StringUtils.join(requirements," , " );
	}
}