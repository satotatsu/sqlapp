/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.schemas;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IDENTITYの生成タイプ
 * 
 * @author satoh
 * 
 */
public enum IdentityGenerationType implements EnumProperties {
	/**
	 * ALWAYS
	 */
	Always("ALWAYS", "always")
	/**
	 * ByDefault
	 */
	, ByDefault("BY DEFAULT", ".*default");

	IdentityGenerationType(String text, String patternText) {
		this.text = text;
		this.pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
	}

	private final String text;

	private final Pattern pattern;

	public static IdentityGenerationType parse(String text) {
		if (text==null){
			return null;
		}
		for (IdentityGenerationType order : values()) {
			Matcher matcher = order.pattern.matcher(text);
			if (matcher.matches()) {
				return order;
			}
		}
		return null;
	}

	@Override
	public String getDisplayName() {
		return this.text;
	}

	@Override
	public String getDisplayName(Locale locale) {
		return this.text;
	}

	@Override
	public String getSqlValue() {
		return this.text;
	}

}
