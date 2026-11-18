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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import com.sqlapp.data.geometry.Box;
import com.sqlapp.data.geometry.Line;
import com.sqlapp.data.geometry.Lseg;

/**
 * LineType Converter
 * 
 * @author SATOH
 *
 */
public class LineConverter extends AbstractConverter<Line> implements Supplier<Line> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7480426186864635353L;

	@Override
	public Line convertObject(Object value) {
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (isEmpty(value)) {
			return getDefaultValue();
		} else if (value instanceof org.geolatte.geom.Geometry<?> geometry) {
			return SpatialGeometryConversions.fromSpatial(geometry, Line.class);
		} else if (value instanceof Line) {
			return (Line) value;
		} else if (value instanceof Lseg) {
			return ((Lseg) value).toLine();
		} else if (value instanceof Box) {
			return ((Box) value).toLine();
		}
		Line obj = new Line();
		obj.setValue(value.toString());
		return obj;
	}

	@Override
	public String format(Line value) {
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
		if (!(obj instanceof LineConverter)) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	public Line copy(Object obj) {
		if (obj == null) {
			return null;
		}
		return (Line) convertObject(obj);
	}

	@Override
	public Line get() {
		return new Line();
	}
}
