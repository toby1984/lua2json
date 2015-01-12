/**
 * Copyright 2015 Tobias Gierke <tobias.gierke@code-sourcery.de>
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class LenientJSONParser
{

    public static JSONObject parse(String s) throws JSONException {
    	return parse( new JSONTokener( s ) );
    }

    public static JSONObject parse(JSONTokener x) throws JSONException
    {
    	final JSONObject result = new JSONObject();
        char c;
        String key;

        if (x.nextClean() != '{') {
            throw x.syntaxError("A JSONObject text must begin with '{'");
        }
        for (;;) {
            c = x.nextClean();
            switch (c) {
            case 0:
                throw x.syntaxError("A JSONObject text must end with '}'");
            case '}':
                return result;
            default:
                x.back();
                key = nextValue(x).toString();
            }

// The key is followed by ':'.

            c = x.nextClean();
            if (c != ':') {
                throw x.syntaxError("Expected a ':' after a key");
            }
            result.put(key, nextValue(x));

// Pairs are separated by ','.

            switch (x.nextClean()) {
            case ';':
            case ',':
                if (x.nextClean() == '}') {
                    return result;
                }
                x.back();
                break;
            case '}':
                return result;
            default:
                throw x.syntaxError("Expected a ',' or '}'");
            }
        }
    }

    private static Object nextValue(JSONTokener tokenizer) throws JSONException
    {
        char c = tokenizer.nextClean();
        String string;

        switch (c) {
            case '"':
            case '\'':
                return tokenizer.nextString(c);
            case '{':
            	tokenizer.back();
                return parse(tokenizer); // new JSONObject(tokenizer);
            case '[':
            	tokenizer.back();
                return new JSONArray(tokenizer);
        }

        /*
         * Handle unquoted text. This could be the values true, false, or
         * null, or it can be a number. An implementation (such as this one)
         * is allowed to also accept non-standard forms.
         *
         * Accumulate characters until we reach the end of the text or a
         * formatting character.
         */

        StringBuilder sb = new StringBuilder();
        while (c >= ' ' && ",:]}/\\\"[{;=#".indexOf(c) < 0) {
            sb.append(c);
            c = tokenizer.next();
        }
        tokenizer.back();

        string = sb.toString().trim();
        if ("".equals(string)) {
            throw tokenizer.syntaxError("Missing value");
        }
        return JSONObject.stringToValue(string);
    }
}
