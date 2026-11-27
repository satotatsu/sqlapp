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
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Reads Dr.Sum tables from {@code __all_tables__}, which has one row per
 * column. Rows with a {@code view_select} are views; rows with a
 * {@code view_connect} are multiviews, which this dialect does not model and
 * therefore skips. A row without {@code column_name} is taken as the
 * table-level row and supplies the table comment. The raw {@code assortment}
 * value is kept in the {@value #ASSORTMENT} specific.
 */
public class DrSumTableReader extends TableReader {

	/** Specifics key holding the raw {@code __all_tables__.assortment} value. */
	public static final String ASSORTMENT = "drsum.assortment";

	protected DrSumTableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final Map<String, Table> tables = new LinkedHashMap<>();
		execute(connection, getSqlNodeCache().getString("tables.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String name = getString(rs, "table_name");
				if (getReaderOptions().isExcludeSystemObjects() && DrSumMetadataSupport.isSystemTable(name)) {
					return;
				}
				if (!DrSumMetadataSupport.isEmpty(getString(rs, "view_connect"))) {
					return;
				}
				final Table table = tables.computeIfAbsent(name, n -> createTable(n));
				final String assortment = getString(rs, "assortment");
				if (assortment != null) {
					table.getSpecifics().put(ASSORTMENT, assortment);
				}
				if (DrSumMetadataSupport.isEmpty(getString(rs, "column_name"))) {
					table.setRemarks(getString(rs, "comments"));
				}
			}
		});
		return list(tables.values());
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new DrSumColumnReader(getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new DrSumIndexReader(getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new DrSumUniqueConstraintReader(getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return null;
	}

	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return null;
	}

	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}
}
