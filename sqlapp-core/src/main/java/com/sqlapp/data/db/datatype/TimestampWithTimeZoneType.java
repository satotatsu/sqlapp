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

import com.sqlapp.data.converter.TimestampConverter;
import com.sqlapp.data.converter.ZonedDateTimeConverter;

/**
 * TIMESTAMP_WITH_TIMEZONEを表す型
 * @author satoh
 *
 */
public class TimestampWithTimeZoneType extends TimestampType{

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * コンバータ
	 */
	private TimestampConverter converter=null;

	/**
	 * コンストラクタ
	 */
	public TimestampWithTimeZoneType(){
		this(DataType.TIMESTAMP_WITH_TIMEZONE.getTypeName());
	}
	
	protected TimestampWithTimeZoneType(final String dataTypeName){
		this.setDataType(DataType.TIMESTAMP_WITH_TIMEZONE);
		initialize(dataTypeName);
		setLiteral("{ts '", "'}");
		this.setCreateFormat("TIMESTAMP(", ") WITH TIMEZONE");
		this.setFormats("TIMESTAMP\\s*\\(\\s*([0-9])+\\s*\\)\\s*WITH\\s+TIME\\s*ZONE\\s*"
				, "TIMESTAMP\\s+WITH\\s+TIME\\s*ZONE\\s*"
		);
		this.addFormats("TIMESTAMPTZ\\s*\\(\\s*([0-9])+\\s*\\)\\s*"
				, "TIMESTAMPTZ\\s*"
		);
		converter=new TimestampConverter();
		converter.setZonedDateTimeConverter(
			ZonedDateTimeConverter.newInstance().setParseFormats("uuuu-M-d H:m:s.SSS Z"
					, "uuuu-M-d H:m:s.SSS z"
					, "uuuu-M-d H:m:s.SSS"
					, "uuuu-M-d H:m:s Z"
					, "uuuu-M-d H:m:s"
					, "uuuu-M-d H:m Z"
					, "uuuu-M-d H:m"
					, "uuuu-M-d"
					).setFormat("uuuu-MM-dd HH:mm:ss.SSS Z"));
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
	public boolean equals(final Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof TimestampWithTimeZoneType)){
			return false;
		}
		return true;
	}
}
