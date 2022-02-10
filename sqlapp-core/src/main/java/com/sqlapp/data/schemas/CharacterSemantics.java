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

import com.sqlapp.util.EnumUtils;

/**
 * カラムの文字列のセマンティックス
 * 
 * @author satoh
 * 
 */
public enum CharacterSemantics implements EnumProperties {
	Byte, Char;

	private CharacterSemantics() {
	}

	public static CharacterSemantics parse(String value) {
		if (value == null) {
			return null;
		}
		String upper=value.toUpperCase();
		if (upper.startsWith("B")) {
			return Byte;
		}else if (upper.startsWith("O")) {
			return Byte;
		}else if (upper.startsWith("C")) {
			return Char;
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getDisplayValue()
	 */
	@Override
	public String getDisplayName() {
		return getDisplayName(Locale.ENGLISH);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.EnumProperties#getDisplayValue(java.util.Locale)
	 */
	@Override
	public String getDisplayName(Locale locale) {
		return EnumUtils.getDisplayName(this, locale);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getSqlValue()
	 */
	@Override
	public String getSqlValue() {
		return this.toString().toUpperCase();
	}
}
