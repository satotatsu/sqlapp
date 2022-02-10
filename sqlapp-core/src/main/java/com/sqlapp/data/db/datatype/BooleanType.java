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

import com.sqlapp.data.converter.BooleanConverter;
import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.db.datatype.BitType.BitNumericTypeHandler;
import com.sqlapp.data.db.datatype.BitType.BitStringTypeHandler;

/**
 * BITを表す型
 * @author satoh
 *
 */
public class BooleanType extends AbstractNoSizeType<BooleanType>{

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * Booleanのコンバータ
	 */
	protected Converter<Boolean> converter=new BooleanConverter();
	/**
	 * コンストラクタ
	 */
	public BooleanType(){
		this(DataType.BOOLEAN.getTypeName());
	}

	/**
	 * コンストラクタ
	 */
	public BooleanType(String dataTypeName){
		this.setDataType(DataType.BOOLEAN);
		initialize(dataTypeName);
		this.setUnsigned(true);
		setDefaultValueLiteral("FALSE");
	}

	/**
	 * BITをStringとして扱う設定
	 */
	public BooleanType setAsStringType(){
		this.setJdbcTypeHandler(new BitStringTypeHandler(this.getDataType().getJdbcType(), converter));
		setDefaultValueLiteral("'0'");
		return this;
	}

	/**
	 * BITを数値として扱う設定
	 */
	public BooleanType setAsNumericType(){
		this.setJdbcTypeHandler(new BitNumericTypeHandler(this.getDataType().getJdbcType(), converter));
		setDefaultValueLiteral("0");
		return this;
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.datatype.DbDataType#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof BooleanType)){
			return false;
		}
		return true;
	}
}
