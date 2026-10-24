/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Constant;

public class Db2DropGlobalVariableFactory extends SimpleSqlFactory<Constant, Db2SqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Constant variable) {
		Db2CreateGlobalVariableFactory.validate(variable, getDialect());
		List<SqlOperation> result = new ArrayList<>();
		addSql(result,
				createSqlBuilder()._add("DROP VARIABLE "
						+ Db2CreationContext.qualified(variable.getSchemaName(), variable.getName()) + " RESTRICT"),
				SqlType.DROP, variable);
		return result;
	}
}
