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

import static com.sqlapp.util.CommonUtils.*;

import com.sqlapp.data.geometry.Path;
import com.sqlapp.data.geometry.Path3D;
import com.sqlapp.data.geometry.Polygon;
import com.sqlapp.data.geometry.Polygon3D;
/**
 * PathType Converter
 * @author SATOH
 *
 */
public class PathConverter extends AbstractConverter<Path> implements NewValue<Path>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7480426186864635353L;

	@Override
	public Path convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof Path){
			return (Path)value;
		}else if (value instanceof Path3D){
			return ((Path3D)value).toLowerDimension();
		}else if (value instanceof Polygon){
			return ((Polygon)value).toPath();
		}else if (value instanceof Polygon3D){
			return ((Polygon3D)value).toPath().toLowerDimension();
		}
		Path obj=new Path();
		obj.setValue(value.toString());
		return obj;
	}
	
	@Override
	public String convertString(Path value) {
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
		if (!(obj instanceof PathConverter)){
			return false;
		}
		PathConverter con=cast(obj);
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
	public Path copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Path)convertObject(obj);
	}

	@Override
	public Path newValue() {
		return new Path();
	}
}
