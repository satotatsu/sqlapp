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

import com.sqlapp.data.geometry.Circle;
import com.sqlapp.data.geometry.Circle3D;
/**
 * CircleType Converter
 * @author SATOH
 *
 */
public class CircleConverter extends AbstractConverter<Circle> implements NewValue<Circle>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7480426186864635353L;

	@Override
	public Circle convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof Circle){
			return (Circle)value;
		}else if (value instanceof Circle3D){
			return ((Circle3D)value).toLowerDimension();
		}else if (value instanceof String){
			Circle obj=new Circle();
			obj.setValue((String)value);
			return obj;
		}
		throw new UnsupportedOperationException(value.getClass().getName()+" can't convert Point.");
	}
	
	@Override
	public String convertString(Circle value) {
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
		if (!super.equals(this)){
			return false;
		}
		if (!(obj instanceof CircleConverter)){
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
	public Circle copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Circle)convertObject(obj);
	}

	@Override
	public Circle newValue() {
		return new Circle();
	}
}
