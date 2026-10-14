/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;
import com.sqlapp.data.db.sql.AlterSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects;

/** Keeps opaque PostgreSQL definitions from being silently ignored by generic ALTER. */
public class PostgresAlterSchemaFactory extends AlterSchemaFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getOriginal() instanceof Schema original && difference.getTarget() instanceof Schema target
				&& !PostgresSchemaObjects.get(original).equals(PostgresSchemaObjects.get(target))) {
			throw new IllegalArgumentException("PostgreSQL-only schema objects changed. Use explicit reviewed object DDL; "
					+ "generic ALTER cannot safely infer replacement of definition-backed objects.");
		}
		return super.createDiffSql(difference);
	}
}
