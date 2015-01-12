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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

public class Main
{
	private File factorioInstallDir;
	private File dotOutputFile;
	private File jsonOutputDir;

	public Main() {
	}

	public void setFactorioInstallDir(File factorioInstallDir) {
		this.factorioInstallDir = factorioInstallDir;
	}

	public void setDotOutputFile(File dotOutputFile) {
		this.dotOutputFile = dotOutputFile;
	}

	public void setJsonOutputDir(File jsonOutputDir) {
		this.jsonOutputDir = jsonOutputDir;
	}

	private static void fail(String msg) {
		System.err.println("Invalid command-line: "+msg);
		System.exit(1);
	}

	public static void main(String[] args) throws IOException
	{
		final Main main = new Main();
		for ( int i = 0 ; i < args.length ; i++) {
			final String arg = args[i];
			switch(arg) {
				case "--jsondir":
					if ( (i+1) >= args.length ) {
						fail("--jsondir parameter requires an argument");
					}
					main.jsonOutputDir = new File( args[i+1] );
					i++;
					break;
				case "--help":
					System.out.println("\nUsage: [--dotfile <FILE> ] [ --jsondir <DIRECTORY>] <factorio install dir>");
					System.out.println("Either --dotfile and/or --jsondir options need to be present");
					System.exit(0);
					break;
				case "--dotfile":
					if ( (i+1) >= args.length ) {
						fail("--dotfile parameter requires an argument");
					}
					main.dotOutputFile = new File( args[i+1] );
					i++;
					break;
				default:
					if ( main.factorioInstallDir != null ) {
						fail("Unrecognized option '"+arg+"'");
					}
					main.factorioInstallDir = new File(arg);
			}
		}

		if ( main.factorioInstallDir == null ) {
			fail("You need to specify the Factorio installation directory");
		}

		if ( main.dotOutputFile == null && main.jsonOutputDir == null ) {
			fail("You need to specify the --jsondir and/or --dotfile option(s)");
		}

		main.generateDOT();
		System.exit(0);
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
		for ( final File f : inputDir.listFiles() )
		{
			if ( ! f.isFile() || ! f.getName().endsWith(".lua" ) ) {
				System.out.println("Skipped: "+f.getAbsolutePath());
				continue;
			}
			filesToParse.add(f);
		}

		// filesToParse.add( new File(factorioInstallDir, "data/base/prototypes/entity/entities.lua"  ) );

		for ( final File f : filesToParse )
		{
			final String json;
			try
			{
				System.out.println("Extracting data from "+f.getAbsolutePath()+"...");
				json = luaConverter.getJSON(f);
				System.out.println("Parsing generated JSON ...");
				jsonData.add(new JSONFromFile(f , json) );
				jsonParser.addJSON( json );
			}
			catch(final Exception e)
			{
				e.printStackTrace();
				continue;
			}

			if ( jsonOutputDir != null )
			{
				final File jsonOut = new File(jsonOutputDir,f.getName()+".json" );
				System.out.println("Writing JSON to "+jsonOut.getAbsolutePath());
				try (FileWriter writer = new FileWriter( jsonOut ) )
				{
					writer.write(json);
				}
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
			catch(final Exception e)
			{
				System.out.flush();
				System.err.flush();
				System.err.println("Extracting JSON from "+data.luaSourceFile+" yielded malformed data from");
				e.printStackTrace();
				System.err.flush();
				return;
			}

			for ( final String key : parsed.keySet() )
			{
				if ( root.has( key ) ) {
					throw new RuntimeException("Failed to merge JSON data from file "+data.luaSourceFile+", duplicate key '"+key+"'");
				}
				root.put( key , parsed.get(key) );
			}
		});

		if ( jsonOutputDir != null ) {
			final File combinedJSONOutput = new File( jsonOutputDir , "combined.json" );
			System.out.println("Writing merged JSON output to "+combinedJSONOutput.getAbsolutePath());
			try ( FileWriter combinedJSON = new FileWriter( combinedJSONOutput ) ) {
				combinedJSON .write( root.toString(4) );
			}
		}

		if ( dotOutputFile != null ) {
			final String dotOutput = new DOTRenderer().generateDot( jsonParser.getRecipes() );
			System.out.println("Writing DOT output to "+dotOutputFile.getAbsolutePath());
			try ( FileWriter writer = new FileWriter( dotOutputFile ) ) {
				writer.write( dotOutput );
			}
		}
	}
}