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

import com.sqlapp.data.geometry.Path;
import com.sqlapp.data.geometry.Path3D;
import com.sqlapp.data.geometry.Polygon;
import com.sqlapp.data.geometry.Polygon3D;
/**
 * PolygonType Converter
 * @author SATOH
 *
 */
public class PolygonConverter extends AbstractConverter<Polygon> implements NewValue<Polygon>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7480426186864635353L;

	@Override
	public Polygon convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof Polygon){
			return (Polygon)value;
		}else if (value instanceof Polygon3D){
			return ((Polygon3D)value).toLowerDimension();
		}else if (value instanceof Path){
			return ((Path)value).toPolygon();
		}else if (value instanceof Path3D){
			return ((Path3D)value).toPolygon().toLowerDimension();
		}
		Polygon obj=new Polygon();
		obj.setValue(value.toString());
		return obj;
	}
	
	@Override
	public String convertString(Polygon value) {
		if (value==null){
			return null;
		}
		return value.toString();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==this){
			return true;
		}
		if (!(obj instanceof PolygonConverter)){
			return false;
		}
		PolygonConverter con=cast(obj);
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
	public Polygon copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Polygon)convertObject(obj);
	}

	@Override
	public Polygon newValue() {
		return new Polygon();
	}
}
