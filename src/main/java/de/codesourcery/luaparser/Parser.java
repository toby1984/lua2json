package de.	codesourcery.luaparser;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Stack;

import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;

public class Parser {

	private static final boolean DEBUG = true;

	private final Lexer lexer;
	private final Writer writer;

	public Parser(Lexer lexer,Writer writer) {
		this.lexer = lexer;
		this.writer = writer;
	}

	public static void main(String[] args) throws IOException {

//		final InputStream input = LexerTest.class.getResourceAsStream("test.lua");
		final InputStream input = new FileInputStream("/home/tobi/Downloads/tmp/factorio/data/base/prototypes/entity/entities.lua");

		final Lexer lexer=new Lexer(new Scanner( readFile(input ) ) );

		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final PrintWriter writer = new PrintWriter(out,true);
		try {
			final Parser p = new Parser( lexer , writer );
			p.transform();
		} finally {
			writer.close();
			System.out.println("\n\nPROCESSED:\n\n"+new String(out.toByteArray()));
		}
		System.out.println("*** Finished parsing ***");

		final String jsonString = new String(out.toByteArray() );

		// System.err.println("ERROR:\n"+Parser.toErrorString(jsonString, 108, "ERROR" ) );
		final JSONObject object = new JSONObject( jsonString );
		printJSON(object,0);
	}

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

	private static void printJSON(JSONObject object,int indent) {

		for (final String key : object.keySet() )
		{
			final Object value = object.get(key);
			final String indention = StringUtils.repeat( " ", indent*5 );
			System.out.print( indention );
			System.out.print(key+" = ");
			if ( value instanceof JSONObject) {
				System.out.println("\n"+indention+"{");
				printJSON((JSONObject) value,indent+1);
				System.out.println("}");
			} else {
				System.out.print( value );
				if ( value != null ) {
					System.out.println(" <class: "+value.getClass()+">" );
				}
				System.out.println();
			}
		}
	}

