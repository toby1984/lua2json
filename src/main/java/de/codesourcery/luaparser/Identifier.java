package de.codesourcery.luaparser;

import java.util.regex.Pattern;

public class Identifier {

	public final String id;
	private static final Pattern VALID_ID=Pattern.compile("^_*[a-zA-Z]+[_0-9a-zA-Z]*");

	public Identifier(String id) {
		if ( ! isValidIdentifier( id )  ) {
			throw new IllegalArgumentException("Not a valid identifier");
		}
		this.id=id;
	}

	public static boolean isValidIdentifier(Token t) {
		return t.hasType(TokenType.TEXT) && isValidIdentifier(t.value);
	}

	public static boolean isValidIdentifier(String s) {
		return s != null && VALID_ID.matcher(s).matches();
	}
}
