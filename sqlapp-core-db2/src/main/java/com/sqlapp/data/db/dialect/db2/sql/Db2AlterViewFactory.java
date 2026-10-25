/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;

/** View replacement requires an explicit dependency-aware plan. */
public class Db2AlterViewFactory extends Db2CreateViewFactory {
	@Override
	public List<SqlOperation> createSql(com.sqlapp.data.schemas.Table target) {
		throw new IllegalArgumentException("Db2 ALTER requires original and target models; use createDiffSql");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		if (difference.getState() == State.Added)
			return super.createSql(difference.getTarget(com.sqlapp.data.schemas.Table.class));
		if (difference.getState() == State.Deleted) {
			com.sqlapp.data.schemas.Table before = difference.getOriginal(com.sqlapp.data.schemas.Table.class);
			com.sqlapp.data.db.sql.SqlFactory<com.sqlapp.data.schemas.Table> drop = getSqlFactoryRegistry()
					.getSqlFactory(before, com.sqlapp.data.db.sql.SqlType.DROP);
			return drop.createSql(before);
		}
		if (difference.getState() == State.Modified && Db2AlterCommentSql.onlyRemarks(difference)) {
			com.sqlapp.data.schemas.Table after = difference.getTarget(com.sqlapp.data.schemas.Table.class);
			String target = "TABLE " + (getOptions().isDecorateSchemaName() && after.getSchemaName() != null
					? com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.qualified(after.getSchemaName(),
							after.getName())
					: com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.identifier(after.getName()));
			return List.of(createOperation(Db2AlterCommentSql.comment(getDialect(), target, after.getRemarks()),
					com.sqlapp.data.db.sql.SqlType.SET_COMMENT, after));
		}
		if (difference.getState() == State.Modified)
			throw new IllegalArgumentException(
					"Db2 view changes require an explicit replacement plan for dependent objects and privileges");
		return super.createDiffSql(difference);
	}
}
