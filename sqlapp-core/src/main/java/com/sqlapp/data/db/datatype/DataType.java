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

import static com.sqlapp.util.CommonUtils.cloneMap;
import static com.sqlapp.util.CommonUtils.enumMap;
import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.map;
import static com.sqlapp.util.CommonUtils.upperMap;
import static com.sqlapp.util.CommonUtils.upperSet;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.JDBCType;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.util.GeometryUtils;
import com.sqlapp.data.geometry.Box;
import com.sqlapp.data.geometry.Circle;
import com.sqlapp.data.geometry.Line;
import com.sqlapp.data.geometry.Lseg;
import com.sqlapp.data.geometry.Path;
import com.sqlapp.data.geometry.Point;
import com.sqlapp.data.geometry.Polygon;
import com.sqlapp.data.interval.Interval;
import com.sqlapp.data.interval.IntervalDay;
import com.sqlapp.data.interval.IntervalDayToHour;
import com.sqlapp.data.interval.IntervalDayToMinute;
import com.sqlapp.data.interval.IntervalDayToSecond;
import com.sqlapp.data.interval.IntervalHour;
import com.sqlapp.data.interval.IntervalHourToMinute;
import com.sqlapp.data.interval.IntervalHourToSecond;
import com.sqlapp.data.interval.IntervalMinute;
import com.sqlapp.data.interval.IntervalMinuteToSecond;
import com.sqlapp.data.interval.IntervalMonth;
import com.sqlapp.data.interval.IntervalSecond;
import com.sqlapp.data.interval.IntervalYear;
import com.sqlapp.data.interval.IntervalYearToDay;
import com.sqlapp.data.interval.IntervalYearToMonth;
import com.sqlapp.util.CommonUtils;

/**
 * <code>java.sql.JDBCType<code>だけでは対応しにくいので
 * enum型として拡張したTypes
 * 
 * @author satoh
 * 
 */
