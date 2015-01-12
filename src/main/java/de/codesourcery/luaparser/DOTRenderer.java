package de.codesourcery.luaparser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DOTRenderer
{

	private float maxEdgeWidth=3.0f;
	private float minEdgeWidth=1.0f;

	private static int getRequiredByItemsCount(Item currentItem , Collection<Item> items) {
		int requirementCount = 0;
		for ( final Item otherItem : items)
		{
			if ( otherItem.equals( currentItem ) ) {
				continue;
			}
			if ( otherItem.requires( currentItem ) ) {
				requirementCount += 1;
				requirementCount += getRequiredByItemsCount( otherItem , items );
			}
		}
		return requirementCount;
	}

	public String generateDot(Map<String,Item> items) throws IOException
	{
		final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		final PrintWriter writer = new PrintWriter(byteOut);
		writer.println("digraph {");
		writer.println("concentrate=true");
		writer.println("graph [splines=true overlap=false];");

		for ( final Item item : items.values() )
		{
			String label=item.name;
			if ( ! item.requirements.isEmpty() ) {
				label += "\\n\\n";
			}
			for (final Iterator<ItemAndAmount> it = item.requirements.iterator(); it.hasNext();)
			{
				final ItemAndAmount a = it.next();
				label += a.item.name+" x "+a.amount;
				if ( it.hasNext() ) {
					label += "\\n";
				}
			}
			writer.println( '"'+item.name+"\"" + " [shape=box,label=\""+label+"\"];");
		}

		// count the number of times each item is used as direct pre-requisite of another item
		final Map<Item,Integer> requirementCountByItem = new HashMap<>();
		for ( final Item currentItem : items.values() )
		{
			final int requirementCount = getRequiredByItemsCount( currentItem, items.values() );
			requirementCountByItem.put( currentItem, requirementCount );
		}

		// find item that is required by the least number of items
		Item leastRequired = null;
		int leastRequirementCount = 0;
		for ( final Item currentItem : items.values() )
		{
			final int requirementCount = requirementCountByItem.get(currentItem);
			if ( leastRequired == null || leastRequirementCount > requirementCount ) {
				leastRequired = currentItem;
				leastRequirementCount = requirementCount;
			}
		}

		// find item that is required by the largest number of items
		Item mostRequired = null;
		int mostRequirementCount = 0;
		for ( final Item currentItem : items.values() )
		{
			final int requirementCount = requirementCountByItem.get(currentItem);
			if ( mostRequired == null || mostRequirementCount < requirementCount ) {
				mostRequired = currentItem;
				mostRequirementCount = requirementCount;
			}
		}

		System.out.println("Least used item: "+leastRequired.name+"( required by "+leastRequirementCount+" items )");
		System.out.println("Most used item: "+mostRequired.name+"( required by "+mostRequirementCount+" items )");

		// output edges
		final String[] colors = {"black","red","green","blue","firebrick4","darkorchid","gold","cadetblue"};

		final float edgeWidthDelta = maxEdgeWidth - minEdgeWidth;
		final DecimalFormat DF = new DecimalFormat("#0.0##");
		for ( final Item item : items.values() )
		{
			for ( final ItemAndAmount req : item.requirements )
			{
				final int colorBits = (req.item.name.hashCode() & 7);
				final String color = colors[colorBits];

				final float usagePercentage = requirementCountByItem.get(req.item) / (float) mostRequirementCount;
				final float edgeWidth = minEdgeWidth + usagePercentage*edgeWidthDelta;

				writer.println( '"'+req.item.name+"\" -> \""+item.name+"\"[color=\""+color+"\",penwidth="+DF.format(edgeWidth)+"];" );
			}
		}
		writer.println("}");
		writer.close();
		return byteOut.toString();
	}
}