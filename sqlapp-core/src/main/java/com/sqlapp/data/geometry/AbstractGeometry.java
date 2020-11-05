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
package com.sqlapp.data.geometry;

import java.io.Serializable;

import com.sqlapp.util.HashCodeBuilder;

public abstract class AbstractGeometry implements Serializable, Cloneable{


	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -8854804404764251294L;

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (this==obj){
			return true;
		}
		if (obj==null){
			return false;
		}
		if (!(obj instanceof AbstractGeometry)){
			return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		HashCodeBuilder builder=new HashCodeBuilder();
		hashCode(builder);
		return builder.hashCode();
	}
	
	protected void hashCode(HashCodeBuilder builder){
		
	}
	
	public abstract int getDimension();
	
	/**
	 * 文字列からオブジェクトへ値を設定します
	 * @param text
	 */
	public abstract AbstractGeometry setValue(String text);

}
