/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.bulk;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertExecutor;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.vertica.jdbc.VerticaConnection;
import com.vertica.jdbc.VerticaCopyStream;

/** Vertica bulk insert backed by VerticaCopyStream. */
public class VerticaBulkInsertExecutor implements BulkInsertExecutor {
	@Override
	public boolean supportsCallerTransactionAtomicity() {
		return true;
	}

	private final Dialect dialect;

	public VerticaBulkInsertExecutor(final Dialect dialect) {
		this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
	}

	@Override
	public long execute(final Connection connection, final Table table, final BulkOption options) throws SQLException {
		java.util.Objects.requireNonNull(connection, "connection");
		java.util.Objects.requireNonNull(table, "table");
		validateOptions(options);
		try (var transaction = com.sqlapp.jdbc.bulk.BulkUpsertTransaction.begin(connection,true)) {
			try {
				long count=copy(connection,table,options);
				transaction.commit();
				return count;
			} catch(SQLException | RuntimeException | Error failure) {
				transaction.rollback(failure);
				throw failure;
			}
		}
	}

	private long copy(Connection connection,Table table,BulkOption options) throws SQLException {
		boolean json=table.getColumns().stream().anyMatch(column->VerticaBulkDataInputStream.writable(column,options) && column.getArrayDimension()>0);
		try (VerticaBulkDataInputStream input=json?new VerticaJsonBulkDataInputStream(table,options):new VerticaBulkDataInputStream(table,options)) {
			final StringBuilder sql=new StringBuilder("COPY ").append(dialect.getObjectFullName(table.getSchemaName(),table.getName())).append(" (");
			for(int i=0;i<input.getColumns().size();i++) {
				if(i>0)sql.append(", ");
				var column=input.getColumns().get(i);
				String name=dialect.quote(column.getName());
				String incoming=dialect.quote(input.inputName(column));
				if(json && VerticaBulkDataInputStream.binary(column)) {
					String first=dialect.quote(input.binaryInputName(column,0)),second=dialect.quote(input.binaryInputName(column,1));
					sql.append(first).append(" FILLER VARCHAR(65000),").append(second).append(" FILLER VARCHAR(65000),")
						.append(name).append(" AS (HEX_TO_BINARY(").append(first).append(") || HEX_TO_BINARY(").append(second).append("))");
				} else if(VerticaBulkDataInputStream.transformedInterval(column)) {
					sql.append(incoming).append(" FILLER ").append("ARRAY[".repeat(column.getArrayDimension())).append("VARCHAR(128)")
							.append("]".repeat(column.getArrayDimension()));
					if(column.getArrayDimension()>0)sql.append("(32000000)");
					sql.append(",").append(name).append(" AS CAST(").append(incoming).append(" AS ")
							.append(new com.sqlapp.data.db.dialect.vertica.VerticaIntervalJdbcTypeHandler(column.getDataType()).arrayType(column)).append(")");
				} else sql.append(name);
			}
			sql.append(") FROM STDIN ");
			if(json)sql.append("PARSER FJSONPARSER(reject_on_materialized_type_error=true,reject_on_duplicate=true)");
			else sql.append("DELIMITER E'\\037' RECORD TERMINATOR E'\\036' NULL AS E'\\035' ESCAPE AS E'\\\\'");
			sql.append(" ABORT ON ERROR NO COMMIT");
			var stream=new VerticaCopyStream(connection.unwrap(VerticaConnection.class),sql.toString(),input);
			stream.start();long count=stream.finish();
			if(count!=input.getRowCount() || stream.getRejects().stream().anyMatch(rejected->rejected!=0))
				throw new SQLException("Vertica COPY did not accept every supplied row");
			return count;
		} catch(IOException failure) {throw new SQLException("Failed to stream Vertica COPY rows for "+table.getName(),failure);}
	}

	private void validateOptions(final BulkOption options) {
		if (options != null && (options.getBatchSize() != null || options.getBulkCopyTimeout() != null
				|| options.isAllowEncryptedValueModifications() || options.isFireTriggers() || options.isTableLock()
				|| options.isUseTransaction())) {
			throw new IllegalArgumentException(
					"Vertica COPY does not support the requested SQL Server-style bulk options");
		}
	}
}
