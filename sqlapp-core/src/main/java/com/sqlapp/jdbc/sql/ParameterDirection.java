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

package com.sqlapp.jdbc.sql;
/**
 * パラメタの種類
 * @author satoh
 *
 */
public enum ParameterDirection {
	Input("IN"), Output("OUT"), Inout("INOUT");
	
	private final String value;
	
	ParameterDirection(String value){
		this.value=value;
	}
	
	public static ParameterDirection parse(String text){
		if (text==null||text.length()==0){
			return null;
		}
		String up=text.toLowerCase().replace("/", "");
		if (up.startsWith("inout")){
			return Inout;
		}else if (up.startsWith("in")){
			return Input;
		}else{
			return Output;
		}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Enum#toString()
	 */
	@Override
	public String toString(){
		return this.value;
	}
}
