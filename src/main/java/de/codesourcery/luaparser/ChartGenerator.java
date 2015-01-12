package de.codesourcery.luaparser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

public class ChartGenerator
{
	private final File factorioInstallDir;
	private final File dotOutputFile;

	public ChartGenerator(File factorioInstallDir,File dotOutputFile) {
		this.factorioInstallDir = factorioInstallDir;
		this.dotOutputFile = dotOutputFile;
	}

	public static void main(String[] args) throws IOException
	{
		final File appDir;
		final File dotOutput;
		if ( args.length == 2 ) {
			appDir = new File(args[0]);
			dotOutput = new File(args[1]);
		}
		else if ( args.length == 0 )
		{
			appDir = new File( "/home/tgierke/apps/factorio" );
			dotOutput = new File("/tmp/factorio.dot");
		}
		else
		{
			throw new IllegalArgumentException("Expected either two or no command-line arguments");
		}
		new ChartGenerator( appDir , dotOutput ).generateDOT();
	}

	protected static final class JSONFromFile
	{
		public final File luaSourceFile;
		public final String json;

		public JSONFromFile(File luaSourceFile, String json) {
			super();
			this.luaSourceFile = luaSourceFile;
			this.json = json;
		}
	}

	private void generateDOT() throws IOException
	{
		final File inputDir = new File(factorioInstallDir,"data/base/prototypes/recipe");

		final RecipeJSONParser jsonParser = new RecipeJSONParser();

		final LuaToJSON luaConverter = new LuaToJSON();
		final List<JSONFromFile> jsonData = new ArrayList<>();

		final List<File> filesToParse = new ArrayList<>();
		for ( File f : inputDir.listFiles() )
		{
			if ( ! f.isFile() || f.getName().contains("demo" ) || ! f.getName().endsWith(".lua" ) ) {
				System.out.println("Skipped: "+f.getAbsolutePath());
				continue;
			}
			filesToParse.add(f);
		}

		// filesToParse.add( new File(factorioInstallDir, "data/base/prototypes/entity/entities.lua"  ) );

		for ( File f : filesToParse )
		{
			try
			{
				System.out.println("Extracting data from "+f.getAbsolutePath()+"...");
				final String json = luaConverter.getJSON(f);
				System.out.println("Parsing generated JSON ...");
				jsonData.add(new JSONFromFile(f , json) );
				jsonParser.addJSON( json );
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}

		// merge JSON data from all parsed files
		final JSONObject root = new JSONObject();
		jsonData.forEach( data ->
		{
			final JSONObject parsed;
			try {
				parsed = LenientJSONParser.parse(data.json);
			}
			catch(Exception e)
			{
				System.out.flush();
				System.err.flush();
				System.err.println("Extracting JSON from "+data.luaSourceFile+" yielded malformed data from");
				e.printStackTrace();
				System.err.flush();
				return;
			}

			for ( String key : parsed.keySet() )
			{
				if ( root.has( key ) ) {
					throw new RuntimeException("Failed to merge JSON data from file "+data.luaSourceFile+", duplicate key '"+key+"'");
				}
				root.put( key , parsed.get(key) );
			}
		});

		final File combinedJSONOutput = new File( dotOutputFile.getAbsolutePath()+".json" );
		System.out.println("Writing combined JSON output to "+combinedJSONOutput.getAbsolutePath());
		try ( FileWriter combinedJSON = new FileWriter( combinedJSONOutput ) ) {
			combinedJSON .write( root.toString(4) );
		}

		final String dotOutput = new DOTRenderer().generateDot( jsonParser.getRecipes() );
		System.out.println("Writing DOT output to "+dotOutputFile.getAbsolutePath());
		try ( FileWriter writer = new FileWriter( dotOutputFile ) ) {
			writer.write( dotOutput );
		}
	}
}