/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

public class SapHanaTableReader extends TableReader {

	protected SapHanaTableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlSqlNode(productVersionInfo);
		final List<Table> result = list();
		final DoubleKeyMap<String, String, Table> tableMap = CommonUtils.doubleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Table table = createTable(rs);
				tableMap.put(table.getSchemaName(), table.getName(), table);
				result.add(table);
			}
		});
		if (result.isEmpty()) {
			return result;
		}
		SqlNode partNode = getPartitionSqlSqlNode(productVersionInfo);
		ParametersContext ccontext = context.clone();
		ccontext.put(SCHEMA_NAME, tableMap.keySet());
		ccontext.put(TABLE_NAME, tableMap.secondKeySet());
		execute(connection, partNode, ccontext, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String schemaName = this.getString(rs, SCHEMA_NAME);
				String tableName = this.getString(rs, TABLE_NAME);
				Table table = tableMap.get(schemaName, tableName);
				if (table == null) {
					return;
				}
				if ("DEFINITION".equals(table.getSpecifics().get("PARTITIONING_REPRESENTATION")))
					return;
				if (hasThirdLevel(rs)) {
					readTableDefinition(connection, table);
					return;
				}
				table.toPartitioning();
				String level1Expression = this.getString(rs, "LEVEL_1_EXPRESSION");
				if (table.getPartitioning().getPartitioningColumns().isEmpty()) {
					for (String col : partitionColumns(level1Expression)) {
						if (!CommonUtils.isEmpty(col)) {
							table.getPartitioning().getPartitioningColumns().add(col);
						}
					}
				}
				String level2Expression = this.getString(rs, "LEVEL_2_EXPRESSION");
				if (!CommonUtils.isEmpty(level2Expression)
						&& table.getPartitioning().getSubPartitioningColumns().isEmpty()) {
					for (String col : partitionColumns(level2Expression)) {
						if (!CommonUtils.isEmpty(col)) {
							table.getPartitioning().getSubPartitioningColumns().add(col);
						}
					}
				}
				if (table.getPartitioning().getPartitioningType() == null) {
					int level1Count = this.getInteger(rs, "LEVEL_1_COUNT");
					int level2Count = this.getInteger(rs, "LEVEL_2_COUNT");
					String level1Type = this.getString(rs, "LEVEL_1_TYPE");
					String level2Type = this.getString(rs, "LEVEL_2_TYPE");
					table.getPartitioning().setPartitioningType(PartitioningType.parse(level1Type));
					table.getPartitioning().setSubPartitioningType(PartitioningType.parse(level2Type));
					if (table.getPartitioning().getPartitioningType().isSizePartitioning()) {
						table.getPartitioning().setPartitionSize(level1Count);
					}
					if (table.getPartitioning().getSubPartitioningType() != null
							&& table.getPartitioning().getSubPartitioningType().isSizePartitioning()) {
						table.getPartitioning().setSubPartitionSize(level2Count);
					}
					setStatistics(rs, "EXTENDED_STORAGE_ENABLE_DELTA", table.getPartitioning());
				}
				int level1Partition = this.getInteger(rs, "LEVEL_1_PARTITION");
				int level2Partition = this.getInteger(rs, "LEVEL_2_PARTITION");
				if (level1Partition <= 0)
					return; // The heterogeneous tree root is not a partition.
				Partition partition;
				if (table.getPartitioning().getPartitions().size() >= level1Partition && level1Partition > 0) {
					partition = table.getPartitioning().getPartitions().get(level1Partition - 1);
				} else {
					partition = table.getPartitioning().getPartitions().newElement();
					partition.setId(this.getString(rs, "PART_ID"));
					// Parent ranges also need distinct names for Schema XML identity.
					// Otherwise unnamed parents resolve to the same object on XML read.
					partition.setName(Integer.toString(level1Partition));
					String levelRangeMinValue = this.getString(rs, "LEVEL_1_RANGE_MIN_VALUE");
					String levelRangeMaxValue = this.getString(rs, "LEVEL_1_RANGE_MAX_VALUE");
					partition.setLowValue(levelRangeMinValue);
					partition.setHighValue(levelRangeMaxValue);
					if (level2Partition == 0) {
						setPartitionData(rs, partition);
					}
					table.getPartitioning().getPartitions().add(partition);
				}
				if (level2Partition != 0) {
					SubPartition subpartition = partition.getSubPartitions().newElement();
					subpartition.setId(this.getString(rs, "PART_ID"));
					subpartition.setName(subpartition.getId());
					String levelRangeMinValueSub = this.getString(rs, "LEVEL_2_RANGE_MIN_VALUE");
					String levelRangeMaxValueSub = this.getString(rs, "LEVEL_2_RANGE_MAX_VALUE");
					subpartition.setLowValue(levelRangeMinValueSub);
					subpartition.setHighValue(levelRangeMaxValueSub);
					setPartitionData(rs, subpartition);
					partition.getSubPartitions().add(subpartition);
				} else {
					// Heterogeneous parent nodes have no physical PART_ID; their
					// bounds/preferences occur on a separate row from the leaf rows.
					partition.setLowValue(this.getString(rs, "LEVEL_1_RANGE_MIN_VALUE"));
					partition.setHighValue(this.getString(rs, "LEVEL_1_RANGE_MAX_VALUE"));
					setPartitionData(rs, partition);
				}
			}
		});
		return result;
	}

	private void readTableDefinition(Connection connection, Table table) {
		// Exception to bulk metadata: TABLES exposes an internal compact JSON
		// tree, not executable SQL. GET_OBJECT_DEFINITION accepts one object,
		// so only unmodelable three-level tables need one extra call each.
		var parameters = new ParametersContext();
		parameters.put("definitionSchemaName", table.getSchemaName());
		parameters.put("definitionTableName", table.getName());
		execute(connection, getSqlNodeCache().getString("tableDefinitions.sql"), parameters,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(ExResultSet rs) throws SQLException {
						String ddl = rs.getString("OBJECT_CREATION_STATEMENT");
						if (ddl == null || !ddl.stripLeading().toUpperCase(java.util.Locale.ROOT).startsWith("CREATE "))
							throw new SQLException("SAP HANA did not return executable table DDL for "
									+ table.getSchemaName() + "." + table.getName());
						table.setDefinition(ddl);
					}
				});
		if (table.getDefinition().isEmpty())
			throw new IllegalStateException(
					"SAP HANA table definition is unavailable: " + table.getSchemaName() + "." + table.getName());
		table.setPartitioning(null);
		table.getSpecifics().put("PARTITIONING_REPRESENTATION", "DEFINITION");
		table.getSpecifics().put("DEFINITION_SOURCE_TABLE", table.getName());
		table.getSpecifics().put("DEFINITION_SOURCE_SCHEMA", table.getSchemaName());
	}

	static void validatePartitionDepth(ExResultSet rs, String schemaName, String tableName) throws SQLException {
		if (hasThirdLevel(rs))
			throw new SQLException(
					"SAP HANA three-level partitioning cannot be represented by the two-level Schema model: "
							+ schemaName + "." + tableName + "; retain the original CREATE TABLE DDL for this table");
	}

	private static boolean hasThirdLevel(ExResultSet rs) throws SQLException {
		// Optional catalog columns are absent on older HANA versions. Check both
		// the table declaration and leaf rows before reducing the tree to our
		// two-level Schema model, including empty third-level branches.
		Object type = rs.getObject("LEVEL_3_TYPE");
		Object expression = rs.getObject("LEVEL_3_EXPRESSION");
		Object count = rs.getObject("LEVEL_3_COUNT");
		Object partition = rs.getObject("LEVEL_3_PARTITION");
		return type != null && !type.toString().isBlank() || expression != null && !expression.toString().isBlank()
				|| count != null && Long.parseLong(count.toString()) > 0
				|| partition != null && Long.parseLong(partition.toString()) > 0;
	}

	static List<String> partitionColumns(final String expression) {
		List<String> columns = new java.util.ArrayList<>();
		if (expression == null || expression.isBlank())
			return columns;
		boolean quoted = false;
		int depth = 0;
		int start = 0;
		for (int i = 0; i <= expression.length(); i++) {
			char value = i == expression.length() ? ',' : expression.charAt(i);
			if (value == '"') {
				if (quoted && i + 1 < expression.length() && expression.charAt(i + 1) == '"')
					i++;
				else
					quoted = !quoted;
			} else if (!quoted && value == '(')
				depth++;
			else if (!quoted && value == ')')
				depth--;
			else if (!quoted && depth == 0 && value == ',') {
				String column = expression.substring(start, i).trim();
				if (column.startsWith("\"") && column.endsWith("\"") && !column.contains("("))
					column = column.substring(1, column.length() - 1).replace("\"\"", "\"");
				if (!column.isEmpty())
					columns.add(column);
				start = i + 1;
			}
		}
		if (quoted || depth != 0)
			throw new IllegalArgumentException("Invalid SAP HANA partition expression: " + expression);
		return columns;
	}

	private void setPartitionData(ExResultSet rs, AbstractPartition<?> partition) throws SQLException {
		setSpecifics(rs, "LOAD_UNIT", partition);
		// Keep ON as well: a child can override its parent's INSERT OFF.
		setSpecifics(rs, "INSERT", partition);
		setSpecifics(rs, "IS_DYNAMIC", partition);
		setSpecifics(rs, "STORAGE_TYPE", partition);
		Object threshold = rs.getObject("DYNAMIC_RANGE_THRESHOLD");
		if (threshold != null && Long.parseLong(threshold.toString()) != -1) {
			partition.getStatistics().put("DYNAMIC_RANGE_THRESHOLD", Long.parseLong(threshold.toString()));
		}
		setSpecifics(rs, "DYNAMIC_RANGE_INTERVAL", partition);
		setSpecifics(rs, "PERSISTENT_MEMORY", partition);
		setSpecifics(rs, "GROUP_TYPE", partition);
		setSpecifics(rs, "SUBTYPE", "SUB_TYPE", partition);
		setSpecifics(rs, "GROUP_NAME", partition);
		setSpecifics(rs, "NUMA_NODE_INDEXES", partition);
		setSpecifics(rs, "DYNAMIC_DISTANCE_INTERVAL", partition);
		setSpecifics(rs, "DYNAMIC_DISTANCE_LOAD_UNIT", partition);
		setSpecifics(rs, "DYNAMIC_RANGE_DIRECTION", partition);
		setSpecifics(rs, "IS_MOVABLE", partition);
		inferLegacyDynamic(partition);
	}

	static void inferLegacyDynamic(AbstractPartition<?> partition) {
		// IS_DYNAMIC was added after the original per-partition policy fields.
		// Never override an explicit FALSE or infer from a table-wide default on
		// an ordinary bounded range.
		if (partition.getSpecifics().containsKey("IS_DYNAMIC"))
			return;
		String low = partition.getLowValue(), high = partition.getHighValue();
		boolean others = (low == null || low.isBlank()) && (high == null || high.isBlank())
				|| "OTHERS".equalsIgnoreCase(low) || "OTHERS".equalsIgnoreCase(high);
		if (!others)
			return;
		String threshold = partition.getStatistics().get("DYNAMIC_RANGE_THRESHOLD");
		String interval = partition.getSpecifics().get("DYNAMIC_RANGE_INTERVAL");
		if (threshold != null && Long.parseLong(threshold) > 0 || interval != null && !interval.isBlank())
			partition.getSpecifics().put("IS_DYNAMIC", "TRUE");
	}

	protected Table createTable(ExResultSet rs) throws SQLException {
		Table table = createTable(getString(rs, TABLE_NAME));
		table.setSchemaName(getString(rs, SCHEMA_NAME));
		table.setId("" + rs.getLong("TABLE_OID"));
		table.setRemarks(getString(rs, "COMMENTS"));
		table.setTableDataStoreType(getString(rs, "TABLE_TYPE"));
		setSpecifics(rs, table);
		setStatistics(rs, table);
		return table;
	}

	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("tables.sql");
	}

	protected SqlNode getPartitionSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("partitions.sql");
	}

	protected void setSpecifics(ExResultSet rs, Table table) throws SQLException {
		setSpecifics(rs, "IS_LOGGED", table);
		setSpecifics(rs, "IS_SYSTEM_TABLE", table);
		setSpecifics(rs, "IS_COLUMN_TABLE", table);
		setSpecifics(rs, "IS_INSERT_ONLY", table);
		setSpecifics(rs, "IS_TENANT_SHARED_DATA", table);
		setSpecifics(rs, "IS_TENANT_SHARED_METADATA", table);
		setSpecifics(rs, "SESSION_TYPE", table);
		setSpecifics(rs, "IS_TEMPORARY", table);
		setSpecifics(rs, "TEMPORARY_TABLE_TYPE", table);
		setSpecifics(rs, "IS_USER_DEFINED_TYPE", table);
		setSpecifics(rs, "USES_EXTKEY", table);
		setSpecifics(rs, "AUTO_MERGE_ON", table);
		setSpecifics(rs, "LOAD_UNIT", table);
		if (table.getTableDataStoreType().isColumn()) {
			setSpecifics(rs, "PARTITION_SPEC", table);
			setSpecifics(rs, "USES_DIMFN_CACHE", table);
			setSpecifics(rs, "IS_PUBLIC", table);
			setSpecifics(rs, "COMPRESSED_EXTKEY", table);
			setSpecifics(rs, "HAS_TEXT_FIELDS", table);
			setSpecifics(rs, "USES_QUEUE_TABLE", table);
			setSpecifics(rs, "IS_PRELOAD", table);
			setSpecifics(rs, "IS_PARTIAL_PRELOAD", table);
		}
		setStatistics(rs, "FIXED_PART_SIZE", table);
	}

	protected void setStatistics(ExResultSet rs, Table table) throws SQLException {
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new SapHanaColumnReader(this.getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new SapHanaUniqueConstraintReader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new SapHanaCheckConstraintReader(this.getDialect());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.metadata.TableReader#newForeignKeyConstraintReader()
	 */
	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new SapHanaForeignKeyConstraintReader(this.getDialect());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.metadata.TableReader#newIndexReader()
	 */
	@Override
	protected IndexReader newIndexReader() {
		return new SapHanaIndexReader(this.getDialect());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.TableReader#newExcludeConstraintReader ()
	 */
	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}
}
