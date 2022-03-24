/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.sybase.metadata;

import static com.sqlapp.util.CommonUtils.notZero;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.MetadataReader;
import com.sqlapp.data.db.metadata.ReaderUtils;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Routine;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.CommonUtils;

public class SybaseUtils extends ReaderUtils {

	protected static String replaceNames(String definition, String name) {
		return definition.replace("[" + name + "]", name);
	}

	public static IndexType getIndexType(int indexType) {
		if (indexType == 0) {
			// ヒープ
			return IndexType.BTree;
		} else if (indexType == 1) {
			// クラスタ化インデックス
			return IndexType.Clustered;
		} else if (indexType == 3) {
			return IndexType.Xml;
		} else if (indexType == 4) {
			return IndexType.Spatial;
		}
		// 非クラスタ化インデックス
		return IndexType.BTree;
	}

	protected static void setNamedArgument(Dialect dialect, ResultSet rs,
			NamedArgument obj, Routine<?> routine) throws SQLException {
		routine.setCatalogName(getString(rs, MetadataReader.CATALOG_NAME));
		routine.setSchemaName(getString(rs, MetadataReader.SCHEMA_NAME));
		routine.setName(getString(rs, MetadataReader.ROUTINE_NAME));
		obj.setCatalogName(getString(rs, MetadataReader.CATALOG_NAME));
		obj.setSchemaName(getString(rs, MetadataReader.SCHEMA_NAME));
		SchemaUtils.setRoutine(obj, routine);
		String productDataType = getString(rs, "NAME");
		Long max_length = getLong(rs, "max_length");
		Long precision = getLong(rs, "precision");
		Integer scale = getInteger(rs, "scale");
		obj.setDefaultValue(getString(rs, "default_value"));
		obj.setReadonly(getBoolean(rs, "is_readonly"));
		dialect.setDbType(productDataType, notZero(max_length, precision),
				scale, obj);
	}

	/**
	 * ResultSetから指定したカラムの文字列を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected static String getString(ResultSet rs, String name)
			throws SQLException {
		return rtrim(rs.getNString(name));
	}

	private static final Pattern PROCEDURE_PATTERN1 = Pattern.compile(
			".*CREATE.*?PROC(EDURE){0,1}.*?\\s+AS(.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	private static final Pattern PROCEDURE_PATTERN2 = Pattern
			.compile(
					".*CREATE.*?PROC(EDURE){0,1}.*EXECUTE\\s+AS\\s+[^\\s]+.*?\\s+AS(.*)",
					Pattern.CASE_INSENSITIVE + Pattern.MULTILINE
							+ Pattern.DOTALL);

	private static final Pattern FIRST_SPACE_PATTERN = Pattern.compile(
			"[ \\t]*\\n(.*)", Pattern.CASE_INSENSITIVE + Pattern.MULTILINE
					+ Pattern.DOTALL);

	/**
	 * プロシージャー定義からステートメント部分を抽出します
	 * 
	 * @param definition
	 *            プロシージャー定義
	 * @return ステートメント
	 */
	public static String getProcedureStatement(String definition) {
		definition = rtrim(definition);
		if (CommonUtils.isEmpty(definition)){
			return definition;
		}
		Matcher matcher = PROCEDURE_PATTERN2.matcher(definition);
		String statement = null;
		if (matcher.matches()) {
			statement = trimFirstLine(matcher.group(2));
			return statement;
		} else {
			matcher = PROCEDURE_PATTERN1.matcher(definition);
			if (matcher.matches()) {
				return trimFirstLine(matcher.group(2));
			}
		}
		return definition;
	}

	private static String rtrim(String definition) {
		definition = CommonUtils.rtrim(definition);
		definition = CommonUtils.rtrim(definition, ';');
		definition = CommonUtils.rtrim(definition);
		return definition;
	}

	private static String trimFirstLine(String statement) {
		Matcher matcher = FIRST_SPACE_PATTERN.matcher(statement);
		if (matcher.matches()) {
			return matcher.group(1);
		} else {
			return statement;
		}
	}

	/**
	 * 関数定義からステートメント部分を抽出します
	 * 
	 * @param definition
	 *            関数定義
	 * @return ステートメント
	 */
	public static String getFunctionStatement(String definition, String type) {
		if ("FN".equalsIgnoreCase(type)) {
			return getFunctionStatementFN(definition);
		} else if ("IF".equalsIgnoreCase(type)) {
			return getFunctionStatementIF(definition);
		} else if ("TF".equalsIgnoreCase(type)) {
			return getFunctionStatementTF(definition);
		}
		return definition;
	}

