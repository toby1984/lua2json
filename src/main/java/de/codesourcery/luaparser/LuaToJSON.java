package de.codesourcery.luaparser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import de.codesourcery.luaparser.antlr.LuaLexer;
import de.codesourcery.luaparser.antlr.LuaParser;
import de.codesourcery.luaparser.antlr.LuaParser.ExpContext;
import de.codesourcery.luaparser.antlr.LuaParser.FieldContext;
import de.codesourcery.luaparser.antlr.LuaParser.FieldlistContext;
import de.codesourcery.luaparser.antlr.LuaParser.FieldsepContext;
import de.codesourcery.luaparser.antlr.LuaParser.FunctioncallContext;
import de.codesourcery.luaparser.antlr.LuaParser.NameAndArgsContext;
import de.codesourcery.luaparser.antlr.LuaParser.NumberContext;
import de.codesourcery.luaparser.antlr.LuaParser.OperatorMulDivModContext;
import de.codesourcery.luaparser.antlr.LuaParser.OperatorUnaryContext;
import de.codesourcery.luaparser.antlr.LuaParser.PrefixexpContext;
import de.codesourcery.luaparser.antlr.LuaParser.StringContext;
import de.codesourcery.luaparser.antlr.LuaParser.TableconstructorContext;

public class LuaToJSON
{
	private static final File OUTPUT_FILE = new File("/home/tobi/tmp/test.json");

	private static int itemNumber = 0;

	private static TableconstructorContext topLevelTable;

	private static Writer writer;

	public static String readFile(File file) throws IOException
	{
		final FileInputStream in = new FileInputStream(file);
		try {
			return readFile( in );
		} finally {
			try { in.close(); } catch(final Exception e) {}
		}
	}

	public static String readFile(InputStream input) throws IOException
	{
		final StringBuilder buffer = new StringBuilder();
		String line;
		final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
		while( (line=reader.readLine())!=null) {
			buffer.append(line).append("\n");
		}
		return buffer.toString();
	}

	public static void main(String[] args) throws IOException
	{
		writer = new BufferedWriter(new FileWriter( OUTPUT_FILE ) );

		final File f = new File("/home/tobi/Downloads/tmp/factorio/data/base/prototypes/recipe/recipe.lua");
		final String input = readFile( LuaToJSON.class.getResourceAsStream("test.lua") );

		// Get our lexer
		final LuaLexer lexer = new LuaLexer(new ANTLRInputStream( input ) );

		// Get a list of matched tokens
		final CommonTokenStream tokens = new CommonTokenStream(lexer);

		// Pass the tokens to the parser
		final LuaParser parser = new LuaParser(tokens);

		final LuaParser.ChunkContext context = parser.chunk();

		final NodeSearcher funcSearcher = new NodeSearcher( tree -> {
			if ( tree instanceof FunctioncallContext)
			{
				if ( isMonitoredMethod((FunctioncallContext) tree) ) {
					return true;
				}
			}
			return false;
		});
		visitPreOrder( context , funcSearcher );

		System.out.println("Found: "+funcSearcher.matches+" data:extend() calls");

		writer.append("{");

		funcSearcher.forEach( (funcContext,lastItem) ->
		{
			final FunctioncallContext match = (FunctioncallContext) funcContext;

			final TableconstructorContext child = (TableconstructorContext) match.getChild(1) // NameAndArgsContext
					.getChild(2) // ArgsContext
					.getChild(1) // ExpListContext
					.getChild(0) // ExpContext
					.getChild(0); // TableconstructorContext

			topLevelTable = child;

			String text = "";
			text += printValue( child , true );

			try {
				writer.append( text );
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
			System.out.println("TRANSLATED: "+text);
			System.out.println("======");
		});
		writer.append(" }");

		System.out.println("File closed");
		writer.flush();
		writer.close();

		final String json = readFile( OUTPUT_FILE );

		final JSONObject object = new JSONObject( json );
		parseRecipes(object);
	}

	protected static final class ItemAndAmount
	{
		public final Item item;
		public final double amount;
		public ItemAndAmount(Item item,double amount) {
			this.item = item;
			this.amount = amount;
		}

		@Override
		public String toString() {
			return item.name+" x "+amount;
		}
	}

	protected static final class Item
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

	private static void parseRecipes(JSONObject object) throws IOException
	{

		final Map<String,Item> itemsByName = new HashMap<>();


		System.out.println("Got "+object.getClass());
		for ( final String key : object.keySet() )
		{
			final JSONObject recipe = object.getJSONObject(key);
			final String name = recipe.getString("name");
			final JSONArray requirements= recipe.getJSONArray("ingredients");
			final int len = requirements.length();
			System.out.println("Got "+name);

			Item item = itemsByName.get(name);
			if ( item == null ) {
				item = new Item(name);
				itemsByName.put(name,item);
			}

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

				Item reqItem  = itemsByName.get(reqItemName);
				if ( reqItem == null ) {
					reqItem = new Item(reqItemName);
					itemsByName.put(reqItemName,reqItem);
				}
				item.requirements.add( new ItemAndAmount( reqItem , count ) );
				System.out.println("requires: "+count+" x "+reqItemName);
			}
		}
		printDot( itemsByName );
	}

