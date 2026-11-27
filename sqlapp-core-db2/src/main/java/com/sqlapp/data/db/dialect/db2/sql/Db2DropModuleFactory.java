/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Package;

public class Db2DropModuleFactory extends SimpleSqlFactory<Package, Db2SqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Package module) {
		Db2CreateModuleFactory.validate(module, getDialect());
		List<SqlOperation> result = new ArrayList<>();
		// Db2 DROP MODULE also removes every contained object. Only explicit DROP uses
		// this factory.
		String name = Db2CreationContext.qualified(module.getSchemaName(), module.getName());
		String sql = "A".equals(module.getSpecifics().get("MODULETYPE")) ? "DROP "
				+ ("SYSPUBLIC".equals(module.getSchemaName()) ? "PUBLIC " : "") + "ALIAS " + name + " FOR MODULE"
				: "DROP MODULE " + name;
		addSql(result, createSqlBuilder()._add(sql), SqlType.DROP, module);
		return result;
	}
}
