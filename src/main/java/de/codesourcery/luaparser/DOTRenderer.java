package de.codesourcery.luaparser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;

public class DOTRenderer
{

	public String generateDot(Map<String,Item> items) throws IOException
	{
		final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		final PrintWriter writer = new PrintWriter(byteOut);
		writer.println("digraph {");

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

		final String[] colors = {"black","red","green","blue","firebrick4","darkorchid","gold","cadetblue"};
		for ( final Item item : items.values() )
		{
			for ( final ItemAndAmount req : item.requirements )
			{
				final int colorBits = (req.item.name.hashCode() & 7);
				final String color = colors[colorBits];
				writer.println( '"'+req.item.name+"\" -> \""+item.name+"\"[color=\""+color+"\"];" );
			}
		}
		writer.println("}");
		writer.close();
		return byteOut.toString();
	}
}