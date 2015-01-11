package de.codesourcery.luaparser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

public class Main
{
	private static final String[] IGNORED_FILES = {"projectiles.lua","enemies.lua","tiles.lua"};

	public static void main(String[] args) throws Exception {
		new Main().run();
	}

	private void run() throws Exception
	{
		final List<JSONObject> results = new ArrayList<>();
		process( new File("/home/tobi/Downloads/tmp/factorio/data/base/prototypes") , results );
	}

	private void process(File file,List<JSONObject> results) throws IOException {

		if ( file.isDirectory() )
		{
			for ( final File f : file.listFiles() ) {
				process( f , results );
			}
			return;
		}
		if ( shouldProcessFile( file ) )
		{
			System.out.println("Processing "+file.getAbsolutePath());
			final String contents = Parser.readFile( file );
			final Lexer lexer = new Lexer(new Scanner(contents));

			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final PrintWriter writer = new PrintWriter(out,true);
			final Parser p = new Parser( lexer , writer );
			try {
				p.transform();
				writer.close();
				results.add( new JSONObject( new String(out.toByteArray() ) ) );
			}
			catch(final ParseException e)
			{
				e.printStackTrace();
				final int offset = e.index;
				if ( e.index >= 0 ) {
					final String msg = Parser.toErrorString( contents , offset , e.getMessage() );
					System.err.println( msg );
				}

			}
		}
	}

	private static boolean shouldProcessFile(File file)
	{
		if ( file.getName().endsWith(".lua") && ! file.getName().contains("demo"))
		{
			for ( final String ignored : IGNORED_FILES ) {
				if ( ignored.equals( file.getName() ) ) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
}
