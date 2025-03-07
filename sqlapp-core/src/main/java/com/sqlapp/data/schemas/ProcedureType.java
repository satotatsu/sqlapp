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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * プロシージャの種類
 * 
 * @author satoh
 * 
 */
public enum ProcedureType implements EnumProperties {
	Procedure("PROCEDURE", "Pro.*"),
	/**
	 * Function
	 */
	Function("FUNCTION", "F.*"),
	/**
	 * Package
	 */
	Package("PACKAGE", "Pack.*"),
	/**
	 * PACKAGE BODY
	 */
	PackageBody("PACKAGE BODY", "Pack.*Body"),
	/**
	 * TRIGGER
	 */
	Trigger("TRIGGER", "T.*");

	private final String text;
	private final Pattern pattern;

	private ProcedureType(String text, String patternText) {
		this.text = text;
		pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
	}

	/**
	 * Procedureの文字列からの取得
	 * 
	 * @param type
	 */
	public static ProcedureType parse(String type) {
		if (isEmpty(type)) {
			return null;
		}
		for (ProcedureType procedureType : ProcedureType.values()) {
			Matcher matcher = procedureType.pattern.matcher(type);
			if (matcher.matches()) {
				return procedureType;
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
