/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.cockroach.util.CockroachSequenceOptions;
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

public class CockroachSequenceReader extends SequenceReader {
	public CockroachSequenceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Sequence> doGetAll(Connection c, ParametersContext context, ProductVersionInfo version) {
		var result = new ArrayList<Sequence>();
		try {
			var definitions = CockroachMetadata.definitions(c, "SEQUENCE", context);
			try (var sql = c.createStatement();
					var rows = sql.executeQuery(
							"SELECT sequence_schema,sequence_name,start_value,minimum_value,maximum_value,increment,cycle_option FROM information_schema.sequences WHERE sequence_catalog=current_database() ORDER BY sequence_schema,sequence_name")) {
				while (rows.next()) {
					String schema = rows.getString(1), name = rows.getString(2);
					if (!CockroachMetadata.userSchema(schema)
							|| !CockroachMetadata.matches(context, "schemaName", schema)
							|| !CockroachMetadata.matches(context, "sequenceName", name))
						continue;
					var object = new Sequence(name).setSchemaName(schema)
							.setStartValue(new java.math.BigInteger(rows.getString(3)))
							.setMinValue(new java.math.BigInteger(rows.getString(4)))
							.setMaxValue(new java.math.BigInteger(rows.getString(5)))
							.setIncrementBy(new java.math.BigInteger(rows.getString(6)))
							.setCycle("YES".equals(rows.getString(7)));
					String definition = CockroachMetadata.definition(definitions, schema, name);
					object.setDefinition(definition).setCacheSize(CockroachSequenceOptions.cache(definition).size());
					result.add(object);
				}
			}
			readNextValues(c, result);
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot read CockroachDB Sequence metadata", e);
		}
		return result;
	}

	/**
	 * Selecting from a sequence returns last_value without advancing it. The stored
	 * value is already ahead of values cached by sessions, which the model's
	 * lastValue (an upper bound of the next value) allows. Sequences the reader may
	 * not select from keep an unknown next value.
	 */
	private void readNextValues(Connection c, List<Sequence> sequences) throws SQLException {
		for (int offset = 0; offset < sequences.size(); offset += 100) {
			var batch = sequences.subList(offset, Math.min(offset + 100, sequences.size()));
			var parts = new ArrayList<String>();
			for (int i = 0; i < batch.size(); i++) {
				Sequence sequence = batch.get(i);
				parts.add("SELECT " + i + " AS position,last_value,is_called FROM "
						+ getDialect().quote(sequence.getSchemaName()) + "." + getDialect().quote(sequence.getName()));
			}
			try (var sql = c.createStatement(); var rows = sql.executeQuery(String.join(" UNION ALL ", parts))) {
				while (rows.next()) {
					Sequence sequence = batch.get(rows.getInt("position"));
					var last = rows.getBigDecimal("last_value");
					var next = rows.getBoolean("is_called") ? com.sqlapp.data.db.metadata.SequenceNextValues.after(last,
							new java.math.BigDecimal(sequence.getIncrementBy()),
							new java.math.BigDecimal(sequence.getMinValue()),
							new java.math.BigDecimal(sequence.getMaxValue()), sequence.isCycle()) : last;
					sequence.setLastValue(next == null ? null : next.toBigIntegerExact());
				}
			} catch (SQLException e) {
				// Leave the batch's next values unknown, unless the failure aborted the
				// caller's transaction.
				if (!c.getAutoCommit())
					throw e;
			}
		}
	}

}
