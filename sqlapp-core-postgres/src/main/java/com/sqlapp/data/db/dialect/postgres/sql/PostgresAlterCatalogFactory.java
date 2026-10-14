/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;
import com.sqlapp.data.db.sql.AlterCatalogFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects;

/** Rejects automatic replacement of database-wide definition-backed objects. */
public class PostgresAlterCatalogFactory extends AlterCatalogFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getOriginal() instanceof Catalog original && difference.getTarget() instanceof Catalog target
				&& !PostgresSchemaObjects.get(original).equals(PostgresSchemaObjects.get(target))) {
			throw new IllegalArgumentException("PostgreSQL-only database objects changed. Use explicit reviewed object DDL; "
					+ "generic ALTER cannot safely infer replication or extension changes.");
		}
		return super.createDiffSql(difference);
	}
}
