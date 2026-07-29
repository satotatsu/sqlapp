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
		boolean nullable = toBoolean(getString(rs, "IS_NULLABLE"));
		String data_type = getString(rs, "SPANNER_TYPE");
		obj.setNullable(nullable);
		this.getDialect().setDbType(data_type,
				null, null, obj);
		obj.setDefaultValue(getString(rs, "COLUMN_DEFAULT"));
		obj.setOnUpdate(getString(rs, "ON_UPDATE_EXPRESSION"));
		if ("ALWAYS".equalsIgnoreCase(
				getString(rs, "IS_GENERATED"))) {
			obj.setFormula(getString(rs, "GENERATION_EXPRESSION"));
			obj.setFormulaPersisted("YES".equalsIgnoreCase(
					getString(rs, "IS_STORED")));
		}
		obj.setHidden("TRUE".equalsIgnoreCase(
				getString(rs, "IS_HIDDEN")));
		setSpecifics(rs, "ALLOW_COMMIT_TIMESTAMP", obj);
		if ("YES".equalsIgnoreCase(getString(rs, "IS_IDENTITY"))) {
			obj.setIdentity(true);
			obj.setIdentityGenerationType(IdentityGenerationType.parse(
					getString(rs, "IDENTITY_GENERATION")));
			final Long start = getLong(rs, "IDENTITY_START_WITH_COUNTER");
			if (start != null) {
				obj.setIdentityStartValue(start);
			}
			if (getString(rs, "IDENTITY_KIND") != null) {
				obj.getSpecifics().put(
						SpannerSqlBuilder.IDENTITY_BIT_REVERSED_POSITIVE,
						true);
			}
			final Long skipMin = getLong(rs, "IDENTITY_SKIP_RANGE_MIN");
			final Long skipMax = getLong(rs, "IDENTITY_SKIP_RANGE_MAX");
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
