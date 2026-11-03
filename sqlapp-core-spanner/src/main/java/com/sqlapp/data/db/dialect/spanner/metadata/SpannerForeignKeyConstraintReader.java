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
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnPair;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.FlexList;
import com.sqlapp.util.TripleKeyMap;

/**
 * Spanner Foreign Key Constraint Reader
 * 
 * @author satoh
 * 
 */
public class SpannerForeignKeyConstraintReader extends ForeignKeyConstraintReader {

	public SpannerForeignKeyConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ForeignKeyConstraint> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<ForeignKeyConstraint> list = list();
		final TripleKeyMap<String, String, String, ForeignKeyConstraint> tCMap = tripleKeyMap();
		final TripleKeyMap<String, String, String, FlexList<ColumnPair>> tColMap = tripleKeyMap();
		final TripleKeyMap<String, String, String, java.util.SortedMap<Long, ColumnPair>> positions = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String fk_catalog = getString(rs, "constraint_catalog");
				String fk_schema = getString(rs, "constraint_schema");
				String fk_name = getString(rs, CONSTRAINT_NAME);
				String columnName = getString(rs, COLUMN_NAME);
				String referenced_table_catalog = getString(rs, "referenced_table_catalog");
				String referenced_table_schema = getString(rs, "referenced_table_schema");
				String referenced_table_name = getString(rs, "referenced_table_name");
				String referenced_column_name = getString(rs, "referenced_column_name");
				ForeignKeyConstraint c = tCMap.get(fk_catalog, fk_schema, fk_name);
				var colList = positions.get(fk_catalog, fk_schema, fk_name);
				final ForeignKeyConstraint header = createConstraint(rs);
				if (c == null) {
					c = header;
					colList = new java.util.TreeMap<>();
					tCMap.put(fk_catalog, fk_schema, fk_name, c);
					positions.put(fk_catalog, fk_schema, fk_name, colList);
					list.add(c);
				} else if (!java.util.Objects.equals(c.getTableName(), header.getTableName())
						|| c.isEnable() != header.isEnable() || c.getDeleteRule() != header.getDeleteRule()
						|| !java.util.Objects.equals(c.getSpecifics().get("spanner_state"),
								header.getSpecifics().get("spanner_state"))) {
					throw new IllegalArgumentException("Inconsistent Cloud Spanner foreign-key header: " + fk_name);
				}
				ColumnPair cPair = new ColumnPair();
				cPair.refCatalogName = referenced_table_catalog;
				cPair.refSchemaName = referenced_table_schema;
				cPair.refTableName = referenced_table_name;
				cPair.refColumnName = referenced_column_name;
				cPair.columnName = columnName;
				final Long ordinal = getLong(rs, "ordinal_position");
				if (ordinal == null || ordinal <= 0 || ordinal > Integer.MAX_VALUE || columnName == null
						|| columnName.isBlank() || referenced_column_name == null || referenced_column_name.isBlank()
						|| referenced_table_name == null || referenced_table_name.isBlank()
						|| colList.putIfAbsent(ordinal, cPair) != null) {
					throw new IllegalArgumentException(
							"Invalid Cloud Spanner foreign-key column position/identity: " + fk_name);
				}
			}
		});
		for (final var constraint : list) {
			final var sorted = positions.get(constraint.getCatalogName(), constraint.getSchemaName(),
					constraint.getName());
			final var pairs = new FlexList<ColumnPair>();
			final var localNames = new java.util.HashSet<String>();
			final var relatedNames = new java.util.HashSet<String>();
			long expected = 1;
			ColumnPair first = null;
			for (final var entry : sorted.entrySet()) {
				final var pair = entry.getValue();
				if (entry.getKey() != expected++ || !localNames.add(pair.columnName.toUpperCase(java.util.Locale.ROOT))
						|| !relatedNames.add(pair.refColumnName.toUpperCase(java.util.Locale.ROOT))
						|| (first != null && (!java.util.Objects.equals(first.refCatalogName, pair.refCatalogName)
								|| !java.util.Objects.equals(first.refSchemaName, pair.refSchemaName)
								|| !java.util.Objects.equals(first.refTableName, pair.refTableName)))) {
					throw new IllegalArgumentException(
							"Incomplete/inconsistent Cloud Spanner foreign-key columns: " + constraint.getName());
				}
				first = pair;
				pairs.add(pair);
			}
			tColMap.put(constraint.getCatalogName(), constraint.getSchemaName(), constraint.getName(), pairs);
		}
		setForeignKeyConstraintColumns(tColMap, list);
		return list;
	}

	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("foreignKeyConstraints.sql");
	}

	protected ForeignKeyConstraint createConstraint(ExResultSet rs) throws SQLException {
		final ForeignKeyConstraint c = new ForeignKeyConstraint(getString(rs, CONSTRAINT_NAME));
		if (c.getName() == null || c.getName().isBlank() || getString(rs, TABLE_NAME) == null
				|| getString(rs, TABLE_NAME).isBlank())
			throw new IllegalArgumentException("Cloud Spanner foreign key requires constraint/table names");
		c.setCatalogName(getString(rs, "constraint_catalog"));
		c.setSchemaName(getString(rs, "constraint_schema"));
		c.setTableName(getString(rs, TABLE_NAME));
		if (!"SIMPLE".equalsIgnoreCase(getString(rs, MATCH_OPTION))
				|| !"NO ACTION".equalsIgnoreCase(getString(rs, UPDATE_RULE)))
			throw new UnsupportedOperationException(
					"Unrepresentable Cloud Spanner foreign-key match/update metadata: " + c.getName());
		c.setMatchOption(ForeignKeyConstraint.MatchOption.Simple);
		c.setUpdateRule(CascadeRule.None);
		final String delete = getString(rs, DELETE_RULE);
		if ("NO ACTION".equalsIgnoreCase(delete))
			c.setDeleteRule(CascadeRule.None);
		else if ("CASCADE".equalsIgnoreCase(delete))
			c.setDeleteRule(CascadeRule.Cascade);
		else if ("SET NULL".equalsIgnoreCase(delete))
			c.setDeleteRule(CascadeRule.SetNull);
		else
			throw new UnsupportedOperationException(
					"Unrepresentable Cloud Spanner foreign-key delete metadata: " + delete);
		final String enforced = getString(rs, "enforced");
		if (!"YES".equalsIgnoreCase(enforced) && !"NO".equalsIgnoreCase(enforced))
			throw new IllegalArgumentException("Unknown Cloud Spanner foreign-key ENFORCED metadata: " + enforced);
		c.setEnable("YES".equalsIgnoreCase(enforced));
		setSpecifics(rs, "spanner_state", c);
		return c;
	}
}
