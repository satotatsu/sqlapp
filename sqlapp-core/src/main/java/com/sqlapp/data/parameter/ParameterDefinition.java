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
package com.sqlapp.data.parameter;

import com.sqlapp.data.AbstractDto;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.HashCodeBuilder;
import com.sqlapp.util.ToStringBuilder;

public class ParameterDefinition extends AbstractDto{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5239717055561918707L;
	
	public static final String ROW_KEY_PARANETER_NAME="_row";
	
	public static final String OFFSET_KEY_PARANETER_NAME="_offset";

	public static final String COUNTSQL_KEY_PARANETER_NAME="_countSql";
	
	public static final String ORDER_BY_KEY_PARANETER_NAME="_orderBy";
	
	public ParameterDefinition(){
		
	}

	public ParameterDefinition(String name){
		this.name=normalize(name);
		this.type=null;
	}
	
	public ParameterDefinition(String name, String type){
		this.name=normalize(name);
		this.type=type;
	}

	private String normalize(String name){
		String[] vals=name.split("[+\\-/*]");
		for(String val:vals){
			val=CommonUtils.trim(val);
			if (CommonUtils.isEmpty(val)){
				continue;
			}
			if (val.startsWith("'")&&val.endsWith("'")){
				continue;
			}
			if (val.startsWith("\"")&&val.endsWith("\"")){
				continue;
			}
			return val;
		}
		return name;
	}
	
	private String name;
	private String type;
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}
	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}
	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}
	/**
	 * @param type the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}
	
	@Override
	protected  void buildToString(ToStringBuilder builder){
		builder.add("name", getName());
		builder.add("type", getType());
	}
	

	/* (non-Javadoc)
	 * @see java.lang.Object#buildHashCode()
	 */
	@Override
	protected void buildHashCode(HashCodeBuilder builder){
		builder.append(getName());
	}
	
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof ParameterDefinition)){
			return false;
		}
		ParameterDefinition cst=ParameterDefinition.class.cast(obj);
		if (!CommonUtils.eq(this.getName(), cst.getName())){
			return false;
		}
		if (!CommonUtils.eq(this.getType(), cst.getType())){
			return false;
		}
		return true;
	}
	
	@Override
	public ParameterDefinition clone(){
		return (ParameterDefinition)super.clone();
	}
}
