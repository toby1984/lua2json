package de.codesourcery.luaparser;

public class ParseException extends RuntimeException {

	public final int index;
	
	public ParseException(String msg,int index) {
		super(msg+" at offset "+index);
		this.index = index;
	}
}
