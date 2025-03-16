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
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

/**
 * URIType Converter
 * 
 * @author SATOH
 *
 */
public class URIConverter extends AbstractConverter<URI> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7894583139837528990L;

	@Override
	public URI convertObject(Object value) {
		if (isEmpty(value)) {
			return getDefaultValue();
		} else if (value instanceof URI) {
			return (URI) value;
		} else if (value instanceof Path) {
			return ((Path) value).toUri();
		} else if (value instanceof URL) {
			try {
				return ((URL) value).toURI();
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		} else if (value instanceof File) {
			return ((File) value).toURI();
		} else if (value instanceof String) {
			URI url;
			try {
				url = new URI((String) value);
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
			return url;
		}
		try {
			return new URI(value.toString());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String convertString(URI value) {
		if (value == null) {
			return null;
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
		if (!(obj instanceof URIConverter)) {
			return false;
		}
		URIConverter con = cast(obj);
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
	public URI copy(Object obj) {
		if (obj == null) {
			return null;
		}
		return (URI) convertObject(obj);
	}
}
