/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateCheckConstraintFactory;
import com.sqlapp.data.db.sql.AbstractCreateUniqueConstraintFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.AbstractSqlFactory;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * Inline details remain usable on old engines; named CHECK changes use native
 * SQL on 3.53.1+.
 */
public final class SqliteConstraintFactories {
	private SqliteConstraintFactories() {
	}

	public static class CreateCheck extends AbstractCreateCheckConstraintFactory<SqliteSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(CheckConstraint constraint) {
			validateCheck(constraint, (Sqlite) getDialect(), false);
			if (constraint.getExpression() == null || constraint.getExpression().isBlank())
				throw new IllegalArgumentException("SQLite CHECK addition requires an expression");
			var builder = createSqlBuilder(getDialect());
			builder.alter().table().name(constraint.getSchemaName())._add(".").name(constraint.getTableName()).add()
					.space();
			addObjectDetail(constraint, constraint.getTable(), builder);
			return List.of(createOperation(builder.toString(), SqlType.CREATE, constraint));
		}
	}

	public static class DropCheck extends AbstractSqlFactory<CheckConstraint, SqliteSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(CheckConstraint constraint) {
			validateCheck(constraint, (Sqlite) getDialect(), true);
			var builder = createSqlBuilder(getDialect());
			builder.alter().table().name(constraint.getSchemaName())._add(".").name(constraint.getTableName()).drop()
					.constraint().name(constraint.getName());
			return List.of(createOperation(builder.toString(), SqlType.DROP, constraint));
		}
	}

	private static void validateCheck(CheckConstraint constraint, Sqlite dialect, boolean dropping) {
		if (!dialect.supportsAlterCheckConstraints())
			throw new IllegalArgumentException(
					"SQLite named CHECK changes require SQLite 3.53.1 or later (quoted DROP names are unreliable in 3.53.0)");
		if (constraint.getName() == null || constraint.getName().isBlank() || constraint.getSchemaName() == null
				|| constraint.getSchemaName().isBlank() || constraint.getTableName() == null
				|| constraint.getTableName().isBlank())
			throw new IllegalArgumentException(
					"SQLite CHECK change requires explicit schema, table and constraint names");
		if (dropping && "true".equals(constraint.getSpecifics().get("sqlite.check.unnamed")))
			throw new IllegalArgumentException("SQLite cannot drop an anonymous CHECK by its generated metadata name");
		Table table = constraint.getTable();
		if (table != null && ("true".equals(table.getSpecifics().get("sqlite.virtual"))
				|| com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl
						.isVirtualTable(String.join("\n", table.getDefinition()))))
			throw new IllegalArgumentException("SQLite virtual-table constraints require module-specific planning");
		if (constraint.getDeferrability() != null
				&& constraint.getDeferrability() != com.sqlapp.data.schemas.Deferrability.NotDeferrable)
			throw new IllegalArgumentException("SQLite CHECK constraints cannot be deferred");
	}

	public static class CreateUnique extends AbstractCreateUniqueConstraintFactory<SqliteSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(UniqueConstraint constraint) {
			throw new IllegalArgumentException(
					"SQLite PRIMARY KEY/UNIQUE addition requires a separately planned table rebuild");
		}
	}
}
