/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.metadata;

import java.sql.SQLException;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import com.sqlapp.data.parameter.ParametersContext;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.information_schema.metadata.AbstractISViewReader;
import com.sqlapp.data.db.dialect.spanner.sql.SpannerCreateViewFactory;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.View;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Cloud Spanner view metadata reader.
 */
public class SpannerViewReader extends AbstractISViewReader {

	protected SpannerViewReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		final List<Table> views = super.doGetAll(connection, context, version);
		final Set<List<String>> identities = new HashSet<>();
		for (final Table view : views)
			if (!identities.add(Arrays.asList(view.getCatalogName(), view.getSchemaName(), view.getName())))
				throw new IllegalArgumentException("Duplicate Spanner view metadata: " + view.getName());
		return views;
	}

	@Override
	protected Table createTable(final ExResultSet rs) throws SQLException {
		final View view = new View(getString(rs, TABLE_NAME));
		view.setCatalogName(getString(rs, TABLE_CATALOG));
		view.setSchemaName(getString(rs, TABLE_SCHEMA));
		final String statement = rs.getString("view_definition");
		if (view.getName() == null || view.getName().isBlank() || statement == null || statement.isBlank())
			throw new IllegalArgumentException("Spanner view metadata requires a name and query statement");
		view.setStatement(statement);
		final String security = getString(rs, "security_type");
		if (!"INVOKER".equalsIgnoreCase(security) && !"DEFINER".equalsIgnoreCase(security))
			throw new IllegalArgumentException(
					"Unknown Spanner view security type for " + view.getName() + ": " + security);
		view.getSpecifics().put(SpannerCreateViewFactory.SECURITY_TYPE, security);
		view.setDialect(getDialect());
		return view;
	}

	@Override
	protected SqlNode getSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("views.sql");
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new SpannerColumnReader(getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return null;
	}

	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}
}
