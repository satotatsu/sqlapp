/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.max;
import static com.sqlapp.util.CommonUtils.rtrim;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.metadata.MetadataReader;
import com.sqlapp.data.db.metadata.ReaderUtils;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.ArgumentRoutine;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Routine;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.CommonUtils;

public class HsqlUtils extends ReaderUtils {

	private static final Pattern EXTERNAL_PATTERN = Pattern.compile(".*\\s+EXTERNAL\\s+NAME\\s*'(.*)'.*",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	protected static void setRoutineInfo(ResultSet rs, ArgumentRoutine<?> obj) throws SQLException {
		obj.setCatalogName(getString(rs, "ROUTINE_CATALOG"));
		obj.setSchemaName(getString(rs, "ROUTINE_SCHEMA"));
		obj.setSpecificNameLiteral(getString(rs, MetadataReader.SPECIFIC_NAME));
		obj.setLanguage(getString(rs, "ROUTINE_BODY"));
		obj.setDeterministic(toBoolean(getString(rs, "IS_DETERMINISTIC")));
		obj.setSqlDataAccess(getString(rs, MetadataReader.SQL_DATA_ACCESS));
		obj.setSqlSecurity(getString(rs, MetadataReader.SECURITY_TYPE));
		obj.setMaxDynamicResultSets(getInteger(rs, MetadataReader.MAX_DYNAMIC_RESULT_SETS));
		String externalName = getString(rs, "EXTERNAL_NAME");
		if (!CommonUtils.isEmpty(externalName)) {
			String routine_definition = getString(rs, "ROUTINE_DEFINITION");
			Matcher matcher = EXTERNAL_PATTERN.matcher(routine_definition);
			String fullExternal = matcher.matches() ? matcher.group(1) : externalName;
			int pos = fullExternal.indexOf(":");
			String prefix = null;
			if (pos > 0) {
				prefix = fullExternal.substring(0, pos);
				obj.setClassNamePrefix(prefix);
				fullExternal = fullExternal.replace(prefix + ":", "");
			}
			pos = fullExternal.lastIndexOf(".");
			if (pos > 0) {
				obj.setClassName(fullExternal.substring(0, pos));
				obj.setMethodName(fullExternal.substring(pos + 1));
			} else {
				obj.setMethodName(fullExternal);
			}
			obj.setLanguage(getString(rs, "EXTERNAL_LANGUAGE"));
		}
	}

	protected static String normalizeStatement(AbstractSchemaObject<?> obj, String statement) {
		if (statement == null) {
			return null;
		}
		return statement;
	}

	/**
	 * ResultSetから指定したカラムの文字列を取得します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected static String getString(ResultSet rs, String name) throws SQLException {
		return rtrim(rs.getString(name));
	}

	protected static void setNamedArgument(ResultSet rs, NamedArgument obj, Routine<?> routine) throws SQLException {
		routine.setName(getString(rs, MetadataReader.ROUTINE_NAME));
		routine.setCatalogName(getString(rs, MetadataReader.SPECIFIC_CATALOG));
		routine.setSchemaName(getString(rs, MetadataReader.SPECIFIC_SCHEMA));
		routine.setSpecificNameLiteral(getString(rs, MetadataReader.SPECIFIC_NAME));
		String productDataType = getString(rs, "DATA_TYPE");
		obj.setCatalogName(getString(rs, MetadataReader.SPECIFIC_CATALOG));
		obj.setSchemaName(getString(rs, MetadataReader.SPECIFIC_SCHEMA));
		SchemaUtils.setRoutine(obj, routine);
		obj.getSpecifics().put("hsql.argumentNameLiteral.v1", "true");
		if (routine.getDialect() instanceof com.sqlapp.data.db.dialect.hsql.Hsql2_0_0) {
			String mode = getString(rs, "PARAMETER_MODE");
			if (mode != null && !java.util.Set.of("IN", "OUT", "INOUT").contains(mode))
				throw new SQLException("Unknown HSQLDB parameter mode: " + mode);
			if (mode != null)
				obj.setDirection(com.sqlapp.jdbc.sql.ParameterDirection.parse(mode));
		}
		String interval_type = getString(rs, "INTERVAL_TYPE");
		String domainName = getString(rs, "UDT_NAME");
		// String domain_name=getString(rs, "DOMAIN_NAME");
		// String udt_name=getString(rs, "UDT_NAME");
		Long char_maxlength = getLong(rs, "CHARACTER_MAXIMUM_LENGTH");
		Long numeric_precision = getLong(rs, "NUMERIC_PRECISION");
		Integer numeric_scale = getInteger(rs, "NUMERIC_SCALE");
		Integer datetime_scale = getInteger(rs, "DATETIME_PRECISION");
		if (!isEmpty(domainName)) {
			obj.setDataTypeName(routine.getDialect() instanceof com.sqlapp.data.db.dialect.hsql.Hsql2_0_0
					? qualifiedType(routine.getDialect(), getString(rs, "UDT_SCHEMA"), domainName)
					: domainName);
			obj.setDataType(DataType.DOMAIN);
		} else if (!isEmpty(interval_type)) {
			Long interval_precision = getLong(rs, "INTERVAL_PRECISION");
			obj.getDialect().setDbType(productDataType, interval_precision, datetime_scale, obj);
			obj.setLength(interval_precision);
		} else {
			obj.getDialect().setDbType(productDataType, max(char_maxlength, numeric_precision), numeric_scale, obj);
		}
	}

	protected static String qualifiedType(com.sqlapp.data.db.dialect.Dialect dialect, String schema, String name) {
		var sql = dialect.createSqlBuilder();
		if (schema != null)
			sql.name(schema)._add(".");
		return sql.name(name).toString();
	}

	public static String formatStatement(String sql) {
		return sql;
	}
}
