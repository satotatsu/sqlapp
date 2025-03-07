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

import org.geolatte.geom.codec.Wkt;
import org.geolatte.geom.jts.JTS;

/**
 * GeometryType Converter
 * @author SATOH
 *
 */
public class GeometryConverter extends AbstractConverter<org.geolatte.geom.Geometry<?>>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7894583139837528990L;

	@Override
	public org.geolatte.geom.Geometry<?> convertObject(final Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof org.geolatte.geom.Geometry){
			return (org.geolatte.geom.Geometry<?>)value;
		}else if (value instanceof String){
			return Wkt.fromWkt((String)value);
		}else if (value instanceof org.locationtech.jts.geom.Geometry){
			return JTS.from((org.locationtech.jts.geom.Geometry)value);
		}
		throw new IllegalArgumentException("value="+value);
	}	

	@Override
	public String convertString(final org.geolatte.geom.Geometry<?> value) {
		if (value==null){
			return null;
		}
		return Wkt.toWkt(value);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
		if (!super.equals(this)){
			return false;
		}
		if (!(obj instanceof GeometryConverter)){
			return false;
		}
		final GeometryConverter con=cast(obj);
		if (!eq(this.getDefaultValue(), con.getDefaultValue())){
			return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		return this.getClass().getName().hashCode();
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	@Override
	public org.geolatte.geom.Geometry<?> copy(final Object obj){
		if (obj==null){
			return null;
		}
		return convertObject(obj);
	}
}
