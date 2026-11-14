/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constant;
import com.sqlapp.data.schemas.Domain;

/**
 * Host global variables ({@code CREATE VARIABLE}). As in the Db2 LUW dialect a
 * variable is a {@link Constant} with {@code DB2_OBJECT_TYPE=VARIABLE}; its
 * type is kept in the specifics below and its DEFAULT expression in
 * {@code statement}. A complete {@code CREATE VARIABLE} in {@code definition}
 * is replayed verbatim.
 */
public final class Db2HostVariables {
	public static final String OBJECT_TYPE = "DB2_OBJECT_TYPE";
	public static final String VARIABLE = "VARIABLE";
	/** Built-in type name (SYSIBM types only). */
	public static final String TYPE_NAME = "DB2_HOST_VARIABLE_TYPE_NAME";
	/** Length/precision; fractional second digits for TIMESTAMP. */
	public static final String TYPE_LENGTH = "DB2_HOST_VARIABLE_TYPE_LENGTH";
	public static final String TYPE_SCALE = "DB2_HOST_VARIABLE_TYPE_SCALE";
	/** Schema and name of a distinct or array type. */
	public static final String UDT_SCHEMA = "DB2_HOST_VARIABLE_UDT_SCHEMA";
	public static final String UDT_NAME = "DB2_HOST_VARIABLE_UDT_NAME";

	private Db2HostVariables() {
	}

	/**
	 * Builds the transient column used to render and validate the variable type.
	 * String CCSID ({@link Db2HostTables#CCSID}) and FOR BIT DATA
	 * ({@code CODEPAGE=0}) are carried over with the same rules as columns.
	 */
	public static Column column(Constant variable, Dialect dialect) {
		var fields = variable.getSpecifics();
		if (!VARIABLE.equals(fields.get(OBJECT_TYPE)))
			throw new IllegalArgumentException(
					"Db2 host Constant must explicitly represent a VARIABLE: " + variable.getName());
		var column = new Column(variable.getName());
		String udtSchema = fields.get(UDT_SCHEMA), udtName = fields.get(UDT_NAME), type = fields.get(TYPE_NAME);
		if (udtSchema != null || udtName != null) {
			if (udtSchema == null || udtName == null || type != null || fields.get(TYPE_LENGTH) != null
					|| fields.get(TYPE_SCALE) != null)
				throw new IllegalArgumentException(
						"Invalid Db2 host variable user-defined type: " + variable.getName());
			column.setDataType(DataType.OTHER);
			column.setDomain(new Domain(udtName).setSchemaName(udtSchema));
		} else {
			if (type == null || type.isBlank())
				throw new IllegalArgumentException("Db2 host variable requires a data type: " + variable.getName());
			Long length = number(fields.get(TYPE_LENGTH), variable);
			Integer scale = number(fields.get(TYPE_SCALE), variable) == null ? null
					: Math.toIntExact(number(fields.get(TYPE_SCALE), variable));
			if (!dialect.setDbType(type, length, scale, column))
				throw new IllegalArgumentException(
						"Unsupported Db2 host variable type " + type + ": " + variable.getName());
		}
		for (String key : List.of(Db2HostTables.CCSID, "CODEPAGE")) {
			String value = fields.get(key);
			if (value != null)
				column.getSpecifics().put(key, value);
		}
		return column;
	}

	private static Long number(String value, Constant variable) {
		if (value == null)
			return null;
		try {
			return Long.valueOf(value.strip());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid Db2 host variable type attribute: " + variable.getName(), e);
		}
	}

	static String name(Constant variable) {
		return Db2CreationContext.qualified(variable.getSchemaName(), variable.getName());
	}

	/**
	 * {@code CREATE VARIABLE name type [DEFAULT ...]} and
	 * {@code COMMENT ON VARIABLE}. Db2 for z/OS accepts only NULL, a constant or a
	 * special register as DEFAULT, which the catalog reader produces verbatim; on
	 * IBM i the catalog keeps an expression, emitted as {@code (expression)}.
	 */
	public static class Create extends SimpleSqlFactory<Constant, Db2SqlBuilder> {
		@Override
		public List<SqlOperation> createSql(Constant variable) {
			List<SqlOperation> result = new ArrayList<>();
			if (!variable.getDefinition().isEmpty()) {
				if (!VARIABLE.equals(variable.getSpecifics().get(OBJECT_TYPE)))
					throw new IllegalArgumentException(
							"Db2 host Constant must explicitly represent a VARIABLE: " + variable.getName());
				String definition = String.join("\n", variable.getDefinition());
				if (!definition.stripLeading().matches("(?is)CREATE\\s+(?:OR\\s+REPLACE\\s+)?VARIABLE\\b.*"))
					throw new IllegalArgumentException(
							"Variable definition must be a complete CREATE VARIABLE: " + variable.getName());
				result.add(createOperation(definition, SqlType.CREATE, variable));
			} else {
				var builder = (Db2HostSqlBuilder) createSqlBuilder();
				builder._add("CREATE VARIABLE " + name(variable) + " ");
				builder.typeDefinition(column(variable, getDialect()));
				String expression = variable.getStatement().isEmpty() ? null
						: String.join("\n", variable.getStatement()).strip();
				if (expression != null) {
					if (expression.isEmpty() || expression.indexOf('\0') >= 0)
						throw new IllegalArgumentException("Invalid Db2 host variable DEFAULT: " + variable.getName());
					var platform = ((AbstractDb2HostDialect) getDialect()).getPlatform();
					if (!"NULL".equalsIgnoreCase(expression) && platform == AbstractDb2HostDialect.Platform.IBM_I)
						expression = "(" + expression + (expression.contains("--") ? "\n)" : ")");
					builder._add(" DEFAULT " + expression);
				}
				addSql(result, builder, SqlType.CREATE, variable);
			}
			if (variable.getRemarks() != null && !variable.getRemarks().isEmpty()) {
				var comment = createSqlBuilder();
				comment._add("COMMENT ON VARIABLE " + name(variable) + " IS ").sqlChar(variable.getRemarks());
				addSql(result, comment, SqlType.SET_COMMENT, variable);
			}
			return result;
		}
	}

	/** {@code DROP VARIABLE name}; neither host accepts CASCADE. */
	public static class Drop extends SimpleSqlFactory<Constant, Db2SqlBuilder> {
		@Override
		public List<SqlOperation> createSql(Constant variable) {
			if (!VARIABLE.equals(variable.getSpecifics().get(OBJECT_TYPE)))
				throw new IllegalArgumentException(
						"Db2 host Constant must explicitly represent a VARIABLE: " + variable.getName());
			return List.of(createOperation("DROP VARIABLE " + name(variable), SqlType.DROP, variable));
		}
	}
}
