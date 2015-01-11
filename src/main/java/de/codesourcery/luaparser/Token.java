package de.codesourcery.luaparser;

public class Token {

	public final TokenType type;
	public final String value;
	public final int position;
	
	public Token(TokenType type,String value,int position) {
		this.type=type;
		this.value=value;
		this.position=position;
	}
	
	public boolean hasType(TokenType t) {
		return t.equals( type );
	}
	
	public boolean matches(TokenType t,String expected) {
		return hasType(t) && expected.equals(this.value);
	}
	
	@Override
	public String toString() {
		return "Token[ "+type+" , value=>"+value+"< , pos="+position; 
	}
}
