/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.drsum.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.ViewReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.util.CommonUtils;

/**
 * Reads Dr.Sum views from {@code __all_tables__.view_select}. When the stored
 * text is a complete {@code CREATE VIEW} statement it is kept as the
 * definition; otherwise it is kept as the view's SELECT statement. Different
 * texts for one view are rejected rather than merged.
 */
public class DrSumViewReader extends ViewReader {

	protected DrSumViewReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final Map<String, Table> views = new LinkedHashMap<>();
		final Map<String, String> texts = new LinkedHashMap<>();
		execute(connection, getSqlNodeCache().getString("views.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String name = getString(rs, "table_name");
				final Table view = views.computeIfAbsent(name, n -> createTable(n));
				final String assortment = getString(rs, "assortment");
				if (assortment != null) {
					view.getSpecifics().put(DrSumTableReader.ASSORTMENT, assortment);
				}
				if (DrSumMetadataSupport.isEmpty(getString(rs, "column_name"))) {
					view.setRemarks(getString(rs, "comments"));
				}
				final String text = getString(rs, "view_select");
				final String previous = texts.putIfAbsent(name, text);
				if (previous != null && !previous.equals(text)) {
					throw new IllegalStateException("Dr.Sum view has inconsistent view_select rows. view=" + name);
				}
			}
		});
		for (final Map.Entry<String, String> entry : texts.entrySet()) {
			final View view = (View) views.get(entry.getKey());
			final String text = entry.getValue().strip();
			if (text.toUpperCase(Locale.ROOT).startsWith("CREATE")) {
				view.setDefinition(text);
			} else {
				view.setStatement(CommonUtils.splitLine(text));
			}
		}
		return list(views.values());
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new DrSumColumnReader(getDialect());
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
