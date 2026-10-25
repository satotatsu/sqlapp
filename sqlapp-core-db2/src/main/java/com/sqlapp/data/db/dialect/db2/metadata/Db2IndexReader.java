/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.TripleKeyMap;

/**
 * DB2のインデックス読み込みクラス
 *
 * @author satoh
 *
 */
public class Db2IndexReader extends IndexReader {

	public Db2IndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Index> result = list();
		final TripleKeyMap<String, String, String, Index> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				boolean modern = getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110;
				String nativeType = getString(rs, "INDEXTYPE");
				if (modern && java.util.Set.of("XPTH", "XRGN", "XVIP").contains(nativeType == null ? "" : nativeType))
					return;
				String catalogName = null;
				String schemaName = getString(rs, SCHEMA_NAME);
				String name = getString(rs, INDEX_NAME);
				boolean uniqueness = !"D".equalsIgnoreCase(getString(rs, "UNIQUERULE"));
				Index index = map.get(catalogName, schemaName, name);
				if (index == null) {
					index = new Index(name);
					index.setCatalogName(catalogName);
					index.setSchemaName(getString(rs, "table_schema"));
					index.getSpecifics().put("INDEX_SCHEMA", schemaName);
					index.setTableName(getString(rs, TABLE_NAME));
					index.setUnique(uniqueness);
					index.setCompression("Y".equalsIgnoreCase(getString(rs, "COMPRESSION")));
					index.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
					index.setIndexType(Db2Utils.getIndexType(getString(rs, "INDEXTYPE")));
					index.setTableSpaceName(this.getString(rs, "table_space"));
					index.setRemarks(getString(rs, REMARKS));
					setSpecifics(rs, "INDEXTYPE", index);
					setSpecifics(rs, "COMPRESSION", index);
					if (getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110) {
						setSpecifics(rs, "FUNC_PATH", index);
						setSpecifics(rs, "NULLKEYS", index);
					}
					setSpecifics(rs, "PCTFREE", index);
					//
					setSpecifics(rs, "MINPCTUSED", index);
					setSpecifics(rs, "REVERSE_SCANS", index);
					//
					map.put(catalogName, schemaName, name, index);
					result.add(index);
				}
				if (modern && "XVIL".equals(nativeType)) {
					for (String field : java.util.List.of("XML_PATTERN", "XML_TYPEMODEL", "XML_DATATYPE", "XML_HASHED",
							"XML_LENGTH", "XML_SCALE")) {
						String value = rs.getString(field);
						if (value == null)
							continue;
						if (!"XML_PATTERN".equals(field))
							value = value.stripTrailing();
						String previous = index.getSpecifics().get(field);
						if (previous != null && !previous.equals(value))
							throw new SQLException(
									"Db2 XML index has conflicting pattern/type rows; complete native definition required: "
											+ name);
						index.getSpecifics().put(field, value);
					}
				}
				String colOrder = getString(rs, "COLORDER");
				String columnName = getString(rs, COLUMN_NAME);
				if (modern && colOrder == null && columnName == null)
					return;
				if (!java.util.Set.of("A", "D", "I", "R").contains(colOrder == null ? "" : colOrder))
					throw new SQLException("Unsupported Db2 index column order: " + colOrder);
				if (modern && "XVIL".equals(nativeType) && index.getColumns().contains(columnName)) {
					if (!colOrder.equals(index.getColumns().get(columnName).getSpecifics().get("DB2_COLORDER")))
						throw new SQLException("Conflicting Db2 XML index key order: " + name);
					return;
				}
				var reference = new com.sqlapp.data.schemas.ReferenceColumn(columnName);
				reference.setIncludedColumn("I".equals(colOrder));
				reference.setOrder(Order.parse(colOrder));
				reference.getSpecifics().put("DB2_COLORDER", colOrder);
				if (getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110) {
					String expression = rs.getString("key_expression"), virtual = getString(rs, "key_virtual");
					if (virtual != null)
						reference.getSpecifics().put("DB2_VIRTUAL", virtual);
					if (expression != null) {
						if (expression.isBlank())
							throw new SQLException("Blank Db2 index key expression");
						reference.setStatement(expression);
					} else if ("Y".equals(virtual) || "S".equals(virtual)) {
						throw new SQLException("Db2 virtual index column is missing its expression: " + columnName);
					}
				}
				index.getColumns().add(reference);
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache(Db2IndexReader.class).getString(
				getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110 ? "indexes1110.sql" : "indexes.sql");
	}

}
