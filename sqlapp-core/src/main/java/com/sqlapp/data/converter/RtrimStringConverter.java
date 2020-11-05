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
/**
 * RTRIM付き文字列のコンバーター
 * @author SATOH
 *
 */
public class RtrimStringConverter extends StringConverter{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -4052731861912428486L;

	public RtrimStringConverter(){
		super();
	}

	@Override
	public String convertObject(Object value) {
		if (value==null) {
			return getDefaultValue();
		}
		return rtrim(super.convertObject(value));
	}

	@Override
	public String convertString(String value) {
		return rtrim(value);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==this){
			return true;
		}
		if (!super.equals(this)){
			return false;
		}
		if (!(obj instanceof RtrimStringConverter)){
			return false;
		}
		RtrimStringConverter con=cast(obj);
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
}