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
 * FunctionのNULL INPUT
 * 
 * @author satoh
 * 
 */
public enum OnNullCall implements EnumProperties {
	/**
	 * RETURNS NULL ON NULL INPUT
	 * NULLが引数で呼ばれた場合に関数を実行しないでNULLを返す
	 */
	ReturnsNullOnNullInput("RETURNS NULL ON NULL INPUT",
			"RETURNS\\s*NULL\\s*ON\\s*NULL\\s*INPUT")
	,
	/**
	 * CALLED ON NULL INPUT
	 * NULLが引数で呼ばれた場合も関数を実行
	 */
	CalledOnNullInput("CALLED ON NULL INPUT", "CALLED\\s*ON\\s*NULL\\s*INPUT"){
		@Override
		public boolean isDefault(){
			return true;
		}
	};
	private final Pattern pattern;
	private final String text;
	
	private OnNullCall(final String text, final String patternText) {
		this.text = text;
		pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
	}

	/**
	 * デフォルトかを返します
	 */
	public boolean isDefault(){
		return false;
	}
	
	/**
	 * 文字列からenumオブジェクトを取得します
	 * 
	 * @param text
	 */
	public static OnNullCall parse(String text) {
		if (text==null){
			return null;
		}
		for (OnNullCall rule : OnNullCall.values()) {
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
