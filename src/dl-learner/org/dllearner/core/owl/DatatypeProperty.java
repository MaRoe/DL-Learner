/**
 * Copyright (C) 2007-2008, Jens Lehmann
 *
 * This file is part of DL-Learner.
 * 
 * DL-Learner is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * DL-Learner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.dllearner.core.owl;

import java.util.Map;

import org.dllearner.utilities.Helper;

/**
 * @author Jens Lehmann
 *
 */
public class DatatypeProperty implements Comparable<DatatypeProperty>, Property, NamedKBElement {

	protected String name;
	
	public DatatypeProperty(String name) {
		this.name=name;
	}	
	
	/* (non-Javadoc)
	 * @see org.dllearner.core.dl.KBElement#getLength()
	 */
	public int getLength() {
		return 1;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return toString(null, null);
	}
	
	public String toString(String baseURI, Map<String, String> prefixes) {
		return  Helper.getAbbreviatedString(name, baseURI, prefixes);
	}
	
	public String toKBSyntaxString(String baseURI, Map<String, String> prefixes) {
		return "\"" + Helper.getAbbreviatedString(name, baseURI, prefixes) + "\"";
	}

	public void accept(KBElementVisitor visitor) {
		visitor.visit(this);
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(DatatypeProperty o) {
		return name.compareTo(o.name);
	}	
}
