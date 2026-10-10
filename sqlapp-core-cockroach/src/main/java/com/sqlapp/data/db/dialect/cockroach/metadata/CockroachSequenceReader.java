/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
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
		try (var sql = c.createStatement();
				var rows = sql.executeQuery(
						"SELECT sequence_schema,sequence_name,start_value,minimum_value,maximum_value,increment,cycle_option FROM information_schema.sequences WHERE sequence_catalog=current_database() ORDER BY sequence_schema,sequence_name")) {
			while (rows.next()) {
				String schema = rows.getString(1), name = rows.getString(2);
				if (!CockroachMetadata.userSchema(schema) || !CockroachMetadata.matches(context, "schemaName", schema)
						|| !CockroachMetadata.matches(context, "sequenceName", name))
					continue;
				var object = new Sequence(name).setSchemaName(schema)
						.setStartValue(new java.math.BigInteger(rows.getString(3)))
						.setMinValue(new java.math.BigInteger(rows.getString(4)))
						.setMaxValue(new java.math.BigInteger(rows.getString(5)))
						.setIncrementBy(new java.math.BigInteger(rows.getString(6)))
						.setCycle("YES".equals(rows.getString(7)));
				object.setDefinition(CockroachMetadata.definition(c, "SEQUENCE", schema, name));
				result.add(object);
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot read CockroachDB Sequence metadata", e);
		}
		return result;
	}

}
