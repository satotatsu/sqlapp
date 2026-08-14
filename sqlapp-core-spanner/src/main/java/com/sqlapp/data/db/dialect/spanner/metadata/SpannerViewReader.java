/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.metadata;

import java.sql.SQLException;

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
	protected Table createTable(final ExResultSet rs) throws SQLException {
		final View view = new View(getString(rs, TABLE_NAME));
		view.setCatalogName(getString(rs, TABLE_CATALOG));
		view.setSchemaName(getString(rs, TABLE_SCHEMA));
		view.setStatement(getString(rs, "view_definition"));
		view.getSpecifics().put(SpannerCreateViewFactory.SECURITY_TYPE,
				getString(rs, "security_type"));
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
