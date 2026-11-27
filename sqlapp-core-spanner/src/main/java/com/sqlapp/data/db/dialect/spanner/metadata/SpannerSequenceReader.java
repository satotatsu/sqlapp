/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerCreateSequenceFactory;
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Cloud Spanner sequence metadata reader.
 */
public class SpannerSequenceReader extends SequenceReader {

	protected SpannerSequenceReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Sequence> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNode(productVersionInfo);
		final List<Sequence> result = list();
		final Set<List<String>> identities = new HashSet<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Sequence sequence = createSequence(rs);
				if (!identities
						.add(Arrays.asList(sequence.getCatalogName(), sequence.getSchemaName(), sequence.getName())))
					throw new IllegalArgumentException("Duplicate Spanner sequence metadata: " + sequence.getName());
				result.add(sequence);
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("sequences.sql");
	}

	protected Sequence createSequence(final ExResultSet rs) throws SQLException {
		final Sequence sequence = new Sequence(getString(rs, "NAME"));
		sequence.setDialect(getDialect());
		sequence.setCatalogName(getString(rs, "CATALOG"));
		sequence.setSchemaName(getString(rs, "SCHEMA"));
		if (sequence.getName() == null || sequence.getName().isBlank())
			throw new IllegalArgumentException("Spanner sequence metadata requires a name");
		sequence.setDataTypeName(getString(rs, "DATA_TYPE"));
		final String kind = getString(rs, "SEQUENCE_KIND");
		if (kind != null) {
			// OPTION_VALUE can be a SQL string literal; the emulator exposes the raw name.
			if (!"bit_reversed_positive".equalsIgnoreCase(kind) && !"'bit_reversed_positive'".equalsIgnoreCase(kind)
					&& !"\"bit_reversed_positive\"".equalsIgnoreCase(kind))
				throw new IllegalArgumentException(
						"Unknown Spanner sequence kind for " + sequence.getName() + ": " + kind);
			sequence.getSpecifics().put("SEQUENCE_KIND", "bit_reversed_positive");
		}
		final Long start = getLong(rs, "START_WITH_COUNTER");
		if (start != null) {
			if (start <= 0)
				throw new IllegalArgumentException(
						"Spanner sequence start counter must be positive: " + sequence.getName());
			sequence.setStartValue(start);
		}
		final Long skipMin = getLong(rs, "SKIP_RANGE_MIN");
		final Long skipMax = getLong(rs, "SKIP_RANGE_MAX");
		if ((skipMin == null) != (skipMax == null))
			throw new IllegalArgumentException(
					"Spanner sequence skip range requires both bounds: " + sequence.getName());
		if (skipMin != null && skipMin > skipMax)
			throw new IllegalArgumentException(
					"Spanner sequence skip range minimum exceeds maximum: " + sequence.getName());
		if (skipMin != null) {
			sequence.getSpecifics().put(SpannerCreateSequenceFactory.SKIP_RANGE_MIN, skipMin);
		}
		if (skipMax != null) {
			sequence.getSpecifics().put(SpannerCreateSequenceFactory.SKIP_RANGE_MAX, skipMax);
		}
		return sequence;
	}
}
