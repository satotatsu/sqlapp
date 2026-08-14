/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.metadata;

import static com.sqlapp.util.CommonUtils.doubleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.DimensionReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Dimension;
import com.sqlapp.data.schemas.DimensionAttribute;
import com.sqlapp.data.schemas.DimensionAttributeColumn;
import com.sqlapp.data.schemas.DimensionHierarchy;
import com.sqlapp.data.schemas.DimensionHierarchyLevel;
import com.sqlapp.data.schemas.DimensionLevel;
import com.sqlapp.data.schemas.DimensionLevelColumn;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.DoubleKeyMap;

/**
 * OracleのDimension読み込み
 * 
 * @author satoh
 * 
 */
public class OracleDimensionReader extends DimensionReader {

	protected OracleDimensionReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Dimension> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final DoubleKeyMap<String, String, Dimension> dimensions = doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Dimension obj = createDimension(rs);
				dimensions.put(obj.getSchemaName(), obj.getName(), obj);
			}
		});
		setLevels(connection, context, dimensions);
		setHierarchies(connection, context, dimensions);
		setAttributes(connection, context, dimensions);
		return dimensions.toList();
	}

	private void setLevels(final Connection connection,
			final ParametersContext context,
			final DoubleKeyMap<String, String, Dimension> dimensions) {
		execute(connection, getSqlNodeCache().getString("dimensionLevels.sql"),
				context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Dimension dimension = dimensions.get(getString(rs, "OWNER"),
						getString(rs, DIMENSION_NAME));
				if (dimension == null) {
					return;
				}
				String levelName = getString(rs, "LEVEL_NAME");
				DimensionLevel level = dimension.getLevels().get(levelName);
				if (level == null) {
					level = new DimensionLevel(levelName);
					level.setSkipWhenNull("Y".equalsIgnoreCase(
							getString(rs, "SKIP_WHEN_NULL")));
					dimension.getLevels().add(level);
				}
				DimensionLevelColumn levelColumn = new DimensionLevelColumn(
						getString(rs, "COLUMN_NAME"));
				level.getColumns().add(levelColumn);
				Column column = levelColumn.getColumn();
				column.setSchemaName(getString(rs, "DETAILOBJ_OWNER"));
				column.setTableName(getString(rs, "DETAILOBJ_NAME"));
			}
		});
	}

	private void setHierarchies(final Connection connection,
			final ParametersContext context,
			final DoubleKeyMap<String, String, Dimension> dimensions) {
		execute(connection, getSqlNodeCache().getString("dimensionHierarchies.sql"),
				context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Dimension dimension = dimensions.get(getString(rs, "OWNER"),
						getString(rs, DIMENSION_NAME));
				if (dimension == null) {
					return;
				}
				String hierarchyName = getString(rs, "HIERARCHY_NAME");
				DimensionHierarchy hierarchy = dimension.getHierarchies()
						.get(hierarchyName);
				if (hierarchy == null) {
					hierarchy = new DimensionHierarchy(hierarchyName);
					dimension.getHierarchies().add(hierarchy);
				}
				addHierarchyLevel(hierarchy, getString(rs, "CHILD_LEVEL_NAME"));
				addHierarchyLevel(hierarchy, getString(rs, "PARENT_LEVEL_NAME"));
			}
		});
	}

	private void addHierarchyLevel(final DimensionHierarchy hierarchy,
			final String levelName) {
		if (levelName != null && hierarchy.getLevels().get(levelName) == null) {
			hierarchy.getLevels().add(new DimensionHierarchyLevel(levelName));
		}
	}

	private void setAttributes(final Connection connection,
			final ParametersContext context,
			final DoubleKeyMap<String, String, Dimension> dimensions) {
		execute(connection, getSqlNodeCache().getString("dimensionAttributes.sql"),
				context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Dimension dimension = dimensions.get(getString(rs, "OWNER"),
						getString(rs, DIMENSION_NAME));
				if (dimension == null) {
					return;
				}
				String attributeName = getString(rs, "ATTRIBUTE_NAME");
				DimensionAttribute attribute = dimension.getAttributes()
						.get(attributeName);
				if (attribute == null) {
					attribute = new DimensionAttribute(attributeName);
					dimension.getAttributes().add(attribute);
				}
				DimensionAttributeColumn attributeColumn =
						new DimensionAttributeColumn(getString(rs, "COLUMN_NAME"));
				attribute.getColumns().add(attributeColumn);
				Column column = attributeColumn.getColumn();
				column.setSchemaName(getString(rs, "DETAILOBJ_OWNER"));
				column.setTableName(getString(rs, "DETAILOBJ_NAME"));
			}
		});
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("dimensions.sql");
	}

	protected Dimension createDimension(ExResultSet rs) throws SQLException {
		Dimension obj = new Dimension(getString(rs, DIMENSION_NAME));
		obj.setSchemaName(getString(rs, "OWNER"));
		setSpecifics(rs, "COMPILE_STATE", obj);
		setSpecifics(rs, "INVALID", obj);
		setSpecifics(rs, "REVISION", obj);
		return obj;
	}
}
