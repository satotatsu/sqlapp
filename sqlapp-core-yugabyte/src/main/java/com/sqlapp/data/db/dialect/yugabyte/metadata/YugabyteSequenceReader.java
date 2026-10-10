/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.postgres.metadata.PostgresSequenceReader;
import com.sqlapp.data.schemas.Sequence;

/**
 * Reads configuration from pg_sequence on both supported YSQL engine majors.
 */
public class YugabyteSequenceReader extends PostgresSequenceReader {
	public YugabyteSequenceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void setMetadataDetail(Connection connection, Sequence sequence) throws SQLException {
		String sql = "SELECT pg_catalog.format_type(s.seqtypid, NULL) AS sequence_type, s.seqstart, s.seqincrement, s.seqmin, s.seqmax, s.seqcache, s.seqcycle, "
				+ "pg_catalog.obj_description(c.oid, 'pg_class') AS remarks "
				+ "FROM pg_catalog.pg_sequence s JOIN pg_catalog.pg_class c ON c.oid = s.seqrelid "
				+ "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ? AND c.relname = ?";
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, sequence.getSchemaName());
			statement.setString(2, sequence.getName());
			try (var rows = statement.executeQuery()) {
				if (!rows.next())
					throw new SQLException(
							"Sequence metadata missing: " + sequence.getSchemaName() + "." + sequence.getName());
				String typeName = rows.getString("sequence_type");
				sequence.setDataType(switch (typeName) {
				case "smallint" -> DataType.SMALLINT;
				case "integer" -> DataType.INT;
				case "bigint" -> DataType.BIGINT;
				default -> null;
				});
				if (sequence.getDataType() == null)
					sequence.setDataTypeName(typeName);
				sequence.setStartValue(rows.getBigDecimal("seqstart"));
				sequence.setIncrementBy(rows.getBigDecimal("seqincrement"));
				sequence.setMinValue(rows.getBigDecimal("seqmin"));
				sequence.setMaxValue(rows.getBigDecimal("seqmax"));
				sequence.setCacheSize(rows.getBigDecimal("seqcache"));
				sequence.setCycle(rows.getBoolean("seqcycle"));
				sequence.setRemarks(rows.getString("remarks"));
			}
		}
	}
}
