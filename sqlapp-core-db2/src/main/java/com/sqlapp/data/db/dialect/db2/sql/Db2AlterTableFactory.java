/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.db2.Db2Nickname;
import com.sqlapp.data.db.sql.AlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;

public class Db2AlterTableFactory extends AlterTableFactory {
	@Override
	protected void addIndexDefinition(Table originalTable, Table table, com.sqlapp.data.schemas.Index originalIndex,
			com.sqlapp.data.schemas.Index index, DbObjectDifference difference, List<SqlOperation> result) {
		if (getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110
				&& Db2AlterIndexFactory.canAlter(difference)) {
			com.sqlapp.data.db.sql.SqlFactory<com.sqlapp.data.schemas.Index> factory = getSqlFactoryRegistry()
					.getSqlFactory(index, com.sqlapp.data.db.sql.SqlType.ALTER);
			result.addAll(factory.createDiffSql(difference));
			return;
		}
		super.addIndexDefinition(originalTable, table, originalIndex, index, difference, result);
	}

	@Override
	protected void addDropIndexDefinition(Table originalTable, Table table, com.sqlapp.data.schemas.Index originalIndex,
			com.sqlapp.data.schemas.Index index, DbObjectDifference difference, List<SqlOperation> result) {
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110)) {
			super.addDropIndexDefinition(originalTable, table, originalIndex, index, difference, result);
			return;
		}
		com.sqlapp.data.db.sql.SqlFactory<com.sqlapp.data.schemas.Index> factory = getSqlFactoryRegistry()
				.getSqlFactory(originalIndex, com.sqlapp.data.db.sql.SqlType.DROP);
		result.addAll(factory.createSql(originalIndex));
	}

	@Override
	protected void addCreateIndexDefinition(Table originalTable, Table table,
			com.sqlapp.data.schemas.Index originalIndex, com.sqlapp.data.schemas.Index index,
			DbObjectDifference difference, List<SqlOperation> result) {
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110)) {
			super.addCreateIndexDefinition(originalTable, table, originalIndex, index, difference, result);
			return;
		}
		com.sqlapp.data.db.sql.SqlFactory<com.sqlapp.data.schemas.Index> factory = getSqlFactoryRegistry()
				.getSqlFactory(index, com.sqlapp.data.db.sql.SqlType.CREATE);
		result.addAll(factory.createSql(index));
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Table before = difference.getOriginal(Table.class), after = difference.getTarget(Table.class);
		if ((before != null && Db2Nickname.isNickname(before)) || (after != null && Db2Nickname.isNickname(after))) {
			if (before != null) {
				if (Db2Nickname.isNickname(before))
					Db2Nickname.get(before);
			}
			if (after != null) {
				if (Db2Nickname.isNickname(after))
					Db2Nickname.get(after);
			}
			if (difference.getState() == State.Unchanged)
				return List.of();
			if (difference.getState() == State.Added) {
				com.sqlapp.data.db.sql.SqlFactory<Table> factory = getSqlFactoryRegistry().getSqlFactory(after,
						com.sqlapp.data.db.sql.SqlType.CREATE);
				return factory.createSql(after);
			}
			if (difference.getState() == State.Deleted) {
				com.sqlapp.data.db.sql.SqlFactory<Table> factory = getSqlFactoryRegistry().getSqlFactory(before,
						com.sqlapp.data.db.sql.SqlType.DROP);
				return factory.createSql(before);
			}
			if (difference.getState() == State.Modified)
				throw new IllegalArgumentException(
						"Db2 nickname changes require an explicit native ALTER or recreation plan; dependent objects and remote mappings must be coordinated");
		}
		return super.createDiffSql(difference);
	}
}
