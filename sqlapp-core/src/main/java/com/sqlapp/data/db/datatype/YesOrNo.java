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
package com.sqlapp.data.db.datatype;
/**
 * DBのYES_OR_NO型用のenum
 *
 */
public enum YesOrNo {
	YES, NO;
	
	public static YesOrNo parse(String value){
		if (value==null||value.length()==0){
			return null;
		}
		String uValue=value.toUpperCase();
		if (uValue.startsWith("Y")||uValue.startsWith("T")||"1".equals(value)){
			return YES;
		}
		if (uValue.startsWith("N")||uValue.startsWith("F")||"0".equals(value)){
			return NO;
		}
		return null;
	}
}
