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
 * Routine or ViewのSQL SECURITY
 * 
 * @author satoh
 * 
 */
public enum SqlSecurity implements EnumProperties {
	/** Routine、Viewへの権限のみではなくアクセスするオブヘクトへの権限も必要*/
	Invoker("SECURITY INVOKER", ".*(Invoker|Caller)\\s*"), 
	/** Routine、Viewへの権限のみで実行可能 */
	Definer("SECURITY DEFINER", ".*(Definer|Owner)\\s*"),
	;
	private final Pattern pattern;
	private final String text;

	private SqlSecurity(String text, String patternText) {
		this.text = text;
		pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
	}

	/**
	 * 文字列からenumオブジェクトを取得します
	 * 
	 * @param text
	 */
	public static SqlSecurity parse(String text) {
		if (text==null){
			return null;
		}
		for (SqlSecurity rule : SqlSecurity.values()) {
			Matcher matcher = rule.pattern.matcher(text);
			if (matcher.matches()) {
				return rule;
			}
		}
		return null;
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
