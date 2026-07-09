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

package com.sqlapp.data.converter;

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.FileUtils.read;

import java.io.Reader;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.SQLXML;

import com.sqlapp.util.JsonConverter;

/**
 * Json文字列へのコンバーター
 * 
 * @author SATOH
 *
 */
public class JsonStringConverter extends AbstractConverter<String> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7755839975092182747L;

	private JsonConverter jsonConverter = new JsonConverter();

	public JsonStringConverter() {
	}

	@Override
	public String convertObject(final Object value) {
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (value == null) {
			return getDefaultValue();
		} else if (value instanceof String) {
			return (String) value;
		} else if (value instanceof Reader) {
			final Reader reader = cast(value);
			return read(reader);
		} else if (value instanceof Clob) {
			final Clob lob = (Clob) value;
			try {
				return lob.getSubString(1, (int) lob.length());
			} catch (final SQLException e) {
				throw new RuntimeException(e);
			}
		} else if (value instanceof SQLXML) {
			final SQLXML sqlxml = (SQLXML) value;
			try {
				try {
					return sqlxml.getString();
				} catch (final SQLException e) {
					throw new RuntimeException(e);
				}
			} finally {
				try {
					sqlxml.free();
				} catch (final SQLException e) {
				}
			}
		}
		return toJsonString(value);
	}

	private String toJsonString(Object obj) {
		return jsonConverter.toJsonString(obj);
	}

	@Override
	public String convertString(final String value) {
		return value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof JsonStringConverter)) {
			return false;
		}
		final JsonStringConverter con = cast(obj);
		if (!eq(this.getDefaultValue(), con.getDefaultValue())) {
			return false;
		}
		return true;
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
	@Override
	public String copy(final Object obj) {
		if (obj == null) {
			return null;
		}
		return convertObject(obj);
	}
}