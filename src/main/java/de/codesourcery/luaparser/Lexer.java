package de.codesourcery.luaparser;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;

public class Lexer {

	private static final boolean DEBUG = true;

	private final Scanner scanner;
	private final List<Token> tokens = new ArrayList<>();
	private int startIndex;
	private final StringBuilder buffer = new StringBuilder();
	private final Pattern NUMBER_PATTERN = Pattern.compile("^[0-9]+(\\.[0-9]+){0,1}$"); // LUA supports writing numbers with exponentials like 1e10 , 3e-5 etc.

	private int lineNumber = 0;
	private boolean skipWhitespace = true;

	private final Stack<State> stack = new Stack<>();

	protected final class State
	{
		private final int scannerOffset;
		private final List<Token> tokens;
		private final int lineNo;

		public State() {
			this.lineNo = lineNumber;
			this.scannerOffset = scanner.currentIndex();
			this.tokens = new ArrayList<>( Lexer.this.tokens );
		}

		public void reset()
		{
			lineNumber = lineNo;
			scanner.setOffset( scannerOffset );
			Lexer.this.tokens.clear();
			Lexer.this.tokens.addAll( tokens );
		}
	}

	public void pushBack(Token token)
	{
		if ( token == null ) {
			throw new IllegalArgumentException("Token must not be NULL");
		}
		if ( DEBUG ) {
			System.err.println("pushBack(): "+token);
		}
		tokens.add( 0 , token );
	}

	public Lexer(Scanner scanner) {
		this.scanner=scanner;
	}

	public void rememberState() {
		stack.push(new State() );
	}

	public void recallState() {
		stack.pop().reset();
	}

	public void discardState() {
		stack.pop();
	}

	public Scanner scanner() {
		return scanner;
	}

	public int currentPosition() {
		if ( tokens.isEmpty() ) {
			parse();
		}
		if ( ! tokens.isEmpty() ) {
			return tokens.get(0).position;
		}
		return scanner.currentIndex();
	}

	public boolean eof()
	{
		if ( tokens.isEmpty() ) {
			parse();
		}
		return tokens.isEmpty();
	}

	public int lineNumber() {
		return lineNumber;
	}

	public void setSkipWhitespace(boolean skipWhitespace) {
		this.skipWhitespace = skipWhitespace;
	}

	private void parse()
	{
		startIndex = scanner.currentIndex();
		buffer.setLength(0);
		while ( ! scanner.eof() && isWhitespace( scanner.peek() ) )
		{
			final char c = scanner.next();
			if ( c == '\n' )
			{
				lineNumber++;
				System.out.println("LINE: "+lineNumber);
				if (! skipWhitespace )
				{
					if ( buffer.length() > 0 ) {
						addToken(TokenType.WHITESPACE , buffer.toString() , startIndex );
					}
					addToken(TokenType.EOL , c , scanner.currentIndex()-1 );
					startIndex = scanner.currentIndex();
				}
			}
			else
			{
				if ( ! skipWhitespace ) {
					buffer.append( c );
				}
			}
		}

		if ( ! skipWhitespace && buffer.length() > 0 ) {
			addToken(TokenType.WHITESPACE , buffer.toString() , startIndex );
		}

		buffer.setLength(0);
		startIndex = scanner.currentIndex();
outer:
		while ( ! scanner.eof() )
		{
			final int index = scanner.currentIndex();
			char c = scanner.peek();
			if ( isWhitespace( c ) ) {
				break;
			}
			c = scanner.next();
			if ( c == '-' && ! scanner.eof() && scanner.peek() == '-' ) { // comment marker
				while ( ! scanner.eof() && scanner.peek()!='\n') { // advance to end of line
					scanner.next();
				}
				continue;
			}
			switch(c) {
				case '.':
					if ( ! bufferContainsDigits() )
					{
						parseBuffer();
						addToken(TokenType.DOT,c,index);
						break outer;
					}
					break;
				case ';': parseBuffer(); addToken(TokenType.SEMICOLON,c,index); break outer;
				case '(': parseBuffer(); addToken(TokenType.PARENS_OPEN,c,index); break outer;
				case ')': parseBuffer(); addToken(TokenType.PARENS_CLOSE,c,index); break outer;
				case '{': parseBuffer(); addToken(TokenType.CURLY_BRACE_OPEN,c,index); break outer;
				case '}': parseBuffer(); addToken(TokenType.CURLY_BRACE_CLOSE,c,index); break outer;
				case '=': parseBuffer(); addToken(TokenType.EQUALS,c,index); break outer;
				case '"': parseBuffer(); addToken(TokenType.DOUBLE_QUOTE,c,index); break outer;
				case ',': parseBuffer(); addToken(TokenType.COMMA,c,index); break outer;
				case '-': parseBuffer(); addToken(TokenType.MINUS,c,index); break outer;
				case '+':
				case '/':
				case '*':
					parseBuffer(); addToken(TokenType.OPERATOR,c,index); break outer;
				default: /* fall-through */
			}
			buffer.append( c );
			if ( buffer.toString().startsWith("end") ) {
				System.out.print("");
			}
		}
		parseBuffer();
	}

