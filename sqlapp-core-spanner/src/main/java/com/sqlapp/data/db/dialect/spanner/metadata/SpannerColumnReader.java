/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-spanner.
 *
 * sqlapp-core-spanner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-spanner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-spanner.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.spanner.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.ProductVersionInfo;

/**
 * Spanner Column Reader
 * 
 * @author satoh
 * 
 */
public class SpannerColumnReader extends ColumnReader {

	public SpannerColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Column> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Column obj = createColumn(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected Column createColumn(ExResultSet rs) throws SQLException {
		Column obj = new Column(getString(rs, COLUMN_NAME));
		obj.setCatalogName(getString(rs, TABLE_CATALOG));
		obj.setSchemaName(getString(rs, TABLE_SCHEMA));
		obj.setTableName(getString(rs, TABLE_NAME));
		boolean nullable = toBoolean(getString(rs, "is_nullable"));
		String data_type = getString(rs, "spanner_type");
		obj.setNullable(nullable);
		this.getDialect().setDbType(data_type,
				null, null, obj);
		obj.setDefaultValue(getString(rs, "column_default"));
		obj.setOnUpdate(getString(rs, "on_update_expression"));
		if ("ALWAYS".equalsIgnoreCase(
				getString(rs, "is_generated"))) {
			obj.setFormula(getString(rs, "generation_expression"));
			obj.setFormulaPersisted("YES".equalsIgnoreCase(
					getString(rs, "is_stored")));
		}
		obj.setHidden("TRUE".equalsIgnoreCase(
				getString(rs, "is_hidden")));
		setSpecifics(rs, "allow_commit_timestamp", obj);
		setSpecifics(rs, "vector_length", obj);
		setSpecifics(rs, "locality_group", obj);
		if ("YES".equalsIgnoreCase(getString(rs, "is_identity"))) {
			obj.setIdentity(true);
			obj.setIdentityGenerationType(IdentityGenerationType.parse(
					getString(rs, "identity_generation")));
			final Long start = getLong(rs, "identity_start_with_counter");
			if (start != null) {
				obj.setIdentityStartValue(start);
			}
			if (getString(rs, "identity_kind") != null) {
				obj.getSpecifics().put(
						SpannerSqlBuilder.IDENTITY_BIT_REVERSED_POSITIVE,
						true);
			}
			final Long skipMin = getLong(rs, "identity_skip_range_min");
			final Long skipMax = getLong(rs, "identity_skip_range_max");
			if (skipMin != null) {
				obj.getSpecifics().put(
						SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MIN,
						skipMin);
			}
			if (skipMax != null) {
				obj.getSpecifics().put(
						SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MAX,
						skipMax);
			}
		}
		return obj;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns.sql");
	}
}
