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

	@Override
	public String toString() {
		return "\""+name+"\" requires "+StringUtils.join(requirements," , " );
	}
}