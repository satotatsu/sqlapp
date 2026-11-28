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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import com.sqlapp.data.converter.OffsetDateTimeConverter;

/**
 * TIMESTAMP_WITH_TIMEZONEを表す型
 * 
 * @author satoh
 *
 */
public class TimestampWithTimeZoneType extends TimestampType {

	/** serialVersionUID */
	private static final long serialVersionUID = -8658816953027318522L;
	/**
	 * コンバータ
	 */
	private OffsetDateTimeConverter converter = null;

	/**
	 * コンストラクタ
	 */
	public TimestampWithTimeZoneType() {
		this(DataType.TIMESTAMP_WITH_TIMEZONE.getTypeName());
	}

	protected TimestampWithTimeZoneType(final String dataTypeName) {
		this.setDataType(DataType.TIMESTAMP_WITH_TIMEZONE);
		initialize(dataTypeName);
		setLiteral("{ts '", "'}");
		this.setCreateFormat("TIMESTAMP(", ") WITH TIME ZONE");
		if (this.getDataType().matchName(dataTypeName)) {
			this.setColumnTypeMatcher("TIMESTAMP", "\\s*WITH\\s+TIME\\s*ZONE");
			this.addColumnTypeMatcher("TIMESTAMPTZ", "");
		}
		// Values are OffsetDateTime, the JDBC 4.2 type of TIMESTAMP WITH TIME ZONE.
		// A region (Asia/Tokyo) is reduced to its offset; see
		// docs/development/time-zone-values.md.
		converter = OffsetDateTimeConverter.newInstance()
				.setParseFormats(DateTimeFormatter.ISO_OFFSET_DATE_TIME, ZonedTemporalFormats.dateTimeParser())
				.setFormat(ZonedTemporalFormats.dateTimeText());
		this.setConverter(converter);
		this.setFormatter(converter);
		this.setJdbcTypeHandler(
				new JavaTimeJdbcTypeHandler<>(this.getDataType().getJdbcType(), OffsetDateTime.class, converter));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.DbDataType#hashCode()
	 */
	@Override
	public int hashCode() {
		return super.hashCode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.datatype.DbDataType#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof TimestampWithTimeZoneType)) {
			return false;
		}
		return true;
	}
}
