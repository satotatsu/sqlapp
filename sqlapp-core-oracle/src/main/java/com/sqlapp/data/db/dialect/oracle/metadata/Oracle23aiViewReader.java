/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.sql.OracleJsonDualityViewUtils;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Oracle Database 23ai view reader with JSON relational duality metadata.
 */
public class Oracle23aiViewReader extends OracleViewReader {

	protected Oracle23aiViewReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void setMetadataDetail(final Connection connection,
			final ParametersContext context, final List<Table> views)
			throws SQLException {
		super.setMetadataDetail(connection, context, views);
		if (views.isEmpty()) {
			return;
		}
		final SqlNode node = getSqlNodeCache().getString(
				"jsonRelationalDualityViews.sql");
		try {
			execute(connection, node, context, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(final ExResultSet rs)
						throws SQLException {
					final View view = findView(views,
							getString(rs, "VIEW_OWNER"),
							getString(rs, "VIEW_NAME"));
					if (view != null) {
						setDualityMetadata(view, rs);
					}
				}
			});
		} catch (final RuntimeException e) {
			if (!Oracle23aiIndexReader.isVectorIndexDetailUnavailable(e)) {
				throw e;
			}
			logger.warn("Oracle JSON relational duality view metadata is "
					+ "unavailable or not permitted; duality properties are "
					+ "skipped. " + e.getMessage());
		}
	}

	private View findView(final List<Table> views, final String owner,
			final String name) {
		for (Table table : views) {
			if (table instanceof View
					&& name.equalsIgnoreCase(table.getName())
					&& (owner == null || owner.equalsIgnoreCase(
							table.getSchemaName()))) {
				return (View) table;
			}
		}
		return null;
	}

	private void setDualityMetadata(final View view, final ExResultSet rs)
			throws SQLException {
		OracleJsonDualityViewUtils.setJsonRelationalDualityView(view, true);
		put(view, OracleJsonDualityViewUtils.JSON_COLUMN_NAME,
				getString(rs, "JSON_COLUMN_NAME"));
		put(view, OracleJsonDualityViewUtils.ROOT_TABLE_OWNER,
				getString(rs, "ROOT_TABLE_OWNER"));
		put(view, OracleJsonDualityViewUtils.ROOT_TABLE_NAME,
				getString(rs, "ROOT_TABLE_NAME"));
		view.getSpecifics().put(OracleJsonDualityViewUtils.ALLOW_INSERT,
				rs.getBoolean("ALLOW_INSERT"));
		view.getSpecifics().put(OracleJsonDualityViewUtils.ALLOW_UPDATE,
				rs.getBoolean("ALLOW_UPDATE"));
		view.getSpecifics().put(OracleJsonDualityViewUtils.ALLOW_DELETE,
				rs.getBoolean("ALLOW_DELETE"));
	}

	private void put(final View view, final String key, final String value) {
		if (value != null && !value.isBlank()) {
			view.getSpecifics().put(key, value);
		}
	}
}
