package de.codesourcery.luaparser;

public class Scanner {

	private static final boolean DEBUG = false;

	private final String input;

	private int currentIndex;

	public Scanner(String input) {
		this.input=input;
	}

	public String input() {
		return input;
	}

	public void setOffset(int offset) {
		currentIndex=offset;
	}

	public char peek() {
		final char c = input.charAt(currentIndex);
		if ( DEBUG ) {
			System.out.println("peek(): >"+c+"' ("+(int) c+")");
		}
		return c;
	}

	public char next() {
		final char c = input.charAt(currentIndex++);
		if ( DEBUG ) {
			System.out.println("next(): >"+c+"' ("+(int) c+")");
		}
		return c;
	}

	public boolean eof() {
		return currentIndex >= input.length();
	}

	public int currentIndex() { return currentIndex; }
}
