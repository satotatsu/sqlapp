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


import com.sqlapp.data.converter.StringConverter;
/**
 * ENUMを表す型
 * @author satoh
 *
 */
public class SetType extends AbstractValueSetType<SetType>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -8356115270582233611L;
	/**
	 * Stringのコンバータ
	 */
	protected StringConverter converter=new StringConverter();
	/**
	 * コンストラクタ
	 */
	public SetType(){
		this(DataType.SET.getTypeName());
	}
	
	protected SetType(String dataTypeName){
		this.setDataType(DataType.SET);
		initialize(dataTypeName);
		this.setJdbcTypeHandler(new DefaultJdbcTypeHandler(this.getDataType().getJdbcType(), converter));
		setSearchableWithLike(true);
		setCaseSensitive(true);
		setLiteralPrefix("'");
		setLiteralSuffix("'");
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.datatype.DbDataType#hashCode()
	 */
	@Override
	public int hashCode(){
		return super.hashCode();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.datatype.DbDataType#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof SetType)){
			return false;
		}
		return true;
	}
}
