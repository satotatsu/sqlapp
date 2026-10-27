/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.db2.Db2Nickname;
import com.sqlapp.data.db.sql.AlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;

public class Db2AlterTableFactory extends AlterTableFactory {
	@Override
	protected void addAddColumn(Table original, Table table, DbObjectDifference difference, List<SqlOperation> result) {
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110)) {
			super.addAddColumn(original, table, difference, result);
			return;
		}
		Column column = difference.getTarget(Column.class);
		if (column.isIdentity() || column.getFormula() != null || !column.getDefinition().isEmpty()
				|| !column.getStatement().isEmpty())
			throw new IllegalArgumentException(
					"Db2 ADD COLUMN with generated/opaque attributes requires an explicit native plan: "
							+ column.getName());
		var builder = new com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder(getDialect());
		builder.definition(column, false);
		String name = Db2CreationContext.qualified(table.getSchemaName(), table.getName());
		result.add(new SqlOperation("ALTER TABLE " + name + " ADD COLUMN "
				+ Db2CreationContext.identifier(column.getName()) + " " + builder, SqlType.ALTER, null, column));
		if (column.getRemarks() != null)
			result.add(new SqlOperation(Db2AlterCommentSql.comment(getDialect(),
					"COLUMN " + name + "." + Db2CreationContext.identifier(column.getName()), column.getRemarks()),
					SqlType.SET_COMMENT, column));
	}

	@Override
	protected void addDeleteColumn(Table original, Table table, DbObjectDifference difference,
			List<SqlOperation> result) {
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110)) {
			super.addDeleteColumn(original, table, difference, result);
			return;
		}
		Column column = difference.getOriginal(Column.class);
		result.add(
				new SqlOperation(
						"ALTER TABLE " + Db2CreationContext.qualified(table.getSchemaName(), table.getName())
								+ " DROP COLUMN " + Db2CreationContext.identifier(column.getName()),
						SqlType.ALTER, column));
		result.add(new SqlOperation("-- Db2 DROP COLUMN may require an explicit table REORG before subsequent access.",
				SqlType.COMMENT, column));
	}

	@Override
	protected void addRenameOrAlterColumn(Table original, Table table, DbObjectDifference difference,
			List<SqlOperation> result) {
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110)) {
			super.addRenameOrAlterColumn(original, table, difference, result);
			return;
		}
		result.addAll(Db2ColumnAlterSql.create(getDialect(), table, difference.getOriginal(Column.class),
				difference.getTarget(Column.class), difference));
	}

	@Override
	protected void addOtherDefinitions(java.util.Map<String, Difference<?>> differences, Table original, Table table,
			List<SqlOperation> result) {
		super.addOtherDefinitions(differences, original, table, result);
		if (getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110 && differences.containsKey("remarks"))
			result.add(new SqlOperation(Db2AlterCommentSql.comment(getDialect(),
					"TABLE " + Db2CreationContext.qualified(table.getSchemaName(), table.getName()),
					table.getRemarks()), SqlType.SET_COMMENT, original, table));
	}

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
		if (getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110) {
			if (difference.getState() == State.Unchanged)
				return List.of();
			if (difference.getState() == State.Added || difference.getState() == State.Deleted) {
				Table value = before == null ? after : before;
				com.sqlapp.data.db.sql.SqlFactory<Table> factory = getSqlFactoryRegistry().getSqlFactory(value,
						before == null ? SqlType.CREATE : SqlType.DROP);
				return factory.createSql(value);
			}
			if (!java.util.Set.of("columns", "indexes", "constraints", "remarks")
					.containsAll(difference.getChangedProperties().keySet()))
				throw new IllegalArgumentException("Db2 table ALTER needs an explicit migration plan for attributes: "
						+ difference.getChangedProperties().keySet());
		}
		return super.createDiffSql(difference);
	}
}
