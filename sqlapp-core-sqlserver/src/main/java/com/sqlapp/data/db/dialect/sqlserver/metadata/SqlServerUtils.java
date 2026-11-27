/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static com.sqlapp.util.CommonUtils.notZero;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.metadata.MetadataReader;
import com.sqlapp.data.db.metadata.ReaderUtils;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Routine;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.CommonUtils;

public class SqlServerUtils extends ReaderUtils {

	/**
	 * Removes the brackets around the given column name where the name is a regular
	 * identifier. See {@link #replaceNames(String)}.
	 */
	protected static String replaceNames(String definition, String name) {
		if (name == null) {
			return replaceNames(definition);
		}
		return unquoteNames(definition, name);
	}

	/**
	 * Removes the brackets around names that are valid without them.
	 * <p>
	 * A bracketed name stays quoted when it is not a regular identifier (for
	 * example {@code [a-b]}, {@code [c)]} or {@code [my col]}) or is a reserved
	 * keyword (for example {@code [order]}). Text inside string literals (such as a
	 * LIKE pattern {@code '[abc]%'}) and double-quoted identifiers is unchanged.
	 * </p>
	 */
	protected static String replaceNames(String definition) {
		return unquoteNames(definition, null);
	}

	private static String unquoteNames(final String definition, final String target) {
		if (CommonUtils.isEmpty(definition) || definition.indexOf('[') < 0) {
			return definition;
		}
		final int length = definition.length();
		final StringBuilder builder = new StringBuilder(length);
		int i = 0;
		while (i < length) {
			final char c = definition.charAt(i);
			if (c == '\'' || c == '"' || c == '[') {
				final int end = skipQuoted(definition, i, c == '[' ? ']' : c);
				if (end < 0) {
					builder.append(definition, i, length);
					break;
				}
				if (c == '[') {
					final String name = definition.substring(i + 1, end);
					if ((target == null || target.equals(name)) && isRegularIdentifier(name)) {
						builder.append(name);
					} else {
						builder.append(definition, i, end + 1);
					}
				} else {
					builder.append(definition, i, end + 1);
				}
				i = end + 1;
			} else {
				builder.append(c);
				i++;
			}
		}
		return builder.toString();
	}