public enum DataType {
	ARRAY(java.sql.JDBCType.ARRAY, MetaType.OTHER){
	},
	// 整数型
	BIT(java.sql.JDBCType.BIT, Boolean.class, MetaType.NUMERIC){
		@Override
		public DataType getSurrogate() {
			return BOOLEAN;
		}
		@Override
		public DataType getUpperSurrogate() {
			return TINYINT;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Boolean;
		}
	},
	/** 8bit整数型 */
	TINYINT(java.sql.JDBCType.TINYINT, Byte.class,MetaType.NUMERIC){
		@Override
		public DataType getUpperSurrogate() {
			return SMALLINT;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.TinyInt;
		}
	},
	/** 16bit整数型 */
	SMALLINT(java.sql.JDBCType.SMALLINT, Short.class,
			MetaType.NUMERIC){
		@Override
		public DataType getUpperSurrogate() {
			return MEDIUMINT;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.SmallInt;
		}
	},
	/** 24bit整数型 */
	MEDIUMINT(java.sql.JDBCType.INTEGER, Integer.class,MetaType.NUMERIC){
		@Override
		public DataType getUpperSurrogate() {
			return INT;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Integer;
		}
	},
	/** 32bit整数型 */
	INT(java.sql.JDBCType.INTEGER, Integer.class, MetaType.NUMERIC){
		@Override
		public DataType getUpperSurrogate() {
			return BIGINT;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Integer;
		}
	},
	/** 64bit整数型 */
	BIGINT(java.sql.JDBCType.BIGINT, Long.class, MetaType.NUMERIC){
		@Override
		public DataType getUpperSurrogate() {
			return DECIMAL;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.BigInt;
		}
	},
	/** 128bit整数型 */
	HUGEINT(java.sql.JDBCType.DECIMAL, BigInteger.class, MetaType.NUMERIC){
		@Override
		public DataType getUpperSurrogate() {
			return DECIMAL;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Decimal;
		}
	},
	/** 16bit整数型(IDENTITY) */
	SMALLSERIAL(java.sql.JDBCType.SMALLINT, Short.class,
			MetaType.NUMERIC){
		@Override
		public boolean isAutoIncrementable(){
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return SMALLINT;
		}
		@Override
		public DataType getUpperSurrogate() {
			return SERIAL;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.SmallInt;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/** 32bit整数型(IDENTITY) */
	SERIAL(java.sql.JDBCType.INTEGER, Integer.class, MetaType.NUMERIC){
		@Override
		public boolean isAutoIncrementable(){
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return INT;
		}
		@Override
		public DataType getUpperSurrogate() {
			return BIGSERIAL;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Integer;
		}
	},
	/** 64bit整数型(IDENTITY) */
	BIGSERIAL(java.sql.JDBCType.BIGINT, Long.class, MetaType.NUMERIC){
		@Override
		public boolean isAutoIncrementable(){
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return BIGINT;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}	
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.BigInt;
		}
	},
	/** 8bit符号なし整数型 */
	UTINYINT(java.sql.JDBCType.SMALLINT, "TINYINT UNSIGNED", Short.class,MetaType.NUMERIC){
		@Override
		public DataType getSurrogate() {
			return SMALLINT;
		}
		@Override
		public DataType getUpperSurrogate() {
			return USMALLINT;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}	
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.UnsignedTinyInt;
		}
	},
	/** 16bit符号なし整数型 */
	USMALLINT(java.sql.JDBCType.INTEGER, "SMALLINT UNSIGNED", Integer.class, MetaType.NUMERIC){
		@Override
		public DataType getSurrogate() {
			return MEDIUMINT;
		}
		@Override
		public DataType getUpperSurrogate() {
			return UMEDIUMINT;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.UnsignedSmallInt;
		}
	},
	/** 24bit符号なし整数型 */
	UMEDIUMINT(java.sql.JDBCType.INTEGER, "MEDIUMINT UNSIGNED", Integer.class, MetaType.NUMERIC){
		@Override
		public DataType getSurrogate() {
			return INT;
		}
		@Override
		public DataType getUpperSurrogate() {
			return UINT;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Integer;
		}
	},
	/** 32bit符号なし整数型 */
	UINT(java.sql.JDBCType.BIGINT, "INT UNSIGNED", Long.class, MetaType.NUMERIC){
		@Override
		public DataType getSurrogate() {
			return BIGINT;
		}
		@Override
		public DataType getUpperSurrogate() {
			return UBIGINT;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.UnsignedInt;
		}
	},
	/** 64bit符号なし整数型 */
	UBIGINT(java.sql.JDBCType.DECIMAL, "BIGINT UNSIGNED", BigInteger.class, MetaType.NUMERIC){
		@Override
		public DataType getSurrogate() {
			return DECIMAL;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.UnsignedBigInt;
		}
	},
	/** 単精度浮動小数型 */
	REAL(java.sql.JDBCType.REAL, Float.class, MetaType.NUMERIC){
		@Override
		public DataType getUpperSurrogate() {
			return DOUBLE;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Single;
		}
	},
	/** 倍精度浮動小数型 */
	DOUBLE(java.sql.JDBCType.DOUBLE, Double.class,	MetaType.NUMERIC){
		@Override
		public DataType getSurrogate() {
			return FLOAT;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Double;
		}
	},
	/** 可変浮動小数点型 */
	FLOAT(java.sql.JDBCType.FLOAT, BigDecimal.class,MetaType.NUMERIC){
		@Override
		public DataType getSurrogate() {
			return DECIMALFLOAT;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Double;
		}
	},
	/** 10進数可変浮動小数点型 */
	DECIMALFLOAT(java.sql.JDBCType.DECIMAL, BigDecimal.class, MetaType.NUMERIC){
		@Override
		public DataType getSurrogate() {
			return DECIMAL;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Double;
		}
	},
	/** BOOLEAN型 */
	BOOLEAN(java.sql.JDBCType.BOOLEAN, Boolean.class, MetaType.NUMERIC){
		@Override
		public DataType getSurrogate() {
			return BIT;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Boolean;
		}
	},
	/** 10進数型 */
	DECIMAL(java.sql.JDBCType.DECIMAL, BigDecimal.class, MetaType.NUMERIC){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public boolean isFixedScale() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return NUMERIC;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Decimal;
		}
	},
	/** 10進数型 */
	NUMERIC(java.sql.JDBCType.NUMERIC, BigDecimal.class, MetaType.NUMERIC){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public boolean isFixedScale() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return DECIMAL;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Decimal;
		}
	},
	/** 通貨型(32bit) */
	SMALLMONEY(java.sql.JDBCType.DECIMAL, BigDecimal.class, MetaType.NUMERIC){
		@Override
		public DataType getUpperSurrogate() {
			return MONEY;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Currency;
		}
	},
	/** 通貨型(64bit) */
	MONEY(java.sql.JDBCType.DECIMAL, BigDecimal.class,MetaType.NUMERIC){
		@Override
		public DataType getSurrogate() {
			return DECIMAL;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Currency;
		}
	},
	/** バイナリ */
	BINARY(java.sql.JDBCType.BINARY, byte[].class,MetaType.BINARY){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return VARBINARY;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Binary;
		}
	},
	/** 可変長バイナリ */
	VARBINARY(java.sql.JDBCType.VARBINARY, byte[].class,	MetaType.BINARY){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getUpperSurrogate() {
			return LONGVARBINARY;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Binary;
		}
	},
	/** 可変長バイナリ */
	LONGVARBINARY(java.sql.JDBCType.LONGVARBINARY,	byte[].class, MetaType.BINARY){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return BLOB;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.LongVarBinary;
		}
	},
	/** BLOB */
	BLOB(java.sql.JDBCType.BLOB, byte[].class, MetaType.BINARY){
		@Override
		public DataType getSurrogate() {
			return LONGVARBINARY;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Binary;
		}
	},
	/** 日付 */
	DATE(java.sql.JDBCType.DATE, java.sql.Date.class,MetaType.DATETIME){
		@Override
		public DataType getSurrogate() {
			return DATETIME;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.DBDate;
		}
	},
	/** 日付時刻 */
	SMALLDATETIME(java.sql.JDBCType.TIMESTAMP,	java.util.Date.class, MetaType.DATETIME){
		@Override
		public DataType getUpperSurrogate() {
			return DATETIME;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.DBDate;
		}
	},
	/** DATETIME */
	DATETIME(java.sql.JDBCType.TIMESTAMP, java.util.Date.class,MetaType.DATETIME){
		@Override
		public DataType getUpperSurrogate() {
			return TIMESTAMP;
		}

		@Override
		public DataType getSurrogate() {
			return TIMESTAMP_WITH_TIMEZONE;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.DBTimeStamp;
		}
	},
	/** TIMESTAMP */
	TIMESTAMP(java.sql.JDBCType.TIMESTAMP, Timestamp.class,MetaType.DATETIME){
		@Override
		public boolean isFixedScale() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return DATETIME;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.DBTimeStamp;
		}
	},
	/** TIMESTAMP_WITH_TIMEZONE */
	TIMESTAMP_WITH_TIMEZONE(java.sql.JDBCType.TIMESTAMP_WITH_TIMEZONE,
			"TIMESTAMP WITH TIMEZONE", OffsetDateTime.class, MetaType.DATETIME){
		@Override
		public boolean isFixedScale() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return TIMESTAMP;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.DBTimeStamp;
		}
	},
	/** TIME */
	TIME(java.sql.JDBCType.TIME, Time.class, MetaType.DATETIME){
		@Override
		public boolean isFixedScale() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return DATETIME;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.DBTime;
		}
	},
	/** TIME_WITH_TIMEZONE */
	TIME_WITH_TIMEZONE(java.sql.JDBCType.TIME_WITH_TIMEZONE, "TIME WITH TIMEZONE",
			OffsetTime.class, MetaType.DATETIME){
		@Override
		public boolean isFixedScale() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return TIMESTAMP_WITH_TIMEZONE;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.DBTime;
		}
	},
	// 期間型
	INTERVAL(java.sql.JDBCType.OTHER, Interval.class, MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return TIMESTAMP;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	// 期間型(INTERVAL_YEAR)
	INTERVAL_YEAR(java.sql.JDBCType.OTHER, "INTERVAL YEAR", IntervalYear.class,MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return INTERVAL_YEAR_TO_MONTH;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	}, 
	// 期間型(INTERVAL_MONTH)
	INTERVAL_MONTH(java.sql.JDBCType.OTHER, "INTERVAL MONTH", IntervalMonth.class, MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return INTERVAL_YEAR_TO_MONTH;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	}, 
	// 期間型(INTERVAL_DAY)
	INTERVAL_DAY(java.sql.JDBCType.OTHER, "INTERVAL DAY", IntervalDay.class, MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return INTERVAL_DAY_TO_SECOND;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	}, 
	// 期間型(INTERVAL_HOUR)
	INTERVAL_HOUR(java.sql.JDBCType.OTHER, "INTERVAL HOUR", IntervalHour.class, MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return INTERVAL_DAY_TO_SECOND;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	}, 
	// 期間型(INTERVAL_MINUTE)
	INTERVAL_MINUTE(java.sql.JDBCType.OTHER, "INTERVAL MINUTE", IntervalMinute.class, MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return INTERVAL_DAY_TO_SECOND;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	}, 
	// 期間型(INTERVAL_SECOND)
	INTERVAL_SECOND(java.sql.JDBCType.OTHER, "INTERVAL SECOND",IntervalSecond.class, MetaType.INTERVAL){
		@Override
		public boolean isFixedScale() {
			return false;
		}
		@Override
		public DataType getSurrogate() {
			return INTERVAL_DAY_TO_SECOND;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	}, 
	// 期間型(INTERVAL_YEAR_TO_MONTH)
	INTERVAL_YEAR_TO_MONTH(java.sql.JDBCType.OTHER, "INTERVAL YEAR TO MONTH", IntervalYearToMonth.class, MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return INTERVAL;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	}, 
	// 期間型(INTERVAL_YEAR_TO_DAY)
	INTERVAL_YEAR_TO_DAY(java.sql.JDBCType.OTHER, "INTERVAL YEAR TO DAY", IntervalYearToDay.class, MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return INTERVAL;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	// 期間型(INTERVAL_DAY_TO_HOUR)
	INTERVAL_DAY_TO_HOUR(java.sql.JDBCType.OTHER, "INTERVAL DAY TO HOUR", IntervalDayToHour.class, MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return INTERVAL_DAY_TO_SECOND;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	}, 
	// 期間型(INTERVAL_DAY_TO_MINUTE)
	INTERVAL_DAY_TO_MINUTE(java.sql.JDBCType.OTHER, "INTERVAL DAY TO MINUTE", IntervalDayToMinute.class, MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return INTERVAL_DAY_TO_SECOND;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	}, 
	// 期間型(INTERVAL_DAY_TO_MINUTE)
	INTERVAL_DAY_TO_SECOND(java.sql.JDBCType.OTHER, "INTERVAL DAY TO SECOND", IntervalDayToSecond.class, MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return INTERVAL;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	}, 
	// 期間型(INTERVAL_DAY_TO_MINUTE)
	INTERVAL_MINUTE_TO_SECOND(java.sql.JDBCType.OTHER, "INTERVAL MINUTE TO SECOND",
			IntervalMinuteToSecond.class, MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return INTERVAL_DAY_TO_SECOND;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	}, 
	// 期間型(INTERVAL_HOUR_TO_MINUTE)
	INTERVAL_HOUR_TO_MINUTE(java.sql.JDBCType.OTHER, "INTERVAL HOUR TO MINUTE", IntervalHourToMinute.class, MetaType.INTERVAL){
		@Override
		public DataType getSurrogate() {
			return INTERVAL_DAY_TO_SECOND;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	}, 
	// 期間型(INTERVAL_HOUR_TO_SECOND)
	INTERVAL_HOUR_TO_SECOND(java.sql.JDBCType.OTHER, "INTERVAL HOUR TO SECOND",
			IntervalHourToSecond.class, MetaType.INTERVAL){
		@Override
		public boolean isFixedScale() {
			return false;
		}
		@Override
		public DataType getSurrogate() {
			return INTERVAL_DAY_TO_SECOND;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	//
	CHAR(java.sql.JDBCType.CHAR, String.class, MetaType.CHARACTER){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public DataType getNationalSurrogate() {
			return NCHAR;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Char;
		}
	},
	/** VARCHAR */
	VARCHAR(java.sql.JDBCType.VARCHAR, String.class,MetaType.CHARACTER){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getUpperSurrogate() {
			return LONGVARCHAR;
		}
		@Override
		public DataType getNationalSurrogate() {
			return NVARCHAR;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	/** LONGVARCHAR */
	LONGVARCHAR(java.sql.JDBCType.LONGVARCHAR, String.class,MetaType.CHARACTER){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return LONGNVARCHAR;
		}
		@Override
		public DataType getUpperSurrogate() {
			return CLOB;
		}
		@Override
		public DataType getNationalSurrogate() {
			return LONGNVARCHAR;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.LongVarChar;
		}
	},
	/** CLOB */
	CLOB(java.sql.JDBCType.CLOB, String.class, MetaType.CHARACTER){
		@Override
		public DataType getUpperSurrogate() {
			return VARCHAR;
		}
		@Override
		public DataType getNationalSurrogate() {
			return NCLOB;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.LongVarChar;
		}
	}
	, 
	/** NVARCHAR */
	NVARCHAR(java.sql.JDBCType.NVARCHAR, String.class,	MetaType.CHARACTER){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getUpperSurrogate() {
			return LONGNVARCHAR;
		}
		@Override
		public DataType getNationalSurrogate() {
			return VARCHAR;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarWChar;
		}
	}
	, 
	/** LONGNVARCHAR */
	LONGNVARCHAR(java.sql.JDBCType.LONGNVARCHAR, "LONGNVARCHAR", String.class,
			MetaType.CHARACTER){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getUpperSurrogate() {
			return NCLOB;
		}
		@Override
		public boolean isNationalCharacter() {
			return true;
		}
		@Override
		public DataType getNationalSurrogate() {
			return LONGVARCHAR;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarWChar;
		}
	}
	, 
	/** NCLOB */
	NCLOB(java.sql.JDBCType.NCLOB, String.class, MetaType.CHARACTER){
		@Override
		public DataType getSurrogate() {
			return NVARCHAR;
		}
		@Override
		public boolean isNationalCharacter() {
			return true;
		}
		@Override
		public DataType getNationalSurrogate() {
			return CLOB;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarWChar;
		}
	}
	,
	/** NCHAR */
	NCHAR(java.sql.JDBCType.NCHAR, String.class, MetaType.CHARACTER){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return NVARCHAR;
		}
		@Override
		public boolean isNationalCharacter() {
			return true;
		}
		@Override
		public DataType getNationalSurrogate() {
			return CHAR;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarWChar;
		}
	}
	,
	// For SAP HANA
	/** SHORTTEXT */
	SEARCHABLE_SHORTTEXT(java.sql.JDBCType.VARCHAR, "SHORTTEXT",
			String.class, MetaType.CHARACTER){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public DataType getUpperSurrogate() {
			return SEARCHABLE_TEXT;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	}
	,
	/** TEXT */
	SEARCHABLE_TEXT(java.sql.JDBCType.VARCHAR, "TEXT", String.class,
			MetaType.CHARACTER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	}
	, 
	/** ALPHANUM */
	ALPHANUM(java.sql.JDBCType.VARCHAR, String.class,
			MetaType.CHARACTER){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	//
	/** VARCHAR_IGNORECASE */
	VARCHAR_IGNORECASE(java.sql.JDBCType.VARCHAR, "VARCHAR_IGNORECASE",
			String.class, MetaType.CHARACTER){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	// HiRDB専用
	/** 混在文字 */
	MCHAR(java.sql.JDBCType.VARCHAR, String.class,MetaType.CHARACTER){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return MVARCHAR;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	/** 混在文字 */
	MVARCHAR(java.sql.JDBCType.VARCHAR, String.class,MetaType.CHARACTER){
		@Override
		public boolean isFixedSize() {
			return true;
		}
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	/** IPv4,IPv6型(サブネット付き、ネットマスクビット指定) */
	INET(java.sql.JDBCType.OTHER, String.class, MetaType.CHARACTER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 45L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	/** IPv4,IPv6型(サブネット付き、ネットマスク数値指定) */
	CIDR(java.sql.JDBCType.OTHER, "CIDR", String.class, MetaType.CHARACTER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 49L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	/** マックアドレス型 */
	MACADDR(java.sql.JDBCType.OTHER, "MACADDR", String.class, MetaType.CHARACTER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 17L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	/** UUID型 */
	UUID(java.sql.JDBCType.OTHER, "UUID", java.util.UUID.class, MetaType.BINARY){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 36L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Guid;
		}
	},
	/**
	 * 緯度経度型 GPS の緯度経度座標などの楕円体 (球体地球) データ
	 */
	GEOGRAPHY(java.sql.JDBCType.OTHER, "GEOGRAPHY", GeometryUtils.getGeographyClass(), MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return GEOMETRY;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/**
	 * 位置情報型
	 */
	GEOMETRY(java.sql.JDBCType.OTHER, "GEOMETRY", GeometryUtils.getGeometryClass(), MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	// 幾何学
	/** POINT型 */
	POINT(java.sql.JDBCType.OTHER, "POINT", Point.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 255L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/** CIRCLE型 */
	CIRCLE(java.sql.JDBCType.OTHER, "CIRCLE", Circle.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 255L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/** LINE型 */
	LINE(java.sql.JDBCType.OTHER, "CIRCLE", Line.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 255L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/** BOX型 */
	BOX(java.sql.JDBCType.OTHER, "BOX", Box.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 255L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/** LSEG型 */
	LSEG(java.sql.JDBCType.OTHER, "LSEG", Lseg.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 255L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/** PATH型 */
	PATH(java.sql.JDBCType.OTHER, "PATH", Path.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 255L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/** Polygon型 */
	POLYGON(java.sql.JDBCType.OTHER, "POLYGON", Polygon.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return CLOB;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/** SQLXML */
	SQLXML(java.sql.JDBCType.SQLXML, "SQLXML", java.sql.SQLXML.class, MetaType.CHARACTER){
		@Override
		public DataType getSurrogate() {
			return CLOB;
		}
		@Override
		public boolean isJdbcBaseType(){
			return true;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	/** JSON */
	JSON(java.sql.JDBCType.VARCHAR, "JSON", String.class, MetaType.CHARACTER){
		@Override
		public DataType getSurrogate() {
			return NCLOB;
		}
		@Override
		public DataType getUpperSurrogate() {
			return JSONB;
		}
		@Override
		public boolean isNationalCharacter() {
			return true;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	/** JSONB */
	JSONB(java.sql.JDBCType.VARCHAR, "JSONB", String.class, MetaType.CHARACTER){
		@Override
		public DataType getSurrogate() {
			return JSON;
		}
		@Override
		public boolean isNationalCharacter() {
			return true;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	/** ROWID */
	ROWID(java.sql.JDBCType.ROWID, "ROWID", String.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
	}
	, 
	/** DATALINK */
	DATALINK(java.sql.JDBCType.DATALINK, "DATALINK", MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
	},
	/** ユーザー定義ドメイン型 */
	DOMAIN(java.sql.JDBCType.DISTINCT, "DOMAIN", MetaType.OTHER){
		@Override
		public boolean isDomain() {
			return true;
		}
	},
	/** OTHER型 */
	OTHER(java.sql.JDBCType.OTHER, "OTHER", MetaType.OTHER){
		@Override
		public boolean isOther() {
			return true;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.IUnknown;
		}
	},
	/** 参照型 */
	REF(java.sql.JDBCType.REF, "REF", MetaType.OTHER){
	},
	/** ユーザー定義複合型 */
	STRUCT(java.sql.JDBCType.STRUCT, "STRUCT", MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return CLOB;
		}
		@Override
		public boolean isType() {
			return true;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.IUnknown;
		}
	},
	/** JAVA_OBJECT */
	JAVA_OBJECT(java.sql.JDBCType.JAVA_OBJECT, "JAVA_OBJECT",
			Serializable.class, MetaType.OTHER){
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.IUnknown;
		}
	}
	, 
	/** NULL */
	NULL(java.sql.JDBCType.NULL, "NULL", null, MetaType.OTHER){
	}
	,
	/**
	 * 汎用型
	 */
	ANY_DATA(java.sql.JDBCType.OTHER, "ANYDATA", Object.class, MetaType.OTHER){
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.IUnknown;
		}
	},
	// バージョン型
	/** 64bitバイナリのバージョン型(SQLServer専用) */
	ROWVERSION(java.sql.JDBCType.BINARY, "ROWVERSION", byte[].class, MetaType.BINARY){
		@Override
		public DataType getSurrogate() {
			return BINARY;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 8L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/**
	 * 自動更新タイムスタンプ(MySQL専用)
	 */
	/** TIMESTAMPVERSION */
	TIMESTAMPVERSION(java.sql.JDBCType.TIMESTAMP, "TIMESTAMPVERSION", Timestamp.class, MetaType.DATETIME){
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/**
	 * ENUM型
	 */
	ENUM(java.sql.JDBCType.VARCHAR, "ENUM", String.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return CLOB;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	/**
	 * SET型
	 */
	SET(java.sql.JDBCType.VARCHAR, "SET", String.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return CLOB;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.VarChar;
		}
	},
	/**
	 * YES_OR_NO型
	 */
	YES_OR_NO(java.sql.JDBCType.VARCHAR, "YES_OR_NO", YesOrNo.class,MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 3L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
		@Override
		public OleDbType getOleDbType() {
			return OleDbType.Boolean;
		}
	},
	/**
	 * SQL_IDENTIFIER型
	 */
	SQL_IDENTIFIER(java.sql.JDBCType.VARCHAR, "SQL_IDENTIFIER",	String.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 255L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/**
	 * CHARACTER_DATA型
	 */
	CHARACTER_DATA(java.sql.JDBCType.VARCHAR, "CHARACTER_DATA",String.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return VARCHAR;
		}
		@Override
		public Long getSurrogateTypeLength() {
			return 255L;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	},
	/**
	 * CARDINAL_NUMBER型
	 */
	CARDINAL_NUMBER(java.sql.JDBCType.INTEGER, "CARDINAL_NUMBER", Integer.class, MetaType.OTHER){
		@Override
		public DataType getSurrogate() {
			return INT;
		}
		@Override
		public boolean isJdbcBaseType(){
			return false;
		}
	};
	/**
	 * JDBCの対応する型値
	 */
	private final java.sql.JDBCType jdbcType;
	/**
	 * SQL上の型名
	 */
	private final String typeName;
	/**
	 * 対応するJavaのクラス
	 */
	private final Class<?> defaultClass;
	/**
	 * メタ型
	 */
	private final MetaType metaType;
	/**
	 * @return the autoIncrementable
	 */
	public boolean isAutoIncrementable() {
		return false;
	}

	/**
	 * java.sql.JDBCTypeとのマップ
	 */
	private static final Map<java.sql.JDBCType, DataType> jdbcMap = map();
	/**
	 * 代替型マップ
	 */
	private static final Map<DataType, DataType> surrogateTypeMap = enumMap(DataType.class);
	/**
	 * 上位型代替型マップ
	 */
	private static final Map<DataType, DataType> upperSurrogateTypeMap = enumMap(DataType.class);
	/**
	 * 名称、型マップ
	 */
	private static final Map<String, DataType> nameTypeMap = upperMap();
	/**
	 * 型、別名マップ
	 */
	private static final Map<DataType, Set<String>> typeAliasNameMap = enumMap(DataType.class);
	/**
	 * 正規表現、型マップ
	 */
	private static final Map<Pattern, DataType> patternTypeMap = map();
	/**
	 * スタティックコンストラクタ
	 */
	static {
		initializeJdbcMap();
		initializeSurrogateMap();
		initializeNameTypeMap();
		for (final Map.Entry<String, DataType> entry : nameTypeMap.entrySet()) {
			if (!entry.getKey().contains("_")) {
				final String name = "\\s*" + entry.getKey() + "\\s*";
				final Pattern pattern = Pattern.compile(name.replace(" ", "\\s+"),
						Pattern.CASE_INSENSITIVE);
				patternTypeMap.put(pattern, entry.getValue());
			}
		}
	}

	/**
	 * JDBCマップの初期化
	 */
	static void initializeJdbcMap() {
		for (final DataType type : values()) {
			addJdbcMap(type);
		}
	}

	private static void addJdbcMap(final DataType type) {
		if (type.isJdbcBaseType()) {
			if (jdbcMap.containsKey(type.jdbcType)) {
				throw new RuntimeException("Duplicate jdbcType=" + type);
			}
			jdbcMap.put(type.jdbcType, type);
		}
	}

	protected boolean isJdbcBaseType(){
		return true;
	}
	
	/**
	 * 名称、型マップの初期化
	 */
	static void initializeNameTypeMap() {
		for (final DataType type : DataType.values()) {
			final String name = type.toString();
			setAlias(type, name);
			if (name.contains("_")) {
				setAlias(type, name.replace('_', ' '));
			}
			if (!eq(name, type.getTypeName())) {
				setAlias(type, type.getTypeName());
			}
		}
		// 整数型の別名
		setAlias(UTINYINT, "INT8 UNSIGNED");
		setAlias(USMALLINT, "INT16 UNSIGNED");
		setAlias(UMEDIUMINT, "INT24 UNSIGNED");
		setAlias(UINT, "INT32 UNSIGNED");
		setAlias(UINT, "INTEGER UNSIGNED");
		setAlias(UBIGINT, "INT64 UNSIGNED");
		setAlias(UBIGINT, "INT64 UNSIGNED");
		//
		setAlias(DECIMAL, "DEC");
		// 文字列型の別名
		setAlias(CHAR, "CHARACTER");
		setAlias(NCHAR, "NATIONAL CHARACTER");
		setAlias(VARCHAR, "CHARACTER VARYING");
		setAlias(NVARCHAR, "NATIONAL CHARACTER VARYING");
		setAlias(CLOB, "CHARACTER LARGE OBJECT");
		setAlias(NCLOB, "NATIONAL CHARACTER LARGE OBJECT");
		// 浮動小数点型の別名
		setAlias(DOUBLE, "DOUBLE PRECISION");
		// 時刻型の別名
		setAlias(TIMESTAMP, "TIMESTAMP WITHOUT TIME ZONE");
		setAlias(TIME, "TIME WITHOUT TIME ZONE");
		// バイナリ
		setAlias(BLOB, "BINARY LARGE OBJECT");
	}

	/**
	 * 別名の登録
	 * 
	 * @param type
	 * @param alias
	 */
	static void setAlias(final DataType type, final String alias) {
		nameTypeMap.put(alias, type);
		if (!typeAliasNameMap.containsKey(type)) {
			typeAliasNameMap.put(type, upperSet());
		}
		final Set<String> set = typeAliasNameMap.get(type);
		if (!eq(type.getTypeName(), alias)) {
			set.add(alias);
		}
	}

	/**
	 * 代替型マップの初期化
	 */
	static void initializeSurrogateMap() {
		for(final DataType type:values()){
			if (type.getSurrogate()!=null){
				surrogateTypeMap.put(type, type.getSurrogate());
			}
			if (type.getUpperSurrogate()!=null){
				upperSurrogateTypeMap.put(type, type.getUpperSurrogate());
			}
		}
	}

	/**
	 * コンストラクタ
	 * 
	 * @param jdbcType
	 * @param jdbcBaseType
	 * @param typeName
	 * @param metaType
	 */
	private DataType(final java.sql.JDBCType jdbcType, final String typeName, final MetaType metaType) {
		this(jdbcType, typeName, null, metaType);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param jdbcType
	 * @param jdbcBaseType
	 * @param metaType
	 * @param sizeType
	 */
	private DataType(final java.sql.JDBCType jdbcType,
			final MetaType metaType) {
		this(jdbcType, null, null, metaType);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param jdbcType
	 * @param jdbcBaseType
	 * @param defaultClass
	 * @param metaType
	 */
	private DataType(final java.sql.JDBCType jdbcType,
			final Class<?> defaultClass, final MetaType metaType) {
		this(jdbcType, null, defaultClass, metaType);
	}
	
	/**
	 * コンストラクタ
	 * 
	 * @param jdbcType
	 * @param jdbcBaseType
	 * @param typeName
	 * @param defaultClass
	 * @param metaType
	 * @param sizeType
	 * @param autoIncrementable
	 */
	private DataType(final java.sql.JDBCType jdbcType, final String typeName,
			final Class<?> defaultClass, final MetaType metaType) {
		this.jdbcType = jdbcType;
		this.typeName = typeName;
		this.defaultClass = defaultClass;
		this.metaType = metaType;
	}
	
	/**
	 * 文字列からの変換
	 * 
	 * @param key
	 */
	public static DataType toType(final String key) {
		if (nameTypeMap.containsKey(key)) {
			return nameTypeMap.get(key);
		}
		for (final Map.Entry<Pattern, DataType> entry : patternTypeMap.entrySet()) {
			final Pattern pattern = entry.getKey();
			final Matcher matcher = pattern.matcher(key);
			if (matcher.matches()) {
				return entry.getValue();
			}
		}
		return DataType.OTHER;
	}

	/**
	 * <code>java.sql.JDBCType</code>から対応するEnumを取得
	 * 
	 * @param val
	 */
	public static DataType valueOf(final int val) {
		final DataType dataType= jdbcMap.get(getJDBCType(val));
		if (dataType!=null) {
			return dataType;
		}
		return DataType.OTHER;
	}

	private final static Map<Integer, JDBCType> TYPE_NUMBER_MAP=CommonUtils.concurrentMap();
	
	private static JDBCType getJDBCType(final int val) {
		if (TYPE_NUMBER_MAP.isEmpty()) {
			synchronized(TYPE_NUMBER_MAP) {
				if (TYPE_NUMBER_MAP.isEmpty()) {
					for(final java.sql.JDBCType jdbcType:java.sql.JDBCType.values()) {
						TYPE_NUMBER_MAP.put(jdbcType.getVendorTypeNumber(), jdbcType);
					}
				}
			}
		}
		return TYPE_NUMBER_MAP.get(val);
	}
	
	/**
	 * 別名の取得
	 * 
	 */
	public Set<String> getAliasNames() {
		return typeAliasNameMap.get(this);
	}

	public java.sql.JDBCType getJdbcType() {
		return jdbcType;
	}

	public Class<?> getDefaultClass() {
		return defaultClass;
	}

	public MetaType getMetaTtype() {
		return this.metaType;
	}

	public OleDbType getOleDbType() {
		return null;
	}
	
	/**
	 * 数値型か?
	 * 
	 */
	public boolean isNumeric() {
		return metaType.isNumeric();
	}

	/**
	 * インターバル型か?
	 * 
	 */
	public boolean isInterval() {
		return metaType.isInterval();
	}

	/**
	 * バイナリ型か?
	 * 
	 */
	public boolean isBinary() {
		return metaType.isBinary();
	}

	/**
	 * 文字型か?
	 * 
	 */
	public boolean isCharacter() {
		return metaType.isCharacter();
	}

	/**
	 * boolean型か?
	 * 
	 */
	public boolean isBoolean() {
		return this == BOOLEAN || this == BIT;
	}

	/**
	 * datetime型か?
	 * 
	 */
	public boolean isDateTime() {
		return metaType.isDateTime();
	}

	/**
	 * NATIONAL CHARACTER型か?
	 * 
	 */
	public boolean isNationalCharacter() {
		return false;
	}

	/**
	 * Domain?
	 * 
	 */
	public boolean isDomain() {
		return false;
	}

	/**
	 * Type?
	 * 
	 */
	public boolean isType() {
		return false;
	}

	/**
	 * Other?
	 * 
	 */
	public boolean isOther() {
		return false;
	}

	/**
	 * 固定サイズを持つか?
	 * 
	 */
	public boolean isFixedSize() {
		return false;
	}

	/**
	 * 固定scaleを持つか?
	 * 
	 */
	public boolean isFixedScale() {
		return false;
	}

	public String getTypeName() {
		if (this.typeName!=null){
			return typeName;
		}
		return this.toString();
	}

	/**
	 * 代替型を取得します
	 * 
	 */
	public DataType getSurrogate() {
		return null;
	}

	/**
	 * N型の代替型を取得します
	 * 
	 */
	public DataType getNationalSurrogate() {
		return null;
	}

	/**
	 * 代替型の場合の長さを取得します
	 * 
	 */
	public Long getSurrogateTypeLength() {
		return null;
	}

	/**
	 * 代替型を取得します
	 * 
	 */
	public DataType getUpperSurrogate() {
		return null;
	}

	/**
	 * 代替型マップを取得します。
	 * 
	 */
	public static Map<DataType, DataType> getSurrogateMap() {
		return cloneMap(surrogateTypeMap);
	}

	/**
	 * 上位代替型マップを取得します。
	 * 
	 */
	public static Map<DataType, DataType> getUpperSurrogateMap() {
		return cloneMap(upperSurrogateTypeMap);
	}

}
