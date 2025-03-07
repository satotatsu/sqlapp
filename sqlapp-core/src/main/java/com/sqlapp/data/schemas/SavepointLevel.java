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

package com.sqlapp.data.schemas;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Function or RoutineのSAVE POINT LEVEL
 * 
 * @author satoh
 * 
 */
public enum SavepointLevel implements EnumProperties {
	NewSavePointLevel("NEW SAVEPOINT LEVEL", "NEW.*"),
	/**
	 * 
	 */
	OldSavePointLevel("OLD SAVEPOINT LEVEL", "OLD.*");
	private final Pattern pattern;
	private final String text;

	private SavepointLevel(String text, String patternText) {
		this.text = text;
		pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
	}

	/**
	 * 文字列からenumオブジェクトを取得します
	 * 
	 * @param text
	 */
	public static SavepointLevel parse(String text) {
		if (text==null){
			return null;
		}
		for (SavepointLevel rule : SavepointLevel.values()) {
			Matcher matcher = rule.pattern.matcher(text);
			if (matcher.matches()) {
				return rule;
			}
		}
		return null;
	}

	/**
	 * 値を返します
	 * 
	 */
	public String getValue() {
		return text;
	}

	@Override
	public String getDisplayName() {
		return text;
	}

	@Override
	public String getDisplayName(Locale locale) {
		return getDisplayName();
	}

	@Override
	public String getSqlValue() {
		return getDisplayName();
	}
}