	private static final Pattern FUNCTION_FN_PATTERN1 = Pattern.compile(
			".*CREATE.*?FUNCTION.*?RETURNS.*?(BEGIN.*END)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	/**
	 * 'FN' SQL スカラー関数定義からステートメント部分を抽出します
	 * 
	 * @param definition
	 *            関数定義
	 * @return ステートメント
	 */
	protected static String getFunctionStatementFN(String definition) {
		definition = rtrim(definition);
		Matcher matcher = FUNCTION_FN_PATTERN1.matcher(definition);
		String statement = null;
		if (matcher.matches()) {
			statement = matcher.group(1);
			statement = trimFirstLine(statement);
			return statement;
		}
		return definition;
	}

	// --Transact-SQL Inline Table-Valued Function Syntax
	private static final Pattern FUNCTION_IF_PATTERN1 = Pattern.compile(
			".*CREATE.*?FUNCTION.*?RETURNS.*?(RETURN\\s+.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	/**
	 * 'IF' SQL インライン テーブル値関数定義からステートメント部分を抽出します
	 * 
	 * @param definition
	 *            関数定義
	 * @return ステートメント
	 */
	protected static String getFunctionStatementIF(String definition) {
		definition = rtrim(definition);
		Matcher matcher = FUNCTION_IF_PATTERN1.matcher(definition);
		String statement = null;
		if (matcher.matches()) {
			statement = trimFirstLine(matcher.group(1));
			return statement;
		}
		return definition;
	}

	private static final Pattern FUNCTION_TF_PATTERN1 = Pattern.compile(
			".*CREATE.*?FUNCTION.*?RETURNS\\s+.*?\\s*(@\\S+).*?TABLE\\s+(.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	private static final Pattern FUNCTION_TF_PATTERN2 = Pattern
			.compile(
					".*?(\\(.*\\))\\s+.*(WITH\\s.*?){0,1}(\\s*AS\\s*){0,1}.*?\\s*(BEGIN.*END).*",
					Pattern.CASE_INSENSITIVE + Pattern.MULTILINE
							+ Pattern.DOTALL);

	/**
	 * 'TF' SQL テーブル値関数定義からステートメント部分を抽出します
	 * 
	 * @param definition
	 *            関数定義
	 * @return ステートメント
	 */
	protected static String getFunctionStatementTF(String definition) {
		definition = rtrim(definition);
		Matcher matcher = FUNCTION_TF_PATTERN1.matcher(definition);
		String statement = null;
		if (!matcher.matches()) {
			return definition;
		}
		statement = matcher.group(2);
		matcher = FUNCTION_TF_PATTERN2.matcher(statement);
		if (!matcher.matches()) {
			return statement;
		}
		statement = matcher.group(4);
		return statement;
	}

	/**
	 * 'TF' SQL テーブル値関数定義からリターン変数名を抽出します
	 * 
	 * @param definition
	 *            関数定義
	 * @return リターン変数名
	 */
	public static String getFunctionReturnName(String definition) {
		definition = rtrim(definition);
		Matcher matcher = FUNCTION_TF_PATTERN1.matcher(definition);
		if (!matcher.matches()) {
			return null;
		}
		return matcher.group(1);
	}

	private static final Pattern FUNCTION_FT_PATTERN1 = Pattern
			.compile(
					".*CREATE.*?FUNCTION.*?RETURNS\\s+.*?TABLE\\s+.*?(.*)\\s*EXTERNAL\\s+NAME\\s+([\\S]+).*",
					Pattern.CASE_INSENSITIVE + Pattern.MULTILINE
							+ Pattern.DOTALL);

	private static final Pattern FUNCTION_FT_PATTERN2 = Pattern
			.compile(
					"(\\(.*\\))\\s+.*(WITH\\s.*?){0,1}(ORDER\\s+.*?){0,1}(\\s*AS\\s*){0,1}.*",
					Pattern.CASE_INSENSITIVE + Pattern.MULTILINE
							+ Pattern.DOTALL);

	/**
	 * 'TF' SQL テーブル値関数定義からテーブル定義部分を抽出します
	 * 
	 * @param definition
	 *            関数定義
	 * @return リターンテーブル定義
	 */
	public static String getFunctionReturnTable(String definition) {
		definition = rtrim(definition);
		Matcher matcher = FUNCTION_FT_PATTERN1.matcher(definition);
		String statement = null;
		if (matcher.matches()) {
			String group = matcher.group(1);
			matcher = FUNCTION_FT_PATTERN2.matcher(group);
			if (matcher.matches()) {
				statement = matcher.group(1);
				return statement;
			} else {
				return null;
			}
		}
		matcher = FUNCTION_TF_PATTERN1.matcher(definition);
		if (!matcher.matches()) {
			return null;
		}
		statement = matcher.group(2);
		matcher = FUNCTION_TF_PATTERN2.matcher(statement);
		if (!matcher.matches()) {
			return statement;
		}
		statement = matcher.group(1);
		return statement;
	}

	public static Long getMaxLength(String productDataType, Long byteLength) {
		productDataType = productDataType.toUpperCase();
		if (productDataType.startsWith("NTEXT")
				|| productDataType.startsWith("NCHAR")
				|| productDataType.startsWith("NVARCHAR") && byteLength != null) {
			if (byteLength.longValue() > 0) {
				return byteLength.longValue() / 2;
			}
		}
		return byteLength;
	}
}
