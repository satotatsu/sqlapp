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
 * INTERVALを表す型
 * @author satoh
 *
 */
public class IntervalType extends AbstractPrecisionType<IntervalType>{
	/** serialVersionUID */
	private static final long serialVersionUID = -7497350633746969197L;
	/**
	 * コンストラクタ
	 */
	public IntervalType(){
		this(DataType.INTERVAL.getTypeName());
	}
	
	protected IntervalType(String dataTypeName){
		this.setDataType(DataType.INTERVAL);
		this.setJdbcTypeHandler(new StringTypeHandler(this.getDataType()));
		initialize(dataTypeName);
		this.setDefaultPrecision(0);
		this.setCreateFormat("INTERVAL");
		this.setFormats("INTERVAL\\s*\\(\\s*([0-9]+)\\s*\\)\\s*"
			, "INTERVAL\\s*"
		);
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
		if (!(obj instanceof IntervalType)){
			return false;
		}
		return true;
	}
}
