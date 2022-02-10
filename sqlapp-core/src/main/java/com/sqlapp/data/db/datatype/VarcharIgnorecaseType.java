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

import com.sqlapp.data.converter.UpperStringConverter;

/**
 * VARCHAR_IGNORECASEを表す型
 * @author satoh
 *
 */
public class VarcharIgnorecaseType extends AbstractLengthType<VarcharIgnorecaseType>{

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * Stringのコンバータ
	 */
	protected UpperStringConverter converter=new UpperStringConverter();
	/**
	 * コンストラクタ
	 */
	public VarcharIgnorecaseType(){
		this(DataType.VARCHAR_IGNORECASE.getTypeName());
	}

	protected VarcharIgnorecaseType(String dataTypeName){
		this.setDataType(DataType.VARCHAR_IGNORECASE);
		this.setJdbcTypeHandler(new DefaultJdbcTypeHandler(this.getDataType().getJdbcType(), converter));
		initialize(dataTypeName);
		this.setCaseSensitive(false);
		setLiteralPrefix("'");
		setLiteralSuffix("'");
		setDefaultValueLiteral(withLiteral(""));
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
		if (!(obj instanceof VarcharIgnorecaseType)){
			return false;
		}
		return true;
	}
}
