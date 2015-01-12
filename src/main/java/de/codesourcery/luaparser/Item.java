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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class Item
{
	public final String name;
	public final List<ItemAndAmount> requirements = new ArrayList<>();

	public Item(String name) {
		this.name = name;
	}

	public boolean requires(Item otherItem)
	{
		for ( final ItemAndAmount req : requirements ) {
			if ( req.item.equals( otherItem ) ) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean equals(Object obj)
	{
		return obj instanceof Item && ((Item) obj).name.equals( this.name );
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		return "\""+name+"\" requires "+StringUtils.join(requirements," , " );
	}
}