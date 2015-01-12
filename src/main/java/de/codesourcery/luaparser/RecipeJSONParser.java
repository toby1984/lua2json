package de.codesourcery.luaparser;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class RecipeJSONParser
{
	private static final boolean DEBUG = false;

	private final Map<String,Item> itemsByName = new HashMap<>();

	public void addJSON(String json) {
		parseRecipes( LenientJSONParser.parse( json ) );
	}

	public Map<String,Item> getRecipes() {
		return itemsByName;
	}

	private Item getOrCreateItem(String name) {
		Item item = itemsByName.get(name);
		if ( item == null ) {
			item = new Item(name);
			itemsByName.put(name,item);
		}
		return item;
	}

	private void parseRecipes(JSONObject object)
	{
		if ( DEBUG ) { System.out.println("Got "+object.getClass()); }

		for ( final String key : object.keySet() )
		{
			final JSONObject recipe = object.getJSONObject(key);
			final String name = recipe.getString("name");
			final JSONArray requirements= recipe.getJSONArray("ingredients");
			final int len = requirements.length();

			if ( DEBUG ) { System.out.println("Got "+name); }

			final Item item = getOrCreateItem(name);

			for( int i = 0 ; i < len ; i++ )
			{
				final Object listEntry = requirements.get(i);

				final String reqItemName;
				final Double count;
				if ( listEntry instanceof JSONArray) {
					final JSONArray itemAndCount = (JSONArray) listEntry;
					reqItemName = itemAndCount.getString(0);
					count = itemAndCount.getDouble( 1 );
				} else {
					reqItemName = ((JSONObject) listEntry).getString("name");
					count = ((JSONObject) listEntry).getDouble("amount");
				}

				final Item reqItem  = getOrCreateItem(reqItemName);
				item.requirements.add( new ItemAndAmount( reqItem , count ) );
				if ( DEBUG ) { System.out.println("requires: "+count+" x "+reqItemName); }
			}
		}
	}
}
