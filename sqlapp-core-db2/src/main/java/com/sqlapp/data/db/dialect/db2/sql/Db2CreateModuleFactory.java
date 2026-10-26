/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Package;

/** The shared Package model represents SQL modules, not SYSCAT.PACKAGES. */
public class Db2CreateModuleFactory extends SimpleSqlFactory<Package, Db2SqlBuilder> {
	static void validate(Package module, Dialect dialect) {
		if (!(dialect instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 module support requires LUW 11.1 or later");
		if (!"MODULE".equals(module.getSpecifics().get("DB2_OBJECT_TYPE")))
			throw new IllegalArgumentException(
					"Db2 Package must explicitly represent a SQL MODULE: " + module.getName());
		if (!Set.of("M", "A", "P").contains(module.getSpecifics().getOrDefault("MODULETYPE", "")))
			throw new IllegalArgumentException("Db2 module requires MODULETYPE M, A or P");
		Db2CreationContext.qualified(module.getSchemaName(), module.getName());
	}

	@Override
	public List<SqlOperation> createSql(Package module) {
		validate(module, getDialect());
		List<SqlOperation> result = new ArrayList<>();
		String name = Db2CreationContext.qualified(module.getSchemaName(), module.getName());
		if (!module.getDefinition().isEmpty()) {
			for (String sql : Db2DefinitionSql.statements(module.getDefinition(), getDialect())) {
				if (sql == null || sql.isBlank())
					throw new IllegalArgumentException("Db2 module definition requires executable statements");
				addSql(result, createSqlBuilder()._add(sql), SqlType.CREATE, module);
			}
		} else {
			String type = module.getSpecifics().get("MODULETYPE");
			String sql;
			List<SqlOperation> members = List.of();
			if ("A".equals(type)) {
				sql = "CREATE " + ("SYSPUBLIC".equals(module.getSchemaName()) ? "PUBLIC " : "") + "ALIAS " + name
						+ " FOR MODULE " + Db2CreationContext.qualified(module.getSpecifics().get("BASE_MODULESCHEMA"),
								module.getSpecifics().get("BASE_MODULENAME"));
			} else {
				if (!"M".equals(type) || !module.getStatement().isEmpty() || module.getBody() != null)
					throw new IllegalArgumentException(
							"Db2 module with members or PL/SQL package requires complete ordered CREATE/ALTER MODULE definition: "
									+ name);
				members = Db2ModuleMemberSql.create(module, this,
						(text, operationType) -> createOperation(text, operationType, module));
				sql = "CREATE MODULE " + name;
			}
			addSql(result, createSqlBuilder()._add(sql), SqlType.CREATE, module);
			result.addAll(members);
		}
		if (module.getRemarks() != null) {
			String target = "A".equals(module.getSpecifics().get("MODULETYPE"))
					? ("SYSPUBLIC".equals(module.getSchemaName()) ? "PUBLIC " : "") + "ALIAS " + name + " FOR MODULE"
					: "MODULE " + name;
			addSql(result,
					createSqlBuilder()
							._add("COMMENT ON " + target + " IS '" + module.getRemarks().replace("'", "''") + "'"),
					SqlType.SET_COMMENT, module);
		}
		return result;
	}
}
