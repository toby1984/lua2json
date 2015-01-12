/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.luaparser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.json.JSONTokener;

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
	private int attributeNumber = 0;
	private TableconstructorContext topLevelTable;
	private Writer jsonWriter;
	private File luaInputFile;

	public LuaToJSON() {
	}

	public static void main(String[] args) throws Exception
	{
		args = new String[]{"/home/tgierke/apps/factorio/data/base/prototypes/entity/entities.lua"};

		for ( String file : args )
		{
			System.out.println("Parsing LUA file "+file+" ...");
			final String json = new LuaToJSON().getJSON( new File(file ) );
			final File jsonFile = new File(file+".json" );
			System.out.println("Writing JSON file "+jsonFile.getAbsolutePath()+" ...");
			try ( FileWriter writer = new FileWriter( jsonFile ) ) {
				writer.write( json );
			}
			JSONTokener tokener = new JSONTokener( json );
			try {
				JSONObject obj = LenientJSONParser.parse(tokener);
			}
			catch(Exception e)
			{
				final int errorOffset = getOffset(tokener);
				System.out.println("Error at offset "+errorOffset);
				System.out.println( toErrorString( json ,errorOffset , "HERE: "+e.getMessage() ));
				e.printStackTrace();
			}
		}
	}

    public static String toErrorString(String input,int errorPosition,String msg)
    {
            final String lines[] = input.split("\n");

            String line = lines[0];
            int currentLineStart = 0;
            int lineNo = 0;
            while ( lineNo < lines.length )
            {
                    line = lines[lineNo];
                    if ( currentLineStart+line.length()+1 >= errorPosition) {
                            break;
                    }
                    line = lines[lineNo++];
                    currentLineStart += line.length()+1;
            }

            final int offsetInLine = errorPosition - currentLineStart;
            final String indent = StringUtils.repeat(" ", offsetInLine );
            String result = "";
            for ( int start = lineNo-5 ; start < lineNo ; start++ ) {
                    if ( start >= 0 ) {
                            result += lines[start]+"\n";
                    }
            }
            return "\nLine "+(lineNo+1)+", col "+(offsetInLine+1)+", offset "+errorPosition+":\n"+result+line+"\n"+indent+"^ "+msg;
    }


	private static int getOffset(JSONTokener tokener) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

		for ( Field f : JSONTokener.class.getDeclaredFields() ) {
			if ( f.getName().equals("index") ) {
				f.setAccessible(true);
				return ((Number) f.get( tokener )).intValue();
			}
		}
		throw new RuntimeException("Internal error,failed to find field 'index' in JSONTokener");
	}

	public String getJSON(File luaFile) throws IOException {

		this.luaInputFile = luaFile;

		final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

		jsonWriter = new BufferedWriter( new PrintWriter(byteOut) );

		final String input = readFile( luaInputFile );

		final LuaLexer lexer = new LuaLexer(new ANTLRInputStream( input ) );

		final CommonTokenStream tokens = new CommonTokenStream(lexer);

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

		jsonWriter.append("{");

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
				jsonWriter.append( text );
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		});
		jsonWriter.append(" }");

		jsonWriter.close();

		return byteOut.toString();
	}

	private  String generateUniqueAttr() {
		final String result = "uniq_attr_"+attributeNumber;
		attributeNumber++;
		return result;
	}

	private  String printValue(ParseTree value)
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

	private  String printValue(ExpContext value)
	{
		if ( value.getChild(0) instanceof TableconstructorContext) {
			return printValue( (TableconstructorContext) value.getChild(0) , false );
		}
		if ( value.getChild(0) instanceof PrefixexpContext) {
			return printValue( value.getChild(0) );
		}
		return evaluate(value).toString();
	}

	private  Object evaluate(ParseTree tree) {
		if ( tree instanceof ExpContext) {
			return evaluate( (ExpContext) tree);
		}
		if ( isTerminal( tree ) ) {
			return evaluateTerminal(tree);
		}
		throw new RuntimeException("Don't know how to evaluate: "+tree.getClass().getName()+" >"+tree.getText()+"<");
	}

	private  Object evaluate(ExpContext value)
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

	private  boolean isTerminal(ParseTree tree)
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

	private  Object evaluateTerminal(ParseTree tree)
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

	private  String printValue(TableconstructorContext ctx,boolean isTopLevel)
	{
		if ( ctx.getChildCount() == 2 ) {
			return "{}";
		}
		final FieldlistContext value = (FieldlistContext) ctx.getChild(1);
		if ( containsKeyValue( value ) || containsMixedContent( value ) ) {
			return "{ "+printValue(value)+" }";
		}
		if ( ctx == topLevelTable ) {
			return printValue(value);
		}
		return "[ "+printValue(value)+" ]";
	}

	/**
	 * Check whether a field list contains { "a" : true , "b" : 1 } style records/objects.
	 *
	 * @param ctx
	 * @return true if at least one of the list entries is a
	 */
	private boolean containsKeyValue(FieldlistContext ctx)
	{
		for ( int len = ctx.getChildCount() , i = 0 ; i < len ; i++ )
		{
			final ParseTree tree = ctx.getChild(i);
			if ( tree instanceof FieldContext)
			{
				final FieldContext fieldCtx = (FieldContext) tree;
				if ( isKeyValue(fieldCtx) )
				{
					return true;
				}
				return false;
			}
		}
		return false;
	}

	private static boolean containsMixedContent(FieldlistContext ctx) {

		int arrayCount = 0;
		int recordCount = 0;

		for ( int len = ctx.getChildCount() , i = 0 ; i < len ; i++ )
		{
			final ParseTree tree = ctx.getChild(i);
			if ( tree instanceof FieldContext)
			{
				final FieldContext fieldCtx = (FieldContext) tree;
				if ( isKeyValue(fieldCtx) )
				{
					recordCount++;
				} else {
					arrayCount++;
				}
			}
		}
		return arrayCount > 0 && recordCount > 0;
	}

	private static boolean isKeyValue(FieldContext fieldCtx) {
		return fieldCtx.getChildCount() == 3 && fieldCtx.getChild(2) instanceof ExpContext;
	}

	private  String printValue(FieldlistContext ctx)
	{
		final StringBuilder buffer = new StringBuilder();
		for ( int len = ctx.getChildCount() , i = 0 ; i < len ; i++ ) {
			final ParseTree tree = ctx.getChild(i);
			if ( tree instanceof FieldContext) {
				final FieldContext fieldCtx = (FieldContext) tree;
				if ( isKeyValue(fieldCtx ) ) // key-value
				{

					/*
					 * JSON does not allow mixing key-value content in arrays
					 *
					 * "attribute" : [ { "type" : "output" , "position" : [ 0.0 , 2.0 ] } ] } , "off_when_no_fluid_recipe" : true ]
					 *
					 * so we check whether the list contains mixed data (objects & tuples) and insert
					 * artificial attributes as needed
					 */

					final boolean mixedContent = false; // containsMixedContent( ctx );

					if ( mixedContent ) {
						buffer.append( "\""+generateUniqueAttr()+"\" : {");
					}
					buffer.append( printValue( fieldCtx.getChild(0) ) );
					buffer.append(" : ");
					buffer.append( printValue( fieldCtx.getChild(2) ) );

					if ( mixedContent ) {
						buffer.append( " } ");
					}
				}
				else
				{
					// list that is a direct child of the top-level element => JSON output requires an attribute name here
					boolean generateAttribute = isImmediateChildOfTopLevel( tree.getChild(0) );

					if ( generateAttribute ) {
						buffer.append("\"").append( generateUniqueAttr() ).append("\": ");
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

	private TableconstructorContext findParentTableConstructor(ParseTree tree) {

		ParseTree current = tree;
		while ( current != null && !(current instanceof TableconstructorContext)){
			current = current.getParent();
		}
		return (TableconstructorContext) current;
	}

	private  boolean isImmediateChildOfTopLevel(ParseTree tree) {

		int depth = 0;
		ParseTree current = tree;
		while ( current != topLevelTable && current != null ) {
			current = current.getParent();
			depth++;
		}
		return depth <= 3;
	}

	private  boolean isLastChildOfParent(ParseTree node) {
		final int idx = getIndexOf(node.getParent(),node);
		return idx == node.getParent().getChildCount()-1;
	}

	protected  int getIndexOf(ParseTree parent,ParseTree child) {
		for ( int i = 0 ; i < parent.getChildCount() ; i++ ) {
			if ( parent.getChild( i ) == child ) {
				return i;
			}
		}
		throw new RuntimeException(child+" is no child of "+parent);
	}

	protected  final class NodeSearcher implements Predicate<ParseTree> {

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

	public  boolean isMonitoredMethod(FunctioncallContext ctx) {
		final String name = getFunctionName(ctx);
		if ( "extend".equals( name ) ) {
			return true;
		}
		return false;
	}

	private  String getFunctionName(FunctioncallContext ctx) {
		final List<NameAndArgsContext> args = ctx.nameAndArgs();
		if ( args.size()>0 ) {
			final NameAndArgsContext args0 = ctx.nameAndArgs(0);
			return args0.NAME() != null ? args0.NAME().getText() : null;
		}
		return null;
	}

	private  boolean visitPreOrder(ParseTree node,Predicate<ParseTree> visitor)
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

	public  String readFile(File file) throws IOException
	{
		try ( final FileInputStream in = new FileInputStream(file) ) {
			return readFile( in );
		}
	}

	public  String readFile(InputStream input) throws IOException
	{
		final StringBuilder buffer = new StringBuilder();
		try ( BufferedReader reader = new BufferedReader(new InputStreamReader(input)) )
		{
			String line;
			while( (line=reader.readLine())!=null) {
				buffer.append(line).append("\n");
			}
		}
		return buffer.toString();
	}
}