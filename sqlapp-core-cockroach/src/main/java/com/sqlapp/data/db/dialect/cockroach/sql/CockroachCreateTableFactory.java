/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;
import java.util.List;
import com.sqlapp.data.db.dialect.postgres.sql.PostgresCreateTableFactory;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Table;
/** Catalog definitions include Cockroach storage/index syntax not representable by PostgreSQL factories. */
public class CockroachCreateTableFactory extends PostgresCreateTableFactory {
	@Override protected void addCreateIndexDefinition(Table table, com.sqlapp.data.schemas.Index index,
			List<SqlOperation> result) {
		if (table.getConstraints().getUniqueConstraints().stream().anyMatch(c -> c.getIndex()!=null
				&& (java.util.Objects.equals(c.getName(),index.getName())
						|| java.util.Objects.equals(c.getIndex().getName(),index.getName())))) return;
		super.addCreateIndexDefinition(table,index,result);
	}

	@Override public List<SqlOperation> createSql(Table table) {
		if (table.getDefinition() == null || table.getDefinition().isEmpty()) return super.createSql(table);
		var result = new java.util.ArrayList<SqlOperation>();
		var builder = createSqlBuilder(); builder._add(table.getDefinition());
		addSql(result,builder,SqlType.CREATE,table);
		return result;
	}
}
