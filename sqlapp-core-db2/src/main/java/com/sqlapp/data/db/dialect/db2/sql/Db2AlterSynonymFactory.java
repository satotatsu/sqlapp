/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.*;

public class Db2AlterSynonymFactory extends Db2_1110CreateSynonymFactory {
	@Override
	public List<SqlOperation> createSql(com.sqlapp.data.schemas.Synonym target) {
		throw new IllegalArgumentException("Db2 ALTER requires original and target models; use createDiffSql");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		if (difference.getState() == State.Added)
			return super.createSql(difference.getTarget(com.sqlapp.data.schemas.Synonym.class));
		if (difference.getState() == State.Deleted) {
			com.sqlapp.data.schemas.Synonym before = difference.getOriginal(com.sqlapp.data.schemas.Synonym.class);
			com.sqlapp.data.db.sql.SqlFactory<com.sqlapp.data.schemas.Synonym> drop = getSqlFactoryRegistry()
					.getSqlFactory(before, com.sqlapp.data.db.sql.SqlType.DROP);
			return drop.createSql(before);
		}
		if (difference.getState() == State.Modified && Db2AlterCommentSql.onlyRemarks(difference)) {
			com.sqlapp.data.schemas.Synonym after = difference.getTarget(com.sqlapp.data.schemas.Synonym.class);
			String target = Db2_1110CreateSynonymFactory.designator(after, getOptions().isDecorateSchemaName())
					+ " FOR " + Db2_1110CreateSynonymFactory.kind(after);
			return List.of(createOperation(Db2AlterCommentSql.comment(getDialect(), target, after.getRemarks()),
					com.sqlapp.data.db.sql.SqlType.SET_COMMENT, after));
		}
		if (difference.getState() == State.Modified)
			throw new IllegalArgumentException(
					"Db2 alias changes require an explicit replacement plan for dependent objects");
		return super.createDiffSql(difference);
	}
}
