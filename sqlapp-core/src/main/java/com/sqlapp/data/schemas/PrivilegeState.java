/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

/**
* Copyright 2017 tatsuo satoh
*/
package com.sqlapp.data.schemas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum PrivilegeState {
	/**
	 * 許可
	 */
	Grant("G.*"),
	/**
	 * 不許可
	 */
	Deny("D.*"),
	/**
	 * はく奪
	 */
	Revoke("R.*");
	Pattern pattern;

	PrivilegeState(String patternText) {
		pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
	}

	public static PrivilegeState parse(String text) {
		if (text==null){
			return null;
		}
		for (PrivilegeState state : PrivilegeState.values()) {
			Matcher matcher = state.pattern.matcher(text);
			if (matcher.matches()) {
				return state;
			}
		}
		return null;
	}
}