	private static void printDot(Map<String,Item> items) throws IOException {

		/*
		 * strict graph {
  a -- b
  a -- b
  b -- a [color=blue]
}
		 */

		final PrintWriter writer = new PrintWriter(new FileWriter("/tmp/test.dot",false));
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
	}

	private static String genItemName() {
		final String result = "item_"+itemNumber;
		itemNumber++;
		return result;
	}

	private static String printValue(ParseTree value)
	{
		if ( value instanceof ExpContext) {
			return printValue( (ExpContext) value);
		}
		if ( value instanceof StringContext ) {
			return value.getText();
		}
		if ( value instanceof NumberContext ) {
			return value.getText();
		}
		if ( value instanceof TableconstructorContext) {
			return printValue((TableconstructorContext) value , false );
		}
		if ( value instanceof PrefixexpContext) {
			return "\""+value.getText()+"\"";
		}
		if ( value instanceof TerminalNodeImpl) {
			return "\""+value.getText()+"\"";
		}
		throw new RuntimeException("Unhandled value class: "+value.getClass().getName()+" = "+value.getParent().getText());
	}

	private static String printValue(ExpContext value)
	{
		if ( value.getChild(0) instanceof TableconstructorContext) {
			return printValue( (TableconstructorContext) value.getChild(0) , false );
		}
		if ( value.getChild(0) instanceof PrefixexpContext) {
			return printValue( value.getChild(0) );
		}
		return evaluate(value).toString();
	}

	private static Object evaluate(ParseTree tree) {
		if ( tree instanceof ExpContext) {
			return evaluate( (ExpContext) tree);
		}
		if ( isTerminal( tree ) ) {
			return evaluateTerminal(tree);
		}
		throw new RuntimeException("Don't know how to evaluate: "+tree.getClass().getName()+" >"+tree.getText()+"<");
	}

	private static Object evaluate(ExpContext value)
	{
		final ParseTree child0 = value.getChildCount() > 0 ? value.getChild(0):null;
		final ParseTree child1 = value.getChildCount() >= 1 ? value.getChild(1):null;
		final ParseTree child2 = value.getChildCount() >= 2 ? value.getChild(2):null;

		if ( child0 instanceof OperatorUnaryContext ) {
			return "-"+evaluate(child1);
		}

		if ( child1 == null && child2 == null ) {
			return evaluate(child0);
		}

		System.err.println("Child_0: "+(child0 == null ? "<not set>" : child0.getText()+" ("+child0.getClass().getName()));
		System.err.println("Child_1: "+(child1 == null ? "<not set>" : child1.getText()+" ("+child1.getClass().getName()));
		System.err.println("Child_2: "+(child2 == null ? "<not set>" : child2.getText()+" ("+child2.getClass().getName()));

		if ( child1 instanceof OperatorMulDivModContext)
		{
			final String op = child1.getChild(0).getText();

			final Double value0 = (Double) evaluate( child0 );
			final Double value1 = (Double) evaluate( child2 );
			switch(op) {
				case "/":
					return value0.doubleValue() / value1.doubleValue();
				case "*":
					return value0.doubleValue() * value1.doubleValue();
			}
			System.out.println("Unhandled operator: "+op);
		}

		System.err.println("Unhandled expression: "+value.getText());
		throw new RuntimeException("Unhandled expression");
	}

	private static boolean isTerminal(ParseTree tree)
	{
		if ( tree instanceof TerminalNodeImpl) {
			final String value = ((TerminalNodeImpl) tree).symbol.getText();
			if ( "true".equals( value ) ) {
				return true;
			}
			if ( "false".equals( value ) ) {
				return true;
			}
			return false;
		}
		return tree instanceof StringContext || tree instanceof NumberContext;
	}

	private static Object evaluateTerminal(ParseTree tree)
	{
		if ( tree instanceof StringContext )
		{
			return tree.getText();

		} else if ( tree instanceof NumberContext )
		{
			return Double.parseDouble( tree.getText() );
		}
		if ( tree instanceof TerminalNodeImpl) {
			final String value = ((TerminalNodeImpl) tree).symbol.getText();
			if ( "true".equals( value ) ) {
				return Boolean.TRUE;
			}
			if ( "false".equals( value ) ) {
				return Boolean.FALSE;
			}
		}
		throw new RuntimeException("Unhandled terminal: "+tree.getClass().getName());
	}

