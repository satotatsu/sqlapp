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
	protected List<Sequence> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNode(productVersionInfo);
		final List<Sequence> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs)
					throws SQLException {
				result.add(createSequence(rs));
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(
			final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("sequences.sql");
	}

	protected Sequence createSequence(final ExResultSet rs)
			throws SQLException {
		final Sequence sequence = new Sequence(getString(rs, "NAME"));
		sequence.setDialect(getDialect());
		sequence.setCatalogName(getString(rs, "CATALOG"));
		sequence.setSchemaName(getString(rs, "SCHEMA"));
		sequence.setDataTypeName(getString(rs, "DATA_TYPE"));
		final Long start = getLong(rs, "START_WITH_COUNTER");
		if (start != null) {
			sequence.setStartValue(start);
		}
		final Long skipMin = getLong(rs, "SKIP_RANGE_MIN");
		final Long skipMax = getLong(rs, "SKIP_RANGE_MAX");
		if (skipMin != null) {
			sequence.getSpecifics().put(
					SpannerCreateSequenceFactory.SKIP_RANGE_MIN,
					skipMin);
		}
		if (skipMax != null) {
			sequence.getSpecifics().put(
					SpannerCreateSequenceFactory.SKIP_RANGE_MAX,
					skipMax);
		}
		return sequence;
	}
}
