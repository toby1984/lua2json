This is a crude tool I did to extract material/item requirements from Factorio's .lua files. It uses a full-blown LUA parser (generated using the ANTLR parser generator) to extract data from the LUA files. Currently only a subset of LUA constructs is properly handled - I only implemented partial expression evaluation so LUA files that do things like 
"a" = 3*5 will work but not all operators are currently supported. Assignments that have a function invocation on the RHS will turn the function name into a string in the generated JSON output.

Requirements
============

- Maven 3.x
- JDK 1.8

Building
========

To generate a executable JAR with all dependencies, run

mvn clean package

Running
=======

Building will generate a self-executable JAR in target/lua2json.jar

Running this JAR will try to extract data from the game's LUA files , generate JSON from it and use the JSON data to output a Graphviz .dot file.

Specifying the --jsondir or --dotfile option only will give you just that.

Command-line options
====================
java -jar target/lua2json.jar --help

Usage: [--dotfile <FILE> ] [ --jsondir <DIRECTORY>] <factorio install dir>
Either --dotfile and/or --jsondir options need to be present
