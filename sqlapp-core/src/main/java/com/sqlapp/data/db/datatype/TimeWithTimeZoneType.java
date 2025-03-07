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

import com.sqlapp.data.converter.TimeConverter;
import com.sqlapp.data.converter.ZonedDateTimeConverter;

/**
 * TIME_WITH_TIMEZONEを表す型
 * @author satoh
 *
 */
public class TimeWithTimeZoneType extends TimeType{

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * コンバータ
	 */
	private TimeConverter converter=null;

	/**
	 * コンストラクタ
	 */
	public TimeWithTimeZoneType(){
		this(DataType.TIME_WITH_TIMEZONE.getTypeName());
	}
	
	/**
	 * コンストラクタ
	 */
	public TimeWithTimeZoneType(String dataTypeName){
		this.setDataType(DataType.TIME_WITH_TIMEZONE);
		initialize(dataTypeName);
		this.setCreateFormat("TIME(", ") WITH TIMEZONE");
		this.setFormats("TIME\\s*(\\s*[0-9]+\\s*)\\s*WITH\\s+TIMEZONE\\s*"
				, "TIME\\s+WITH\\s+TIME\\s*ZONE\\s*"
		);
		this.addFormats("TIMETZ\\s*\\(\\s*([0-9])+\\s*\\)\\s*"
				, "TIMESTZ\\s*"
		);
		converter=new TimeConverter();
		converter.setZonedDateTimeConverter(ZonedDateTimeConverter.newInstance().setParseFormats("H:m:s.SSS Z"
					, "H:m:s.SSS z"
					, "H:m:s.SSS"
					, "H:m:s Z"
					, "H:m:s"
					, "H:m Z"
					, "H:m"
					).setFormat("HH:mm:ss.SSS Z"));
		this.setJdbcTypeHandler(new DefaultJdbcTypeHandler(this.getDataType().getJdbcType(), converter));
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
		if (!(obj instanceof TimeWithTimeZoneType)){
			return false;
		}
		return true;
	}
}
