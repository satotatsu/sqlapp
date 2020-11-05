/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.mysql.metadata;

import java.io.InputStream;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.MetadataReader;
import com.sqlapp.data.db.metadata.ReaderUtils;
import com.sqlapp.data.schemas.ArgumentRoutine;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.FileUtils;

public class MySqlUtils extends ReaderUtils {

	protected static void setRoutineInfo(ResultSet rs,
			ArgumentRoutine<?> routine) throws SQLException {
		routine.setCatalogName(rs.getString("ROUTINE_CATALOG"));
		routine.setSchemaName(rs.getString("ROUTINE_SCHEMA"));
		routine.setRemarks(rs.getString("ROUTINE_COMMENT"));
		routine.setCreatedAt(rs.getTimestamp("CREATED"));
		routine.setLastAlteredAt(rs.getTimestamp("LAST_ALTERED"));
		routine.setDeterministic(Converters.getDefault().convertObject(
				rs.getString("IS_DETERMINISTIC"), Boolean.class));
		routine.setStatement(rs.getString("ROUTINE_DEFINITION"));
		routine.setSqlDataAccess(rs.getString(MetadataReader.SQL_DATA_ACCESS));
		routine.setSqlSecurity(rs.getString(MetadataReader.SECURITY_TYPE));
	}

	/**
	 * カタログにCharacterSemanticsを設定します
	 * 
	 * @param catalog
	 */
	protected static void setCharacterSemantics(Catalog catalog) {
		if (catalog.getProductMajorVersion() != null
				&& catalog.getProductMajorVersion().intValue() == 4) {
			if (catalog.getProductMinorVersion() != null
					&& catalog.getProductMinorVersion().intValue() < 1) {
				catalog.setCharacterSemantics(CharacterSemantics.Byte);
				return;
			}
		} else if (catalog.getProductMajorVersion() != null
				&& catalog.getProductMajorVersion().intValue() < 4) {
			catalog.setCharacterSemantics(CharacterSemantics.Byte);
			return;
		} else {
			catalog.setCharacterSemantics(CharacterSemantics.Char);
		}
	}

	/**
	 * SchemaにCharacterSemanticsを設定します
	 * 
	 * @param catalog
	 */
	protected static void setCharacterSemantics(Schema schema) {
		if (schema.getProductMajorVersion() != null
				&& schema.getProductMajorVersion().intValue() == 4) {
			if (schema.getProductMinorVersion() != null
					&& schema.getProductMinorVersion().intValue() < 1) {
				schema.setCharacterSemantics(CharacterSemantics.Byte);
				return;
			}
		} else if (schema.getProductMajorVersion() != null
				&& schema.getProductMajorVersion().intValue() < 4) {
			schema.setCharacterSemantics(CharacterSemantics.Byte);
			return;
		} else {
			schema.setCharacterSemantics(CharacterSemantics.Char);
		}
	}

	protected static final Pattern PROCEDURE_ARGUMENT_PATTERN = Pattern
			.compile("([^ ]+)\\s+([^ ]+)\\s+(.*)", Pattern.CASE_INSENSITIVE);

	/**
	 * Procedureの引数を取得します
	 * 
	 * @param arg
	 * @param dialect
	 * @throws SQLException
	 */
	protected static void setProcedureNamedArgument(String arg,
			NamedArgument obj) {
		Matcher matcher = PROCEDURE_ARGUMENT_PATTERN.matcher(arg);
		matcher.matches();
		String inOut = matcher.group(1);
		String name = matcher.group(2);
		String productDataType = matcher.group(3);
		obj.setName(name);
		obj.setDirection(ParameterDirection.parse(inOut));
		obj.getDialect().setDbType(productDataType, null, null, obj);
	}

	protected static final Pattern FUNCTION_ARGUMENT_PATTERN = Pattern.compile(
			"([^ ]+)\\s+(.*)", Pattern.CASE_INSENSITIVE);

	/**
	 * Functionの引数を取得します
	 * 
	 * @param arg
	 * @param dialect
	 * @throws SQLException
	 */
	protected static NamedArgument getFunctionNamedArgument(String arg,
			Dialect dialect) {
		Matcher matcher = FUNCTION_ARGUMENT_PATTERN.matcher(arg);
		matcher.matches();
		String name = matcher.group(1);
		String productDataType = matcher.group(2);
		NamedArgument obj = new NamedArgument(name);
		dialect.setDbType(productDataType,null, null, obj);
		return obj;
	}

	/**
	 * Blobを文字列として読み出します
	 * 
	 * @param rs
	 * @param name
	 * @throws SQLException
	 */
	protected static String readBlobAsString(ResultSet rs, String name)
			throws SQLException {
		Blob blob = null;
		InputStream is = null;
		try {
			blob = rs.getBlob(name);
			is = blob.getBinaryStream();
			return FileUtils.readText(is, "utf8");
		} finally {
			if (blob!=null){
				blob.free();
			}
			FileUtils.close(is);
		}
	}

}
