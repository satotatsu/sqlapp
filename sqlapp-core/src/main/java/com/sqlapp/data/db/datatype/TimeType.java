/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.datatype;

/**
 * TIMEを表す型
 * @author satoh
 *
 */
public class TimeType extends AbstractPrecisionType<TimeType>{

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * コンストラクタ
	 */
	public TimeType(){
		this(DataType.TIME.getTypeName());
	}
	
	/**
	 * コンストラクタ
	 */
	public TimeType(final String dataTypeName){
		this.setDataType(DataType.TIME);
		initialize(dataTypeName);
		setLiteral("{t '", "'}");
		this.setCreateFormat(this.getDataType().toString()+"(", ")");
		this.addFormats("TIME\\s*\\(\\s*([0-9]+)\\s*\\)\\s*WITHOUT\\s+TIMEZONE");
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
	public boolean equals(final Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof TimeType)){
			return false;
		}
		return true;
	}
}