	private void addToken(TokenType t,char value,int index) {
		addToken( t , Character.toString( value ) , index );
	}

	private void addToken(TokenType t,String value,int index) {
		tokens.add( new Token(t,value,index ) );
	}

	private void parseBuffer()
	{
		final String s = buffer.toString();
		if ( s.length() > 0 )
		{
			if ( NUMBER_PATTERN.matcher( s ).matches() )
			{
				addToken(TokenType.NUMBER,s,startIndex);
			}
			else if ( s.endsWith("e" ) && NUMBER_PATTERN.matcher( s.substring( 0 ,  s.length()-1 ) ).matches() )
			{
				final String num = s.substring( 0 ,  s.length()-1 );
				addToken(TokenType.NUMBER, num  ,startIndex);
				addToken(TokenType.TEXT, "e" , startIndex+num.length() );
			} else if ( "true".equals( s ) || "false".equals( s ) ) {
				addToken(TokenType.BOOLEAN_LITERAL,s,startIndex);
			} else if ( "require".equals(s) ) {
				addToken(TokenType.REQUIRE,s,startIndex);
			} else if ( "function".equals(s) ) {
				addToken(TokenType.FUNCTION,s,startIndex);
			} else if ( "end".equals(s) ) {
				addToken(TokenType.END,s,startIndex);
			} else {
				addToken(TokenType.TEXT,s,startIndex);
			}
		}
		buffer.setLength( 0 );
	}

	private static boolean isWhitespace(char c) {
		return c == '\n' || c == '\r' || Character.isWhitespace( c );
	}

	private boolean bufferContainsDigits()
	{
		if ( buffer.length() > 0 )
		{
			for ( final char c : buffer.toString().toCharArray() )
			{
				if ( ! Character.isDigit(c ) ) {
					return false;
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString()
	{
		if ( tokens.isEmpty() ) {
			parse();
		}
		return tokens.isEmpty() ? "<EOF> ("+currentPosition()+")" : tokens.get(0).toString();
	}

	public Token peek() {
		if ( tokens.isEmpty() ) {
			parse();
		}
		if ( tokens.isEmpty() ) {
			throw new ParseException("Trying to peek() past end of input ?",scanner.currentIndex());
		}
		final Token result = tokens.get(0);
		if (DEBUG) {
			System.err.println("peek(): "+result);
		}
		return result;
	}

	public Token next(TokenType t)
	{
		if ( ! peek().hasType(t ) ) {
			throw new ParseException("Expected token type "+t+" but got "+peek(),peek().position);
		}
		return next();
	}

	public Token next() {
		if ( tokens.isEmpty() ) {
			parse();
		}
		if ( tokens.isEmpty() ) {
			throw new ParseException("Trying to next() past end of input ?",scanner.currentIndex());
		}
		final Token result = tokens.remove(0);
		if (DEBUG) {
			System.err.println("next(): "+result);
		}
		return result;
	}
}