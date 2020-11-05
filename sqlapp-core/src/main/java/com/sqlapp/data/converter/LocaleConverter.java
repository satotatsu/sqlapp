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
package com.sqlapp.data.converter;

import static com.sqlapp.util.CommonUtils.*;

import java.util.Locale;

import com.sqlapp.util.CommonUtils;

/**
 * LocaleType Converter
 * 
 * @author SATOH
 *
 */
public class LocaleConverter extends AbstractConverter<Locale> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -6423916550139552468L;

	private boolean toLanguageTag = true;

	@Override
	public Locale convertObject(Object value) {
		if (isEmpty(value)) {
			return getDefaultValue();
		} else if (value instanceof Locale) {
			return (Locale) value;
		}
		return CommonUtils.getLocale(value.toString());
	}

	@Override
	public String convertString(Locale value) {
		if (value == null) {
			return null;
		}
		if (isToLanguageTag()) {
			final String lang = value.toLanguageTag();
			if ("no".equals(lang)) {
				return "nb";
			} else if ("no-NO".equals(lang)) {
				return "nb-NO";
			}
			return lang;
		}
		return value.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!super.equals(this)) {
			return false;
		}
		if (!(obj instanceof LocaleConverter)) {
			return false;
		}
		LocaleConverter con = cast(obj);
		if (!eq(this.getDefaultValue(), con.getDefaultValue())) {
			return false;
		}
		return true;
	}

	public boolean isToLanguageTag() {
		return toLanguageTag;
	}

	public LocaleConverter setToLanguageTag(boolean toLanguageTag) {
		this.toLanguageTag = toLanguageTag;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return this.getClass().getName().hashCode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public Locale copy(Object obj) {
		if (obj == null) {
			return null;
		}
		return (Locale) convertObject(obj);
	}
}
