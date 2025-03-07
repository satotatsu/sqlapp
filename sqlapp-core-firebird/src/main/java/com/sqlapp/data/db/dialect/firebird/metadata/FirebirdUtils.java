/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.data.db.datatype.DataType.BIGINT;
import static com.sqlapp.data.db.datatype.DataType.BLOB;
import static com.sqlapp.data.db.datatype.DataType.CHAR;
import static com.sqlapp.data.db.datatype.DataType.CLOB;
import static com.sqlapp.data.db.datatype.DataType.DATE;
import static com.sqlapp.data.db.datatype.DataType.DECIMAL;
import static com.sqlapp.data.db.datatype.DataType.DOUBLE;
import static com.sqlapp.data.db.datatype.DataType.FLOAT;
import static com.sqlapp.data.db.datatype.DataType.INT;
import static com.sqlapp.data.db.datatype.DataType.NUMERIC;
import static com.sqlapp.data.db.datatype.DataType.SMALLINT;
import static com.sqlapp.data.db.datatype.DataType.TIME;
import static com.sqlapp.data.db.datatype.DataType.TIMESTAMP;
import static com.sqlapp.data.db.datatype.DataType.VARCHAR;
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.upperMap;
import static com.sqlapp.util.StringUtils.getGroupString;

import java.util.Map;
import java.util.regex.Pattern;

import com.sqlapp.data.schemas.AbstractColumn;

public class FirebirdUtils {
	private FirebirdUtils() {
	}

	private static final Pattern defaultPattern = Pattern.compile("\\s*DEFAULT\\s+(.*)s*",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE);

	/**
	 * Firebirdのデフォルト制約を一般的な式に変換して設定
	 * 
	 * @param column
	 * @param condition
	 */
	public static void setDefaultConstraint(AbstractColumn<?> column, String condition) {
		if (isEmpty(condition)) {
			return;
		}
		String val = getGroupString(defaultPattern, condition, 1);
		if (val == null) {
			return;
		}
		column.setDefaultValue(val);
	}

	/**
	 * Firebirdのチェック条件を一般的な式に変換します
	 * 
	 * @param checkConstraintSource
	 */
	public static String convertCheckConstraint(String checkConstraintSource) {
		if (isEmpty(checkConstraintSource)) {
			return checkConstraintSource;
		}
		String val = getGroupString(CHECK_PATTERN, checkConstraintSource, 1);
		return val;
	}

	private static final Pattern CHECK_PATTERN = Pattern.compile("\\s*CHECK\\s*[(](.*)[)]\\s*",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	private static final Map<String, String> PRIV_CACHE = upperMap();
	static {
		PRIV_CACHE.put("S", "SELECT");
		PRIV_CACHE.put("I", "INSERT");
		PRIV_CACHE.put("U", "UPDATE");
		PRIV_CACHE.put("X", "EXECUTE");
		PRIV_CACHE.put("D", "DELETE");
		PRIV_CACHE.put("R", "REFERENCES");
		PRIV_CACHE.put("A", "ALL");
	}

	protected static String getPrivilege(String priv) {
		String privilege = PRIV_CACHE.get(priv);
		if (privilege != null) {
			return privilege;
		}
		return priv;
	}

	/**
	 * Firebirdのデータ型をTYPEとSUBTYPEから設定する
	 * 
	 * @param column
	 * @param type
	 *            Firebirdのデータ型を表す整数
	 * @param subType
	 *            Firebirdのデータ型の詳細を表す整数
	 * @param length
	 * @param precision
	 * @param scale
	 * @param segmentLength
	 *            BLOBでSEGMENT_SIZEを指定した場合の値
	 */
	public static void setDbType(AbstractColumn<?> column, int type, int subType, int length, int precision, int scale,
			int segmentLength) {
		column.setLength(0);
		column.setOctetLength(0);
		column.setScale(0);
		switch (type) {
		case 7:// SHORT
			column.setDataTypeName("SMALLINT");
			column.setDataType(SMALLINT);
			column.setOctetLength(2);
			break;
		case 8:// LONG
			column.setDataTypeName("INTEGER");
			column.setDataType(INT);
			column.setOctetLength(4);
			break;
		case 10:// FLOAT
			column.setDataTypeName("FLOAT");
			column.setDataType(FLOAT);
			column.setOctetLength(4);
			break;
		case 12:// DATE
			column.setDataTypeName("DATE");
			column.setDataType(DATE);
			column.setOctetLength(length);
			break;
		case 13:// TIME
			column.setDataTypeName("TIME");
			column.setDataType(TIME);
			column.setOctetLength(length);
			break;
		case 14:// TEXT
			column.setDataTypeName("CHAR");
			column.setDataType(CHAR);
			column.setLength(length);
			column.setOctetLength(length);
			break;
		case 16:// INT64
			switch (subType) {
			case 1:// NUMERIC
				column.setDataTypeName("NUMERIC");
				column.setDataType(NUMERIC);
				column.setLength(precision);
				column.setScale(scale);
				break;
			case 2:// DECIMAL
				column.setDataTypeName("DECIMAL");
				column.setDataType(DECIMAL);
				column.setLength(precision);
				column.setScale(scale);
				break;
			default:// 0
				column.setDataTypeName("BIGINT");
				column.setDataType(BIGINT);
				column.setOctetLength(length);
				break;
			}
			break;
		case 27:// DOUBLE
			column.setDataTypeName("DOUBLE PRECISION");
			column.setDataType(DOUBLE);
			column.setOctetLength(length);
			break;
		case 35:// TIMESTAMP
			column.setDataTypeName("TIMESTAMP");
			column.setDataType(TIMESTAMP);
			column.setOctetLength(length);
			break;
		case 37:// VARYING
			column.setDataTypeName("VARCHAR");
			column.setDataType(VARCHAR);
			column.setLength(length);
			column.setOctetLength(length);
			break;
		case 261:// BLOB
			switch (subType) {
			case 0:// BLOB
				column.setDataTypeName("BLOB SUB_TYPE BINARY");
				column.setDataType(BLOB);
				column.setLength(segmentLength);
				column.setOctetLength(segmentLength);
				break;
			case 1:// CLOB
				column.setDataTypeName("BLOB SUB_TYPE TEXT");
				column.setDataType(CLOB);
				column.setLength(segmentLength);
				column.setOctetLength(segmentLength);
				break;
			default:
				break;
			}
			break;
		case 9:// QUAD
		case 40:// CSTRING
		case 45:// BLOB_ID
		default:
		}
		// column.setDataType(Dialect.getClassByJdbc(column.getSqlType()));
	}
}
