/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads 10.3–11.4 sequence storage tables in bounded batches, without advancing
 * them.
 */
class MariadbSequence10_30Reader extends SequenceReader {
	static final int BATCH_SIZE = 100;

	MariadbSequence10_30Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Sequence> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		List<Sequence> sequences = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("legacySequences.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Sequence sequence = new Sequence(getString(rs, "TABLE_NAME"));
				sequence.setCatalogName(getString(rs, "TABLE_CATALOG"));
				sequence.setSchemaName(getString(rs, "TABLE_SCHEMA"));
				sequence.setDataType(DataType.BIGINT);
				sequence.setRemarks(getString(rs, "TABLE_COMMENT"));
				String engine = getString(rs, "ENGINE");
				if (engine != null)
					sequence.getSpecifics().put("ENGINE", engine);
				sequences.add(sequence);
			}
		});
		for (int offset = 0; offset < sequences.size(); offset += BATCH_SIZE) {
			List<Sequence> batch = sequences.subList(offset, Math.min(offset + BATCH_SIZE, sequences.size()));
			boolean[] seen = new boolean[batch.size()];
			StringBuilder sql = new StringBuilder();
			for (int i = 0; i < batch.size(); i++) {
				if (i > 0)
					sql.append(" UNION ALL ");
				Sequence sequence = batch.get(i);
				// Catalog values are identifiers, never SQL expressions; quote each component
				// independently.
				sql.append("SELECT ").append(i).append(" AS sequence_id, minimum_value, maximum_value, start_value, ")
						.append("`increment`, cache_size, cycle_option FROM ").append(quote(sequence.getSchemaName()))
						.append('.').append(quote(sequence.getName()));
			}
			SqlNode storageQuery = new SqlNode(sql.toString());
			storageQuery.setParameters(java.util.Collections.emptySet());
			execute(connection, storageQuery, new ParametersContext(), new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(ExResultSet rs) throws SQLException {
					int id = rs.getInt("sequence_id");
					if (id < 0 || id >= batch.size()) {
						throw new IllegalStateException("Invalid MariaDB sequence batch row id: " + id);
					}
					if (seen[id])
						throw new IllegalStateException("Multiple rows in MariaDB sequence storage table");
					seen[id] = true;
					Sequence sequence = batch.get(id);
					sequence.setMinValue(rs.getBigDecimal("minimum_value").toBigIntegerExact());
					sequence.setMaxValue(rs.getBigDecimal("maximum_value").toBigIntegerExact());
					sequence.setStartValue(rs.getBigDecimal("start_value").toBigIntegerExact());
					sequence.setIncrementBy(rs.getBigDecimal("increment").toBigIntegerExact());
					int cache = rs.getBigDecimal("cache_size").intValueExact();
					if (cache < 0)
						throw new IllegalStateException("Negative MariaDB sequence cache size: " + sequence.getName());
					sequence.setCacheSize(cache);
					sequence.setCache(cache > 0);
					sequence.setCycle(rs.getInt("cycle_option") != 0);
				}
			});
			for (int i = 0; i < seen.length; i++) {
				if (!seen[i])
					throw new IllegalStateException("Missing MariaDB sequence storage row: " + batch.get(i).getName());
			}
		}
		return sequences;
	}

	static String quote(String identifier) {
		if (identifier == null || identifier.isEmpty()) {
			throw new IllegalArgumentException("MariaDB sequence requires schema and name");
		}
		return "`" + identifier.replace("`", "``") + "`";
	}
}