	/**
	 * Transact-SQL reserved keywords, which must be delimited when used as names.
	 */
	private static final java.util.Set<String> RESERVED_WORDS = java.util.Set.of("ADD", "ALL", "ALTER", "AND", "ANY",
			"AS", "ASC", "AUTHORIZATION", "BACKUP", "BEGIN", "BETWEEN", "BREAK", "BROWSE", "BULK", "BY", "CASCADE",
			"CASE", "CHECK", "CHECKPOINT", "CLOSE", "CLUSTERED", "COALESCE", "COLLATE", "COLUMN", "COMMIT", "COMPUTE",
			"CONSTRAINT", "CONTAINS", "CONTAINSTABLE", "CONTINUE", "CONVERT", "CREATE", "CROSS", "CURRENT",
			"CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "CURSOR", "DATABASE", "DBCC",
			"DEALLOCATE", "DECLARE", "DEFAULT", "DELETE", "DENY", "DESC", "DISK", "DISTINCT", "DISTRIBUTED", "DOUBLE",
			"DROP", "DUMP", "ELSE", "END", "ERRLVL", "ESCAPE", "EXCEPT", "EXEC", "EXECUTE", "EXISTS", "EXIT",
			"EXTERNAL", "FETCH", "FILE", "FILLFACTOR", "FOR", "FOREIGN", "FREETEXT", "FREETEXTTABLE", "FROM", "FULL",
			"FUNCTION", "GOTO", "GRANT", "GROUP", "HAVING", "HOLDLOCK", "IDENTITY", "IDENTITY_INSERT", "IDENTITYCOL",
			"IF", "IN", "INDEX", "INNER", "INSERT", "INTERSECT", "INTO", "IS", "JOIN", "KEY", "KILL", "LEFT", "LIKE",
			"LINENO", "LOAD", "MERGE", "NATIONAL", "NOCHECK", "NONCLUSTERED", "NOT", "NULL", "NULLIF", "OF", "OFF",
			"OFFSETS", "ON", "OPEN", "OPENDATASOURCE", "OPENQUERY", "OPENROWSET", "OPENXML", "OPTION", "OR", "ORDER",
			"OUTER", "OVER", "PERCENT", "PIVOT", "PLAN", "PRECISION", "PRIMARY", "PRINT", "PROC", "PROCEDURE", "PUBLIC",
			"RAISERROR", "READ", "READTEXT", "RECONFIGURE", "REFERENCES", "REPLICATION", "RESTORE", "RESTRICT",
			"RETURN", "REVERT", "REVOKE", "RIGHT", "ROLLBACK", "ROWCOUNT", "ROWGUIDCOL", "RULE", "SAVE", "SCHEMA",
			"SECURITYAUDIT", "SELECT", "SEMANTICKEYPHRASETABLE", "SEMANTICSIMILARITYDETAILSTABLE",
			"SEMANTICSIMILARITYTABLE", "SESSION_USER", "SET", "SETUSER", "SHUTDOWN", "SOME", "STATISTICS",
			"SYSTEM_USER", "TABLE", "TABLESAMPLE", "TEXTSIZE", "THEN", "TO", "TOP", "TRAN", "TRANSACTION", "TRIGGER",
			"TRUNCATE", "TRY_CONVERT", "TSEQUAL", "UNION", "UNIQUE", "UNPIVOT", "UPDATE", "UPDATETEXT", "USE", "USER",
			"VALUES", "VARYING", "VIEW", "WAITFOR", "WHEN", "WHERE", "WHILE", "WITH", "WITHIN", "WRITETEXT");

	/**
	 * Whether the name can be written without delimiters: it starts with a letter
	 * or underscore, continues with letters, digits, {@code _ @ $ #}, and is not a
	 * reserved keyword. Names starting with {@code @} or {@code #} are treated as
	 * needing delimiters because of their special meaning.
	 */
	static boolean isRegularIdentifier(final String name) {
		if (name.isEmpty()) {
			return false;
		}
		int codePoint = name.codePointAt(0);
		if (!Character.isLetter(codePoint) && codePoint != '_') {
			return false;
		}
		for (int i = Character.charCount(codePoint); i < name.length(); i += Character.charCount(codePoint)) {
			codePoint = name.codePointAt(i);
			if (!Character.isLetterOrDigit(codePoint) && codePoint != '_' && codePoint != '@' && codePoint != '$'
					&& codePoint != '#') {
				return false;
			}
		}
		return !RESERVED_WORDS.contains(name.toUpperCase(java.util.Locale.ROOT));
	}

	/**
	 * Removes redundant outer parentheses that SQL Server adds to stored
	 * definitions (defaults, CHECK constraints, filtered index predicates).
	 * <p>
	 * Only a pair of parentheses that encloses the whole definition is removed,
	 * repeatedly, so {@code ((1)+(2))} becomes {@code (1)+(2)}, not {@code 1)+(2}.
	 * Parentheses inside string literals and quoted identifiers are ignored.
	 * Unbalanced input is returned unchanged.
	 * </p>
	 *
	 * @param definition stored definition
	 * @return definition without enclosing parentheses
	 */
	public static String unwrapParentheses(final String definition) {
		String value = definition;
		while (!CommonUtils.isEmpty(value) && value.charAt(0) == '('
				&& findClosingParenthesis(value) == value.length() - 1) {
			value = value.substring(1, value.length() - 1);
		}
		return value;
	}

