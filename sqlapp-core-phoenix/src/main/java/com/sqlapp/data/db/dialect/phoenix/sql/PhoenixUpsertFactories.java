/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.db.sql.InsertFactory;
import com.sqlapp.data.db.sql.InsertRowsFactory;
import com.sqlapp.data.db.sql.InsertSelectNotExistsTableFactory;
import com.sqlapp.data.db.sql.AbstractInsertFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;

/**
 * The shared insert factories also generate Phoenix's positional JDBC batch
 * SQL.
 */
public final class PhoenixUpsertFactories {
	private PhoenixUpsertFactories() {
	}

	public static class Single extends InsertFactory {
		@Override
		protected PhoenixSqlBuilder newSqlBuilder(Dialect dialect) {
			return new UpsertBuilder(dialect);
		}
	}

	public static class Rows extends InsertRowsFactory {
		@Override
		protected PhoenixSqlBuilder newSqlBuilder(Dialect dialect) {
			return new UpsertBuilder(dialect);
		}
	}

	public static class SelectNotExists extends InsertSelectNotExistsTableFactory {
		@Override
		protected PhoenixSqlBuilder newSqlBuilder(Dialect dialect) {
			return new UpsertBuilder(dialect);
		}
	}

	public static class AddedRow extends com.sqlapp.data.db.sql.SimpleSqlFactory<Row, PhoenixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(Row row) {
			final var table = row.getTable();
			if (table == null)
				throw new IllegalArgumentException("Phoenix row UPSERT requires an owning table");
			final var factory = new RowValues();
			factory.setSqlFactoryRegistry(getSqlFactoryRegistry());
			return factory.render(row);
		}
	}

	private static class RowValues extends AbstractInsertFactory<PhoenixSqlBuilder> {
		@Override
		protected PhoenixSqlBuilder newSqlBuilder(Dialect dialect) {
			return new UpsertBuilder(dialect);
		}

		List<SqlOperation> render(Row row) {
			final var builder = createSqlBuilder();
			final var columns = addInsertIntoTable(row.getTable(), builder);
			for (var key : PhoenixRowDeleteFactory.requirePrimaryKey(row)) {
				if (!columns.contains(key))
					throw new IllegalArgumentException(
							"Phoenix row UPSERT cannot write primary-key column: " + key.getName());
			}
			builder.space().brackets(true, () -> {
				for (int i = 0; i < columns.size(); i++)
					builder.comma(i > 0)._add(getValueDefinitionForInsert(row, columns.get(i)));
			});
			return List.of(createOperation(builder.toString(), SqlType.INSERT, null, row));
		}
	}

	private static class UpsertBuilder extends PhoenixSqlBuilder {
		UpsertBuilder(Dialect dialect) {
			super(dialect);
		}

		@Override
		public PhoenixSqlBuilder name(Column column) {
			final String family = column.getSpecifics().get(PhoenixCreateTableFactory.COLUMN_FAMILY);
			if (family != null) {
				if (family.isBlank())
					throw new IllegalArgumentException("Phoenix column family must not be blank: " + column.getName());
				name(family)._add(".");
			}
			return super.name(column);
		}
	}
}
