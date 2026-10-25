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
			for (String sql : Db2DefinitionSql.statements(definitions, getDialect())) {
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
			String renderedType = Db2NativeTypeSql.render(getDialect(), fields, variable.getName());
			List<String> statements = variable.getStatement();
			if ("C".equals(fields.get("READONLY")) && statements.isEmpty())
				throw new IllegalArgumentException(
						"Db2 variable requires one DEFAULT/CONSTANT expression: " + variable.getName());
			String expression = statements.isEmpty() ? null : Db2DefinitionSql.text(statements);
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
