/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constant;

/**
 * Complete definitions or DEFAULT/CONSTANT expression plus catalog components.
 */
public class Db2CreateGlobalVariableFactory extends SimpleSqlFactory<Constant, Db2SqlBuilder> {
	static void validate(Constant variable, com.sqlapp.data.db.dialect.Dialect dialect) {
		if (!(dialect instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 global variable support requires LUW 11.1 or later");
		if (!"VARIABLE".equals(variable.getSpecifics().get("DB2_OBJECT_TYPE")))
			throw new IllegalArgumentException(
					"Db2 Constant must explicitly represent a VARIABLE: " + variable.getName());
		Db2CreationContext.qualified(variable.getSchemaName(), variable.getName());
	}

	@Override
	public List<SqlOperation> createSql(Constant variable) {
		validate(variable, getDialect());
		List<SqlOperation> result = new ArrayList<>();
		List<String> definitions = variable.getDefinition();
		if (!definitions.isEmpty()) {
			for (String sql : definitions) {
				if (sql == null || sql.isBlank())
					throw new IllegalArgumentException("Db2 variable definition must contain executable statements");
				addSql(result, createSqlBuilder()._add(sql), SqlType.CREATE, variable);
			}
		} else {
			var fields = variable.getSpecifics();
			if (!"S".equals(fields.get("SCOPE")) || !Set.of("N", "C").contains(fields.getOrDefault("READONLY", ""))
					|| !"SYSIBM".equals(fields.get("TYPESCHEMA")) || fields.get("TYPEMODULENAME") != null)
				throw new IllegalArgumentException(
						"Db2 variable needs a complete CREATE definition for unsupported scope or type: "
								+ variable.getName());
			Column column = new Column(variable.getName());
			String type = fields.get("TYPENAME");
			Long length = fields.get("LENGTH") == null ? null : Long.valueOf(fields.get("LENGTH"));
			Integer scale = fields.get("SCALE") == null ? null : Integer.valueOf(fields.get("SCALE"));
			if (Set.of("CHAR", "CHARACTER", "VARCHAR", "GRAPHIC", "VARGRAPHIC", "BLOB", "CLOB", "DBCLOB", "BINARY",
					"VARBINARY", "DECIMAL", "NUMERIC").contains(type == null ? "" : type)
					&& (length == null || length <= 0))
				throw new IllegalArgumentException(
						"Db2 variable data type requires its declared length/precision: " + variable.getName());
			if (Set.of("DECIMAL", "NUMERIC", "TIMESTAMP", "TIMESTAMP WITHOUT TIME ZONE")
					.contains(type == null ? "" : type) && scale == null)
				throw new IllegalArgumentException(
						"Db2 variable data type requires its declared scale/precision: " + variable.getName());
			if (Set.of("DECIMAL", "NUMERIC").contains(type == null ? "" : type)
					&& (length > 31 || scale < 0 || scale > length))
				throw new IllegalArgumentException(
						"Db2 variable decimal precision/scale is invalid: " + variable.getName());
			if (type == null || Set.of("CURSOR", "ROW", "VECTOR", "REFERENCE").contains(type)
					|| !getDialect().setDbType(type, length, scale, column) || column.getDataType() == null
					|| column.getDataType().isOther() || column.getDataType().isType()
					|| column.getDataType().isDomain())
				throw new IllegalArgumentException(
						"Db2 variable needs a complete CREATE definition for its data type: " + variable.getName());
			String units = fields.get("TYPESTRINGUNITS");
			if (Set.of("CHARACTER", "CHAR", "VARCHAR", "GRAPHIC", "VARGRAPHIC", "CLOB", "DBCLOB", "LONG VARCHAR",
					"LONG VARGRAPHIC").contains(type)) {
				String codepage = fields.get("CODEPAGE");
				if (codepage == null || (units == null && (Set.of("1208", "1200").contains(codepage)
						|| Set.of("GRAPHIC", "VARGRAPHIC", "DBCLOB", "LONG VARGRAPHIC").contains(type))))
					throw new IllegalArgumentException(
							"Db2 variable requires explicit string units or a complete CREATE definition: "
									+ variable.getName());
			}
			boolean binary = "0".equals(fields.get("CODEPAGE"))
					&& Set.of("CHARACTER", "CHAR", "VARCHAR", "LONG VARCHAR").contains(type);
			Long renderedLength = length;
			if (units != null && !binary) {
				if (!Set.of("OCTETS", "CODEUNITS16", "CODEUNITS32").contains(units)
						|| fields.get("STRINGUNITSLENGTH") == null)
					throw new IllegalArgumentException(
							"Db2 variable string units require an explicit declared length: " + variable.getName());
				renderedLength = Long.valueOf(fields.get("STRINGUNITSLENGTH"));
				if (renderedLength <= 0)
					throw new IllegalArgumentException("Db2 variable string unit length must be positive");
			}
			Db2SqlBuilder typeBuilder = createSqlBuilder();
			typeBuilder.typeDefinition(column.getDataType(), null, renderedLength, scale);
			String renderedType = typeBuilder.toString();
			if (units != null && !binary) {
				if (!renderedType.endsWith(")"))
					throw new IllegalArgumentException("Db2 variable string units require a character type");
				renderedType = renderedType.substring(0, renderedType.length() - 1) + ", " + units + ")";
			}
			if (binary)
				renderedType += " FOR BIT DATA";
			List<String> statements = variable.getStatement();
			if (statements.size() > 1 || ("C".equals(fields.get("READONLY")) && statements.isEmpty()))
				throw new IllegalArgumentException(
						"Db2 variable requires one DEFAULT/CONSTANT expression: " + variable.getName());
			String expression = statements.isEmpty() ? null : statements.getFirst();
			if (expression != null && (expression.isBlank() || expression.indexOf('\0') >= 0))
				throw new IllegalArgumentException("Invalid Db2 variable expression");
			for (String sql : Db2CreationContext.sql(fields.get("QUALIFIER"), fields.get("FUNC_PATH")))
				addSql(result, createSqlBuilder()._add(sql), SqlType.SET_SEARCH_PATH_TO_SCHEMA, variable);
			String sql = "CREATE VARIABLE " + Db2CreationContext.qualified(variable.getSchemaName(), variable.getName())
					+ " " + renderedType;
			if (expression != null)
				sql += ("C".equals(fields.get("READONLY")) ? " CONSTANT " : " DEFAULT ") + expression;
			addSql(result, createSqlBuilder()._add(sql), SqlType.CREATE, variable);
		}
		if (variable.getRemarks() != null)
			addSql(result,
					createSqlBuilder()._add("COMMENT ON VARIABLE "
							+ Db2CreationContext.qualified(variable.getSchemaName(), variable.getName()) + " IS '"
							+ variable.getRemarks().replace("'", "''") + "'"),
					SqlType.SET_COMMENT, variable);
		return result;
	}
}
