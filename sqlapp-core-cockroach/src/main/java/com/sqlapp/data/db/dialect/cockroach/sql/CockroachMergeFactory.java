/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres95MergeFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Table;

/** Uses CockroachDB ON CONFLICT without falling back to unsupported SQL MERGE. */
public class CockroachMergeFactory extends Postgres95MergeFactory {
	@Override
	public List<SqlOperation> createSql(final Table table) {
		var key = getUniqueConstraint(table);
		if (key == null) {
			throw new IllegalArgumentException("CockroachDB merge requires a modeled primary or unique constraint");
		}
		return super.createSql(table);
	}
}
