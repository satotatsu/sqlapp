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
 * Function or RoutineのSQL DATA ACCESS
 * 
 * @author satoh
 * 
 */
public enum SqlDataAccess implements EnumProperties {
	/**
	 * no SQL command
	 */
	NoSql("NO SQL", "NO\\s*SQL")
	/**
	 * データを読み書きするステートメントを含まない
	 */
	, ContainsSql("CONTAINS SQL", "CONTAINS\\s*SQL")
	/**
	 * データを読むステートメントを含む
	 */
	, ReadsSqlData("READS SQL DATA", "READS\\s*SQL\\s*DATA")
	/**
	 * データを書くするステートメントを含む
	 */
	, ModifiesSqlData("MODIFIES SQL DATA", "MODIFIES SQL DATA");
	private final Pattern pattern;
	private final String text;

	private SqlDataAccess(String text, String patternText) {
		this.text = text;
		pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
	}

	/**
	 * 文字列からenumオブジェクトを取得します
	 * 
	 * @param text
	 */
	public static SqlDataAccess parse(String text) {
		if (text==null){
			return null;
		}
		for (SqlDataAccess rule : SqlDataAccess.values()) {
			Matcher matcher = rule.pattern.matcher(text);
			if (matcher.matches()) {
				return rule;
			}
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return text;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.EnumProperties#getDisplayName(java.util.Locale)
	 */
	@Override
	public String getDisplayName(Locale locale) {
		return getDisplayName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getSqlValue()
	 */
	@Override
	public String getSqlValue() {
		return getDisplayName();
	}
}