	private static String printValue(TableconstructorContext ctx,boolean isTopLevel)
	{
		if ( ctx.getChildCount() == 2 ) {
			return "{}";
		}
		final FieldlistContext value = (FieldlistContext) ctx.getChild(1);
		if ( isRecord( value ) ) {
			return "{ "+printValue(value)+" }";
		}
		if ( ctx == topLevelTable ) {
			return printValue(value);
		}
		return "[ "+printValue(value)+" ]";
	}

	private static boolean isRecord(FieldlistContext ctx) {
		for ( int len = ctx.getChildCount() , i = 0 ; i < len ; i++ ) {
			final ParseTree tree = ctx.getChild(i);
			if ( tree instanceof FieldContext) {
				final FieldContext fieldCtx = (FieldContext) tree;
				if ( fieldCtx.getChildCount() == 3 && fieldCtx.getChild(2) instanceof ExpContext)
				{
					return true;
				}
				return false;
			}
		}
		return false;
	}

	private static String printValue(FieldlistContext ctx)
	{
		final StringBuilder buffer = new StringBuilder();
		for ( int len = ctx.getChildCount() , i = 0 ; i < len ; i++ ) {
			final ParseTree tree = ctx.getChild(i);
			if ( tree instanceof FieldContext) {
				final FieldContext fieldCtx = (FieldContext) tree;
				if ( fieldCtx.getChildCount() == 3 && fieldCtx.getChild(2) instanceof ExpContext)
				{
					buffer.append( printValue( fieldCtx.getChild(0) ) );
					buffer.append(" : ");
					buffer.append( printValue( fieldCtx.getChild(2) ) );
				} else {
					if ( isImmediateChildOfTopLevel( tree.getChild(0) ) ) {
						buffer.append("\"").append( genItemName() ).append("\": ");
					}
					buffer.append( printValue( tree.getChild(0) ) );
				}
			} else if ( tree instanceof FieldsepContext)
			{
				if ( ! isLastChildOfParent(tree ) ) {
					buffer.append(" , ");
				}
			} else {
				throw new RuntimeException("Unhandled node: "+tree.getClass().getName());
			}
		}
		return buffer.toString();
	}

	private static boolean isImmediateChildOfTopLevel(ParseTree tree) {

		int depth = 0;
		ParseTree current = tree;
		while ( current != topLevelTable && current != null ) {
			current = current.getParent();
			depth++;
		}
		return depth <= 3;
	}

	private static boolean isLastChildOfParent(ParseTree node) {
		final int idx = getIndexOf(node.getParent(),node);
		return idx == node.getParent().getChildCount()-1;
	}

	protected static int getIndexOf(ParseTree parent,ParseTree child) {
		for ( int i = 0 ; i < parent.getChildCount() ; i++ ) {
			if ( parent.getChild( i ) == child ) {
				return i;
			}
		}
		throw new RuntimeException(child+" is no child of "+parent);
	}

	protected static final class NodeSearcher implements Predicate<ParseTree> {

		public List<ParseTree> matches = new ArrayList<>();

		private final Predicate<ParseTree> predicate;
		public NodeSearcher(Predicate<ParseTree> predicate) {
			this.predicate = predicate;
		}

		public void forEach(BiConsumer<ParseTree,Boolean> c)
		{
			final int len = matches.size();
			for ( int i = 0 ; i < len ; i++ ) {
				c.accept( matches.get(i) ,  (i+1) >= len );
			}
		}

		@Override
		public boolean test(ParseTree t)
		{
			if ( predicate.test(t) ) {
				matches.add( t );
			}
			return true;
		}
	}

	public static boolean isMonitoredMethod(FunctioncallContext ctx) {
		final String name = getFunctionName(ctx);
		if ( "extend".equals( name ) ) {
			return true;
		}
		return false;
	}

	private static String getFunctionName(FunctioncallContext ctx) {
		final List<NameAndArgsContext> args = ctx.nameAndArgs();
		if ( args.size()>0 ) {
			final NameAndArgsContext args0 = ctx.nameAndArgs(0);
			return args0.NAME() != null ? args0.NAME().getText() : null;
		}
		return null;
	}

	private static boolean visitPreOrder(ParseTree node,Predicate<ParseTree> visitor)
	{
		if ( ! visitor.test( node ) ) {
			return false;
		}
		final int childCount = node.getChildCount();
		for ( int i = 0 ; i < childCount ; i++ ) {
			if ( ! visitPreOrder( node.getChild( i ) , visitor ) ) {
				return false;
			}
		}
		return true;
	}
}