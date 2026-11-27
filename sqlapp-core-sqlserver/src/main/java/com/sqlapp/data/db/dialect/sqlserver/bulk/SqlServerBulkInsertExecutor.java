/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.bulk;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.microsoft.sqlserver.jdbc.SQLServerBulkCopy;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkOption;

/** SQL Server bulk insert backed by Microsoft JDBC SQLServerBulkCopy. */
public class SqlServerBulkInsertExecutor implements BulkInsertExecutor {
	private static final Logger logger = LogManager.getLogger(SqlServerBulkInsertExecutor.class);

	/**
	 * クラスタ化列ストアインデックスへのバルク投入で、デルタストアを経由せず直接圧縮行グループになる1バッチの最小行数
	 */
	static final int COLUMNSTORE_DIRECT_COMPRESSION_MIN_ROWS = 102_400;

	private final Dialect dialect;

	public SqlServerBulkInsertExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table, final BulkOption options) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		try (SQLServerBulkCopy bulkCopy = new SQLServerBulkCopy(connection);
				BulkData data = new BulkData(table, options)) {
			bulkCopy.setDestinationTableName(
					dialect.getObjectFullName(table.getCatalogName(), table.getSchemaName(), table.getName()));
			bulkCopy.setBulkCopyOptions(BulkData.toSqlServerOptions(options));
			for (final int ordinal : data.getColumnOrdinals()) {
				bulkCopy.addColumnMapping(ordinal, data.getColumnName(ordinal));
			}
			warnIfColumnStoreBatchTooSmall(connection, table, options);
			bulkCopy.writeToServer(data);
			return data.getRowCount();
		}
	}

	/**
	 * クラスタ化列ストアのテーブルに、直接圧縮されない小さいbatchSizeで大量投入する場合に警告します。
	 */
	private void warnIfColumnStoreBatchTooSmall(final Connection connection, final Table table,
			final BulkOption options) {
		final Integer batchSize = options == null ? null : options.getBatchSize();
		final long rowCount = table.getRows().size();
		if (!mayMissDirectCompression(batchSize, rowCount)) {
			return;
		}
		if (hasClusteredColumnStore(connection, table)) {
			logger.warn(columnStoreBatchWarning(table, batchSize, rowCount));
		}
	}

	/**
	 * 行数が直接圧縮の閾値以上なのに、batchSizeが閾値未満か?(未指定は全行1バッチなので対象外)
	 */
	static boolean mayMissDirectCompression(final Integer batchSize, final long rowCount) {
		return batchSize != null && batchSize < COLUMNSTORE_DIRECT_COMPRESSION_MIN_ROWS
				&& rowCount >= COLUMNSTORE_DIRECT_COMPRESSION_MIN_ROWS;
	}

	static String columnStoreBatchWarning(final Table table, final int batchSize, final long rowCount) {
		return "Bulk insert into clustered columnstore table " + table.getName() + " uses batchSize=" + batchSize
				+ " for " + rowCount + " rows. Batches smaller than " + COLUMNSTORE_DIRECT_COMPRESSION_MIN_ROWS
				+ " rows go to the delta store instead of compressed rowgroups; use a batchSize of at least "
				+ COLUMNSTORE_DIRECT_COMPRESSION_MIN_ROWS + " (up to 1048576) or leave it unset.";
	}

	private String quote(final String name) {
		return name == null || name.isEmpty() ? name : dialect.quote(name);
	}

	/**
	 * テーブルにクラスタ化列ストアインデックスがあるか。Schemaモデルにあればそれを使い、無ければDBに問い合わせます。
	 */
	boolean hasClusteredColumnStore(final Connection connection, final Table table) {
		if (table.getIndexes().stream().anyMatch(index -> index.getIndexType() == IndexType.ClusteredColumnStore)) {
			return true;
		}
		final String name = dialect.getObjectFullName(quote(table.getCatalogName()), quote(table.getSchemaName()),
				quote(table.getName()));
		try (PreparedStatement statement = connection
				.prepareStatement("SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(?) AND type = 5")) {
			statement.setString(1, name);
			try (ResultSet rs = statement.executeQuery()) {
				return rs.next();
			}
		} catch (final SQLException e) {
			// 警告のための確認なので、確認できなくてもバルク投入は続行する
			logger.debug("Could not check clustered columnstore index for " + name, e);
			return false;
		}
	}
}
