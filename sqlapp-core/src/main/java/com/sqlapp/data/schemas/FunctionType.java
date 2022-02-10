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
 * Function TYPE
 * 
 * @author satoh
 * 
 */
public enum FunctionType implements EnumProperties {
	/**
	 * Aggregate Function
	 */
	Aggregate("AGGREGATE", "AG.*"){
		@Override
		public boolean isAggregate(){
			return true;
		}
	},
	/**
	 * Window Function
	 */
	Window("WINDOW", "WINDOW.*"),
	/**
	 * Table Function
	 */
	Table("TABLE", "TABLE.*"){
		@Override
		public boolean isTable(){
			return true;
		}
	},
	/**
	 * Row Function
	 */
	Row("ROW", "ROW.*"){
		@Override
		public boolean isRow(){
			return true;
		}
	},
	/**
	 * Scalar Function
	 */
	Scalar("", ".*"){
		@Override
		public boolean isScalar(){
			return true;
		}
	};
	private final Pattern pattern;
	private final String text;

	private FunctionType(String text, String patternText) {
		this.text = text;
		pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
	}

	public boolean isAggregate(){
		return false;
	}

	public boolean isTable(){
		return false;
	}
	
	public boolean isScalar(){
		return false;
	}

	public boolean isRow(){
		return false;
	}

	/**
	 * 文字列からenumオブジェクトを取得します
	 * 
	 * @param text
	 */
	public static FunctionType parse(String text) {
		if (text==null){
			return null;
		}
		for (FunctionType rule : FunctionType.values()) {
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
