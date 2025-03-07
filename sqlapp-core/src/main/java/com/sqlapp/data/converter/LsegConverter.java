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

import com.sqlapp.data.geometry.Line;
import com.sqlapp.data.geometry.Line3D;
import com.sqlapp.data.geometry.Lseg;
import com.sqlapp.data.geometry.Lseg3D;
import com.sqlapp.data.geometry.Box;
import com.sqlapp.data.geometry.Box3D;
/**
 * LsegType Converter
 * @author SATOH
 *
 */
public class LsegConverter extends AbstractConverter<Lseg> implements NewValue<Lseg>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7480426186864635353L;

	@Override
	public Lseg convertObject(Object value) {
		if (isEmpty(value)){
			return getDefaultValue();
		}else if (value instanceof Lseg){
			return (Lseg)value;
		}else if (value instanceof Lseg3D){
			return ((Lseg3D)value).toLowerDimension();
		}else if (value instanceof Line){
			return ((Line)value).toLseg();
		}else if (value instanceof Line3D){
			return ((Line3D)value).toLseg().toLowerDimension();
		}else if (value instanceof Box){
			return ((Box)value).toLseg();
		}else if (value instanceof Box3D){
			return ((Box3D)value).toLseg().toLowerDimension();
		}
		Lseg obj=new Lseg();
		obj.setValue(value.toString());
		return obj;
	}
	
	@Override
	public String convertString(Lseg value) {
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
		if (!(obj instanceof LsegConverter)){
			return false;
		}
		LsegConverter con=cast(obj);
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
	public Lseg copy(Object obj){
		if (obj==null){
			return null;
		}
		return (Lseg)convertObject(obj);
	}

	@Override
	public Lseg newValue() {
		return new Lseg();
	}
}
