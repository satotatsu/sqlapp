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

import com.sqlapp.data.geometry.Box;
import com.sqlapp.data.geometry.Box3D;
import com.sqlapp.data.geometry.Line;
import com.sqlapp.data.geometry.Line3D;
import com.sqlapp.data.geometry.Lseg;
import com.sqlapp.data.geometry.Lseg3D;
/**
 * BoxType Converter
 * @author SATOH
 *
 */
public class BoxConverter extends AbstractConverter<Box> implements NewValue<Box>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7480426186864635353L;

	@Override
	public Box convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof Box){
			return (Box)value;
		}else if (value instanceof Box3D){
			return ((Box3D)value).toLowerDimension();
		}else if (value instanceof Line){
			return ((Line)value).toBox();
		}else if (value instanceof Line3D){
			return ((Line3D)value).toBox().toLowerDimension();
		}else if (value instanceof Lseg){
			return ((Lseg)value).toBox();
		}else if (value instanceof Lseg3D){
			return ((Lseg3D)value).toBox().toLowerDimension();
		}
		Box obj=new Box();
		obj.setValue(value.toString());
		return obj;
	}
	
	@Override
	public String convertString(Box value) {
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
		if (!(obj instanceof BoxConverter)){
			return false;
		}
		BoxConverter con=cast(obj);
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
	public Box copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Box)convertObject(obj);
	}

	@Override
	public Box newValue() {
		return new Box();
	}
}
