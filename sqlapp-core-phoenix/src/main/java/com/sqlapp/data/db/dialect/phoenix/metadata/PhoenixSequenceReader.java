/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Reads sequence configuration in bulk, without allocating sequence values. */
public class PhoenixSequenceReader extends SequenceReader {
	public PhoenixSequenceReader(Dialect dialect) {
		super(dialect);
	}

	static String sqlResource(ProductVersionInfo version) {
		if (version != null && version.getMajorVersion() != null) {
			final int major = version.getMajorVersion();
			final Integer minor = version.getMinorVersion();
			if (major >= 5 || ((major == 3 || major == 4) && minor != null && minor >= 1))
				return "sequences31.sql";
		}
		return "sequences.sql";
	}

	@Override
	protected List<Sequence> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		final List<Sequence> result = new ArrayList<>();
		final Set<List<String>> identities = new HashSet<>();
		execute(connection, getSqlNodeCache().getString(sqlResource(version)), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				final var sequence = createSequence(rs);
				if (!identities
						.add(Arrays.asList(sequence.getCatalogName(), sequence.getSchemaName(), sequence.getName())))
					throw new IllegalArgumentException("Duplicate Phoenix sequence metadata: " + sequence.getName());
				result.add(sequence);
			}
		});
		return result;
	}

	protected Sequence createSequence(ExResultSet rs) throws SQLException {
		final var sequence = new Sequence(getString(rs, "sequence_name"));
		sequence.setCatalogName(getString(rs, "catalog_name"));
		sequence.setSchemaName(getString(rs, "schema_name"));
		if (sequence.getName() == null || sequence.getName().isBlank())
			throw new IllegalArgumentException("Phoenix sequence metadata requires a name");
		sequence.setDialect(getDialect());
		sequence.setDataType(DataType.BIGINT);
		final Long start = rs.getLongValue("start_with");
		final Long increment = rs.getLongValue("increment_by");
		final Long minimum = rs.getLongValue("min_value");
		final Long maximum = rs.getLongValue("max_value");
		final Long cache = rs.getLongValue("cache_size");
		if (increment != null && increment == 0)
			throw new IllegalArgumentException("Phoenix sequence increment must not be zero: " + sequence.getName());
		if (cache != null && cache < 0)
			throw new IllegalArgumentException("Phoenix sequence cache must not be negative: " + sequence.getName());
		if (minimum != null && maximum != null && minimum > maximum)
			throw new IllegalArgumentException("Phoenix sequence minimum exceeds maximum: " + sequence.getName());
		if (start != null && ((minimum != null && start < minimum) || (maximum != null && start > maximum)))
			throw new IllegalArgumentException("Phoenix sequence start is outside its bounds: " + sequence.getName());
		if (start != null)
			sequence.setStartValue(start);
		if (increment != null)
			sequence.setIncrementBy(increment);
		if (minimum != null)
			sequence.setMinValue(minimum);
		if (maximum != null)
			sequence.setMaxValue(maximum);
		if (cache != null)
			sequence.setCacheSize(Math.toIntExact(cache));
		final Boolean cycle = rs.getBooleanValue("cycle_flag");
		if (cycle != null)
			sequence.setCycle(cycle);
		return sequence;
	}
}
