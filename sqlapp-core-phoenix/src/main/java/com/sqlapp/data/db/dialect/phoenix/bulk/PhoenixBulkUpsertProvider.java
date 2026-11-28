package com.sqlapp.data.db.dialect.phoenix.bulk;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.phoenix.Phoenix;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.*;

/**
 * Direct Phoenix JDBC UPSERT for full primary-key insert-or-update workloads.
 */
public final class PhoenixBulkUpsertProvider implements BulkUpsertProvider {
	@Override
	public boolean supports(Dialect dialect) {
		return dialect instanceof Phoenix;
	}

	@Override
	public BulkUpsertExecutor create(Dialect dialect) {
		return new BulkUpsertExecutor() {
			@Override
			public boolean supportsCallerTransactionAtomicity() {
				return false;
			}

			@Override
			public long execute(Connection connection, Table table, BulkUpsertOption options) throws SQLException {
				final var effective = options == null ? BulkUpsertOption.defaults() : options;
				if (effective.isUseTransaction() || !effective.isInsertWhenNotMatched()
						|| !effective.isUpdateWhenMatched() || !effective.getUpdateColumns().isEmpty()
						|| effective.getStagingTableName() != null)
					throw new IllegalArgumentException("Phoenix direct bulk UPSERT requires useTransaction=false, "
							+ "both actions, all writable update columns and no staging table name");
				final var plan = BulkUpsertPlan.resolve(table, effective);
				final var primaryKey = table.getPrimaryKeyConstraint();
				final var names = new HashSet<>(plan.getKeyColumns().stream().map(Column::getName).toList());
				if (primaryKey == null || primaryKey.getColumns().size() != names.size()
						|| primaryKey.getColumns().stream().anyMatch(column -> !names.contains(column.getName())))
					throw new IllegalArgumentException("Phoenix bulk UPSERT requires the complete primary key");
				final String tenant = table.getCatalogName();
				if (tenant != null && !tenant.isEmpty() && !tenant.equals(connection.getCatalog()))
					throw new IllegalArgumentException(
							"Phoenix bulk UPSERT connection does not match the table tenant");
				final var input = plan.createStagingTable(table.getName()).setSchemaName(table.getSchemaName())
						.setCatalogName(table.getCatalogName());
				return new JdbcBatchBulkInsertExecutor(dialect) {
					@Override
					protected void bind(PreparedStatement statement, Row row, List<Column> columns)
							throws SQLException {
						for (var key : plan.getKeyColumns())
							if (row.get(key) == null)
								throw new IllegalArgumentException(
										"Phoenix bulk UPSERT requires a non-null key: " + key.getName());
						super.bind(statement, row, columns);
					}
				}.execute(connection, input, effective.getBulkOption());
			}
		};
	}
}