	public void transform()
	{
		if ( ! parseInput() )
		{
			throw new ParseException( "\n"+toErrorString( lexer.scanner().input() , lexer.currentPosition() , "Syntax error") , lexer.currentPosition() );
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

	private boolean parseInput()
	{
		while ( ! lexer.eof() )
		{
			if ( parseFunction() || parseRequires() ) {
				continue;
			}
			else if ( ! lexer.eof() && lexer.peek().matches(TokenType.TEXT, "data:extend" ) )
			{
				lexer.next();
				if( consume( TokenType.PARENS_OPEN ) && consume(TokenType.CURLY_BRACE_OPEN))
				{
					parseValue();
					return consume(TokenType.CURLY_BRACE_CLOSE) && consume(TokenType.PARENS_CLOSE);
				}
			} else if ( ! lexer.eof() ) {
				consume();
				// return false;
			}
		}
		return true;
	}

	private boolean parseFunction() {

		if ( consume(TokenType.FUNCTION ) ) {
			if ( consume(TokenType.PARENS_OPEN ) )
			{
				// skip function arguments
				while ( ! lexer.eof() && ! lexer.peek().hasType(TokenType.PARENS_CLOSE ) ) {
					consume();
				}
				if ( ! consume(TokenType.PARENS_CLOSE ) ) {
					return false;
				}
				while ( ! lexer.eof() && ! lexer.peek().hasType(TokenType.END ) ) {
					consume();
				}
				return consume(TokenType.END);
			}
		}
		return false;
	}

	private boolean parseRequires()
	{
		if ( ! lexer.eof() && lexer.peek().hasType(TokenType.REQUIRE ) ) {
			consume(TokenType.REQUIRE);
			return consume(TokenType.PARENS_OPEN) && parseString(false) && consume(TokenType.PARENS_CLOSE);
		}
		return false;
	}

	private boolean parseKeyValue()
	{
		return parseKey() && parseEquals() && parseValue();
	}

	private boolean parseValue() {

		if ( ! lexer.eof() )
		{
			do
			{
				if ( lexer.peek().hasType(TokenType.CURLY_BRACE_OPEN ) ) { // either a list of values or a nested map

					final Token braceOpen = lexer.next();

					final boolean isObjectDefinition = ! lexer.eof() && Identifier.isValidIdentifier( lexer.peek() );

					if ( isObjectDefinition ) {
						print(braceOpen);
					} else {
						print("[");
					}

					if ( ! lexer.eof() && ! lexer.peek().hasType(TokenType.CURLY_BRACE_CLOSE ) ) // accept empty {} expression
					{
						if ( ! parseValue() ) {
							return false;
						}
					}

					if ( lexer.eof() || !lexer.peek().hasType(TokenType.CURLY_BRACE_CLOSE)) {
						return false;
					}
					if ( isObjectDefinition ) {
						consumeAndPrint(TokenType.CURLY_BRACE_CLOSE);
					} else {
						consume(TokenType.CURLY_BRACE_CLOSE);
						print("]");
					}
				}
				else
				{
					if ( Identifier.isValidIdentifier( lexer.peek() ) ) { // => either key/value or a simple identifier

						boolean isAssignment = false;
						lexer.rememberState();
						try
						{
							lexer.next(); // swallow
							isAssignment = ! lexer.eof() && lexer.peek().hasType(TokenType.EQUALS);
						} finally {
							lexer.recallState();
						}
						if ( isAssignment ) {
							return parseKeyValue();
						}
						// swallow identifier
						consume();

						if ( ! lexer.eof() && lexer.peek().hasType(TokenType.PARENS_OPEN ) ) { // <identifier>( ===> function invocation
							if ( ! skipExpressionInParens() ) {
								return false;
							}
							print("\"SOME_FUNCTION_INVOCATION\"");
						} else {
							print("\"SOME_IDENTIFIER\"");
						}
					}
					else if ( ! parseAtom() ) {
						return false;
					}
				}
				if ( lexer.eof() || ! lexer.peek().hasType(TokenType.COMMA ) )
				{
					break;
				}
				final Token comma = lexer.next();
				if ( ! lexer.eof() && lexer.peek().hasType(TokenType.CURLY_BRACE_CLOSE ) ) {
					// LUA seems to allow dangling commas in lists like
					// a = {1,2,3,}
					break; // ignore dangling comma
				} else {
					print( comma );
				}
			} while(true);
			return true;
		}
		return false;
	}

	private boolean skipExpressionInParens()
	{
		if ( ! lexer.eof() && lexer.peek().hasType(TokenType.PARENS_OPEN ) ) {
			int openingParens = 0;
			while ( ! lexer.eof() && lexer.peek().hasType(TokenType.PARENS_OPEN ) ) {
				consume();
				openingParens++;
			}
			while ( ! lexer.eof() && ! lexer.peek().hasType(TokenType.PARENS_CLOSE ) ) {
				consume();
			}
			while ( ! lexer.eof() && lexer.peek().hasType(TokenType.PARENS_CLOSE) && openingParens-- > 0 ) {
				consume();
			}
			return openingParens == 0;
		}
		return false;
	}

	private boolean parseAtom()
	{
		if ( parseString() || parseNumber() || parseBoolean() || parseIdentifier() )
		{
			/*
			 * TODO: This is just a hack to ignore some trailing semicolons ; the proper solution
			 * would be to parse actually statements (=implement full grammar) and handle them properly...
			 */
			if ( ! lexer.eof() && lexer.peek().hasType(TokenType.SEMICOLON ) )
			{
				final Token semicolon = lexer.next(TokenType.SEMICOLON);
				if ( DEBUG ) {
					System.err.println("Injecting COMMA instead of SEMICOLON at offset "+semicolon.position);
				}
				lexer.pushBack( new Token(TokenType.COMMA,"," , semicolon.position) );
			}
			return true;
		}
		return false;
	}

	private boolean parseIdentifier()
	{
		if ( ! lexer.eof() && Identifier.isValidIdentifier( lexer.peek() ) ) {
			consumeAndPrint();
		}
		return false;
	}

	private boolean parseBoolean() {
		return parseBoolean(true);
	}

	private boolean parseBoolean(boolean print) {
		return consume(TokenType.BOOLEAN_LITERAL,print);
	}

	private boolean parseNumber()
	{
		return parseNumber(true);
	}

	private boolean parseNumber(boolean print)
	{
		if ( ! lexer.eof() )
		{
			final Stack<Double> valueStack = new Stack<>();
			final Stack<String> operatorStack = new Stack<>();
			while ( ! lexer.eof() )
			{
				final Double number = internalParseNumber(true);
				if ( number != null )
				{
					valueStack.push( number );

					if ( ! lexer.eof() && isOperatorToken( lexer.peek() ) )
					{
						operatorStack.push( lexer.next().value );
					}
					else
					{
						break;
					}
				} else {
					break;
				}
			}

			while ( ! operatorStack.isEmpty() )
			{
				final Double operand2 = valueStack.pop();
				final Double operand1 = valueStack.pop();

				switch( operatorStack.pop() )
				{
					case "+":
						valueStack.push( operand1 + operand2 );
						break;
					case "-":
						valueStack.push( operand1 - operand2 );
						break;
					case "*":
						valueStack.push( operand1 * operand2 );
						break;
					case "/":
						valueStack.push( operand1 / operand2 );
						break;
				}
			}

			if ( valueStack.size() != 1 ) {
				if ( DEBUG ) {
					System.err.println("Invalid numeric expression, expected exactly one number on stack but got "+valueStack.size());
				}
				return false;
			}

			if ( print )
			{
				System.out.print( valueStack.pop() );
			}
			return true;
		}
		return false;
	}

	private Double internalParseNumber(boolean supportExponentialNumbers)
	{
		if ( ! lexer.eof() )
		{
			String number = "";
			if ( lexer.peek().hasType(TokenType.MINUS) ) { // unary minus
				consume();
				number += "-";
				if ( lexer.eof() ) {
					if ( DEBUG ) {
						System.err.println("Unary minus without following number ?");
					}
					return null;
				}
			}

			if ( ! lexer.eof() && lexer.peek().hasType(TokenType.NUMBER) )
			{
				final Token token = lexer.next(TokenType.NUMBER);
				number += token.value;

				// LUA supports exponents in number literals , e.g.
				// 20e-8
				Double exponent = null;
				if ( supportExponentialNumbers && ! lexer.eof() && lexer.peek().value.equals("e" ) )
				{
					lexer.next(); // consume 'e'
					exponent = internalParseNumber(true);
					if ( exponent == null ) {
						if ( DEBUG ) {
							System.err.println("Invalid exponent in floating point number");
						}
						return null;
					}
				}
				Double result = Double.parseDouble( number );
				if ( exponent != null ) {
					result = result * Math.pow( 10 , exponent );
				}
				return result;
			}
		}
		return null;
	}

	private static boolean isOperatorToken(Token t) {
		return t.hasType(TokenType.OPERATOR) || t.hasType(TokenType.MINUS);
	}

	private boolean parseString()
	{
		return parseString(true);
	}

	private boolean parseString(boolean print)
	{
		if ( ! lexer.eof() && consume(TokenType.DOUBLE_QUOTE , print ) )
		{
			while( ! lexer.eof() && ! lexer.peek().hasType(TokenType.DOUBLE_QUOTE ) )
			{
				consume(print);
			}
			return consume(TokenType.DOUBLE_QUOTE,print);
		}
		return false;
	}

	private boolean parseEquals()
	{
		if ( consume(TokenType.EQUALS ) )
		{
			return print(":");
		}
		return false;
	}

	private boolean parseKey()
	{
		if ( ! lexer.eof() && Identifier.isValidIdentifier( lexer.peek() ) )
		{
			final Token token = lexer.next();
			print("\"");
			print(token);
			print("\"");
			return true;
		}
		return false;
	}

	private boolean consume(TokenType t) {
		return consume(t,false);
	}

	private boolean consumeAndPrint(TokenType t) {
		return consume(t,true);
	}

	private boolean consumeAndPrint() {
		return consume(true);
	}

	private boolean consume() {
		return consume(false);
	}

	private boolean consume(boolean print) {
		if ( ! lexer.eof() ) {
			if ( print ) {
				print( lexer.next() );
			} else {
				lexer.next();
			}
			return true;
		}
		return false;
	}

	private boolean print(Token t) {
		return print(t.value);
	}

	private boolean print(String s) {
		try {
			writer.append( s );
			writer.flush();
			return true;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean consume(TokenType t,boolean print) {
		if ( ! lexer.eof() && lexer.peek().hasType( t ) )
		{
			final Token token = lexer.next();
			if (print ) {
				print(token);
			}
			return true;
		}
		return false;
	}
}