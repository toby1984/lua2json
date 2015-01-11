package de.codesourcery.luaparser;

import java.io.IOException;


public class LexerTest {

	public static void main(String[] args) throws IOException {

		final String s = "0.2";

		final Lexer lexer=new Lexer(new Scanner( s ) );
//		final Lexer lexer=new Lexer(new Scanner( Parser.readFile( new File("/home/tobi/Downloads/tmp/factorio/data/base/prototypes/entity/entities.lua") ) ) );

		while( ! lexer.eof() ) {
			System.out.println( lexer.next() );
		}
		System.out.println("Finished parsing.");
	}

	private static char lastChar(String s) {
		return s.charAt(s.length()-1);
	}
}
