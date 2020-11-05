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
import static com.sqlapp.util.CommonUtils.*;
import com.sqlapp.util.CommonUtils;
import java.io.Serializable;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.util.ToStringBuilder;
/**
 * JDBCのバインドパラメタを管理するクラス
 * @author satoh
 *
 */
public final class BindParameter implements Serializable, Cloneable{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 802393409053455937L;
	/**
	 * パラメタ名
	 */
    private String name = null;
    /**
     * PreparedStatementのパラメタ名(?固定)
     */
    private String bindingName = null;
    /**
     * JDBC型に対応した型
     */
    private DataType type=null;
    /**
     * パラメタ値
     */
    private Object value=null;
    /**
     * パラメタ位置
     */
    private int ordinal = 0;
    /**
     * パラメタ入出力方向
     */
    private ParameterDirection direction = ParameterDirection.Input;
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	public String getBindingName() {
		return bindingName;
	}
	public void setBindingName(String bindingName) {
		this.bindingName = bindingName;
	}
	public Object getValue() {
		return value;
	}
	public void setValue(Object value) {
		this.value = value;
	}
	public int getOrdinal() {
		return ordinal;
	}
	public void setOrdinal(int ordinal) {
		this.ordinal = ordinal;
	}
	public ParameterDirection getDirection() {
		return direction;
	}
	public void setDirection(ParameterDirection direction) {
		this.direction = direction;
	}

	public DataType getType() {
		return type;
	}

	public void setType(DataType type) {
		this.type = type;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString(){
		ToStringBuilder builder=new ToStringBuilder();
		builder.add("name", name);
		builder.add("bindingName", bindingName);
		builder.add("type", type);
		builder.add("ordinal", ordinal);
		builder.add("direction", direction);
		builder.add("value", value);
		return builder.toString();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public BindParameter clone(){
		BindParameter clone=new BindParameter();
		clone.setBindingName(this.bindingName);
		clone.setDirection(this.direction);
		clone.setType(this.type);
		clone.setName(this.name);
		clone.setOrdinal(this.ordinal);
		return clone;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode(){
		return CommonUtils.hashCode(name, bindingName, type, ordinal, direction, value);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (obj==null){
			return false;
		}
		if (obj==this){
			return true;
		}
		if (!(obj instanceof BindParameter)){
			return false;
		}
		BindParameter val=(BindParameter)obj;
		if (!eq(this.name, val.name)){
			return false;
		}
		if (!eq(this.bindingName, val.bindingName)){
			return false;
		}
		if (!eq(this.direction, val.direction)){
			return false;
		}
		if (!eq(this.type, val.type)){
			return false;
		}
		if (!eq(this.ordinal, val.ordinal)){
			return false;
		}
		if (!eq(this.value, val.value)){
			return false;
		}
		return true;
	}
	
}
