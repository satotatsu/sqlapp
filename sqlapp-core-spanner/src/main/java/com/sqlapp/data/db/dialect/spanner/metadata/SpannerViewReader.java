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
import com.sqlapp.data.schemas.View;
import com.sqlapp.jdbc.ExResultSet;

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
		view.setStatement(getString(rs, "VIEW_DEFINITION"));
		view.getSpecifics().put(SpannerCreateViewFactory.SECURITY_TYPE,
				getString(rs, "SECURITY_TYPE"));
		return view;
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