	/**
	 * Returns the index of the parenthesis closing the one at index 0, or -1.
	 */
	private static int findClosingParenthesis(final String value) {
		int depth = 0;
		final int length = value.length();
		for (int i = 0; i < length; i++) {
			final char c = value.charAt(i);
			switch (c) {
			case '(':
				depth++;
				break;
			case ')':
				depth--;
				if (depth == 0) {
					return i;
				}
				if (depth < 0) {
					return -1;
				}
				break;
			case '\'':
				i = skipQuoted(value, i, '\'');
				break;
			case '"':
				i = skipQuoted(value, i, '"');
				break;
			case '[':
				i = skipQuoted(value, i, ']');
				break;
			default:
				break;
			}
			if (i < 0) {
				return -1;
			}
		}
		return -1;
	}

	/**
	 * Returns the index of the closing quote (doubled quotes are escapes), or -1.
	 */
	private static int skipQuoted(final String value, final int start, final char close) {
		final int length = value.length();
		for (int i = start + 1; i < length; i++) {
			if (value.charAt(i) == close) {
				if (i + 1 < length && value.charAt(i + 1) == close) {
					i++;
					continue;
				}
				return i;
			}
		}
		return -1;
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
		} else if (indexType == 5) {
			return IndexType.ClusteredColumnStore;
		} else if (indexType == 6) {
			return IndexType.NonClusteredColumnStore;
		} else if (indexType == 9) {
			return IndexType.Json;
		}
		// 非クラスタ化インデックス
		return IndexType.BTree;
	}

	protected static void setNamedArgument(ResultSet rs, Routine<?> routine, NamedArgument obj) throws SQLException {
		routine.setCatalogName(getString(rs, MetadataReader.CATALOG_NAME));
		routine.setSchemaName(getString(rs, MetadataReader.SCHEMA_NAME));
		routine.setName(getString(rs, MetadataReader.ROUTINE_NAME));
		obj.setCatalogName(getString(rs, MetadataReader.CATALOG_NAME));
		obj.setSchemaName(getString(rs, MetadataReader.SCHEMA_NAME));
		SchemaUtils.setRoutine(obj, routine);
		String productDataType = getString(rs, "NAME");
		Long maxLength = getMaxLength(productDataType, getLong(rs, "max_length"));
		if (maxLength != null && maxLength.longValue() == -1L) {
			if ("xml".equalsIgnoreCase(productDataType)) {
				maxLength = null;
			} else {
				maxLength = "nvarchar".equalsIgnoreCase(productDataType) ? CommonUtils.LEN_1GB - 1
						: CommonUtils.LEN_2GB - 1;
			}
		}
		Long precision = getLong(rs, "precision");
		Integer scale = getInteger(rs, "scale");
		if ("decimal".equalsIgnoreCase(productDataType) || "numeric".equalsIgnoreCase(productDataType))
			maxLength = precision;
		boolean temporal = "time".equalsIgnoreCase(productDataType) || "datetime2".equalsIgnoreCase(productDataType)
				|| "datetimeoffset".equalsIgnoreCase(productDataType);
		if (temporal) {
			maxLength = scale == null ? null : scale.longValue();
			precision = null;
			scale = null;
		}
		obj.setDefaultValue(getString(rs, "default_value"));
		obj.setReadonly(getBoolean(rs, "is_readonly"));
		obj.getDialect().setDbType(productDataType, temporal ? maxLength : notZero(maxLength, precision), scale, obj);
	}

	/**
	 * ResultSetから指定したカラムの文字列を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected static String getString(ResultSet rs, String name) throws SQLException {
		return rtrim(rs.getNString(name));
	}

	private static final Pattern PROCEDURE_PATTERN1 = Pattern.compile(".*CREATE.*?PROC(EDURE){0,1}.*?\\s+AS(.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	private static final Pattern PROCEDURE_PATTERN2 = Pattern.compile(
			".*CREATE.*?PROC(EDURE){0,1}.*EXECUTE\\s+AS\\s+[^\\s]+.*?\\s+AS(.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	private static final Pattern FIRST_SPACE_PATTERN = Pattern.compile("[ \\t]*\\n(.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	/**
	 * プロシージャー定義からステートメント部分を抽出します
	 * 
	 * @param definition プロシージャー定義
	 * @return ステートメント
	 */
	public static String getProcedureStatement(String definition) {
		definition = rtrim(definition);
		if (CommonUtils.isEmpty(definition)) {
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
	 * @param definition 関数定義
	 * @return ステートメント
	 */
	public static String getFunctionStatement(String definition, String type) {
		if (definition == null) {
			return definition;
		} else if ("FN".equalsIgnoreCase(type)) {
			return getFunctionStatementFN(definition);
		} else if ("IF".equalsIgnoreCase(type)) {
			return getFunctionStatementIF(definition);
		} else if ("TF".equalsIgnoreCase(type)) {
			return getFunctionStatementTF(definition);
		}
		return definition;
	}

	private static final Pattern FUNCTION_FN_PATTERN1 = Pattern.compile(
			".*CREATE.*?FUNCTION.*?RETURNS.*?(BEGIN.*END)\\s*",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	/**
	 * 'FN' SQL スカラー関数定義からステートメント部分を抽出します
	 * 
	 * @param definition 関数定義
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
	 * @param definition 関数定義
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

	private static final Pattern FUNCTION_TF_PATTERN2 = Pattern.compile(
			".*?(\\(.*\\))\\s+.*(WITH\\s.*?){0,1}(\\s*AS\\s*){0,1}.*?\\s*(BEGIN.*END).*",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	/**
	 * 'TF' SQL テーブル値関数定義からステートメント部分を抽出します
	 * 
	 * @param definition 関数定義
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
	 * @param definition 関数定義
	 * @return リターン変数名
	 */
	public static String getFunctionReturnName(String definition) {
		definition = rtrim(definition);
		if (definition == null) {
			return null;
		}
		Matcher matcher = FUNCTION_TF_PATTERN1.matcher(definition);
		if (!matcher.matches()) {
			return null;
		}
		return matcher.group(1);
	}

	private static final Pattern FUNCTION_FT_PATTERN1 = Pattern.compile(
			".*CREATE.*?FUNCTION.*?RETURNS\\s+.*?TABLE\\s+.*?(.*)\\s*EXTERNAL\\s+NAME\\s+([\\S]+).*",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	private static final Pattern FUNCTION_FT_PATTERN2 = Pattern.compile(
			"(\\(.*\\))\\s+.*(WITH\\s.*?){0,1}(ORDER\\s+.*?){0,1}(\\s*AS\\s*){0,1}.*",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	/**
	 * 'TF' SQL テーブル値関数定義からテーブル定義部分を抽出します
	 * 
	 * @param definition 関数定義
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

	private static final Pattern TRIGGER_PATTERN1 = Pattern.compile(".*CREATE.*?TRIGGER.*?AS\\s+(.*)",
			Pattern.CASE_INSENSITIVE + Pattern.MULTILINE + Pattern.DOTALL);

	/**
	 * トリガー定義からトリガーのstatement部分を抽出します
	 * 
	 * @param definition 関数定義
	 * @return トリガーのstatement部分
	 */
	public static String getTriggerStatement(String definition) {
		if (definition == null) {
			return null;
		}
		definition = rtrim(definition);
		Matcher matcher = TRIGGER_PATTERN1.matcher(definition);
		if (matcher.matches()) {
			String group = matcher.group(1);
			return group;
		}
		return definition;
	}

	public static Long getMaxLength(String productDataType, Long byteLength) {
		if (productDataType == null || byteLength == null)
			return byteLength;
		productDataType = productDataType.toUpperCase(java.util.Locale.ROOT);
		if (productDataType.startsWith("NTEXT") || productDataType.startsWith("NCHAR")
				|| productDataType.startsWith("NVARCHAR")) {
			if (byteLength.longValue() > 0) {
				return byteLength.longValue() / 2;
			}
		}
		return byteLength;
	}
}
