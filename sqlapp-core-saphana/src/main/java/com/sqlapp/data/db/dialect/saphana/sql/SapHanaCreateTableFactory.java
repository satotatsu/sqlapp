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

package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.PartitioningType;

public class SapHanaCreateTableFactory extends AbstractCreateTableFactory<SapHanaSqlBuilder> {
	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(Table table) {
		boolean definitionBacked = "DEFINITION".equals(table.getSpecifics().get("PARTITIONING_REPRESENTATION"));
		if (table.getDefinition().isEmpty()) {
			if (definitionBacked)
				throw new IllegalArgumentException(
						"SAP HANA definition-backed table requires complete executable CREATE DDL: " + table.getName());
			var result = super.createSql(table);
			var partitioning = table.getPartitioning();
			if (partitioning != null && partitioning.getPartitioningType() == PartitioningType.Hash
					&& partitioning.getSubPartitioningType() == null) {
				for (int i = 0; i < partitioning.getPartitions().size(); i++) {
					String nodes = partitioning.getPartitions().get(i).getSpecifics().get("NUMA_NODE_INDEXES");
					if (nodes != null && !nodes.isBlank())
						addSql(result,
								createSqlBuilder()._add("ALTER TABLE ").name(table, getOptions().isDecorateSchemaName())
										._add(" ALTER PARTITION ")._add(i + 1)._add(" NUMA NODE (")
										._add(SapHanaPartitionOptions.numaNodes(nodes))._add(")"),
								com.sqlapp.data.db.sql.SqlType.ALTER, table);
				}
			}
			return result;
		}
		if (definitionBacked
				&& (!java.util.Objects.equals(table.getName(), table.getSpecifics().get("DEFINITION_SOURCE_TABLE"))
						|| !java.util.Objects.equals(table.getSchemaName(),
								table.getSpecifics().get("DEFINITION_SOURCE_SCHEMA"))))
			throw new IllegalArgumentException(
					"SAP HANA complete table DDL retains its original names; supply retargeted DDL and update definition provenance explicitly");
		var result = new java.util.ArrayList<com.sqlapp.data.db.sql.SqlOperation>();
		// GET_OBJECT_DEFINITION includes secondary indexes after the CREATE.
		// Split with the dialect tokenizer, not on literal semicolons, and do
		// not emit the supplemental modeled indexes a second time.
		for (var statement : getDialect().createSqlSplitter().parse(String.join("\n", table.getDefinition())))
			if (!statement.getTextType().isComment() && !statement.getText().isBlank())
				addSql(result, createSqlBuilder()._add(statement.getText()), com.sqlapp.data.db.sql.SqlType.CREATE,
						table);
		if (result.isEmpty())
			throw new IllegalArgumentException(
					"SAP HANA table definition contains no executable CREATE DDL: " + table.getName());
		return result;
	}

	@Override
	protected void addColumnDefinition(final com.sqlapp.data.schemas.Column column, final SapHanaSqlBuilder builder) {
		super.addColumnDefinition(column, builder);
		String unit = column.getSpecifics().get("LOAD_UNIT");
		if (unit == null || unit.isBlank())
			return;
		unit = unit.toUpperCase(java.util.Locale.ROOT);
		if (!java.util.Set.of("TABLE", "DEFAULT", "PAGE", "COLUMN").contains(unit))
			throw new IllegalArgumentException("Unsupported SAP HANA column load unit: " + unit);
		if ("TABLE".equals(unit) || "DEFAULT".equals(unit))
			return;
		if (column.getTable().getTableDataStoreType() != null && column.getTable().getTableDataStoreType().isRow())
			throw new IllegalArgumentException(
					"SAP HANA column load units require a column table: " + column.getName());
		builder._add(" ")._add(unit)._add(" LOADABLE");
	}

	@Override
	protected void addCreateObject(final Table obj, final SapHanaSqlBuilder builder) {
		builder.create();
		builder.column(obj.getTableDataStoreType() == null || obj.getTableDataStoreType().isColumn());
		builder.row(obj.getTableDataStoreType() != null && obj.getTableDataStoreType().isRow());
		builder.table();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addOption(final Table table, final SapHanaSqlBuilder builder) {
		SapHanaPartitionOptions.validate(table);
		if (SapHanaRangePartitions.applies(table))
			SapHanaRangePartitions.append(table, builder);
		else {
			validateInheritedPartitionLoadUnits(table);
			addPartitioning(table, builder);
			addSubPartitioning(table, builder);
		}
		addLoadUnit(table, builder);
	}

	private void validateInheritedPartitionLoadUnits(final Table table) {
		if (table.getPartitioning() == null)
			return;
		String inherited = table.getSpecifics().get("LOAD_UNIT");
		if (inherited == null || inherited.isBlank() || "DEFAULT".equalsIgnoreCase(inherited))
			inherited = "COLUMN";
		for (var parent : table.getPartitioning().getPartitions()) {
			validateInheritedLoadUnit(parent, inherited);
			for (var child : parent.getSubPartitions())
				validateInheritedLoadUnit(child, inherited);
		}
	}

	private void validateInheritedLoadUnit(final com.sqlapp.data.schemas.AbstractPartition<?> partition,
			final String inherited) {
		String unit = partition.getSpecifics().get("LOAD_UNIT");
		if (unit != null && !unit.isBlank() && !"DEFAULT".equalsIgnoreCase(unit) && !"TABLE".equalsIgnoreCase(unit)
				&& !inherited.equalsIgnoreCase(unit))
			throw new IllegalArgumentException(
					"SAP HANA mixed partition load units require RANGE heterogeneous partitioning or explicit DDL: "
							+ partition.getName());
	}

	private void addLoadUnit(final Table table, final SapHanaSqlBuilder builder) {
		String loadUnit = table.getSpecifics().get("LOAD_UNIT");
		if (loadUnit != null && !loadUnit.isBlank()) {
			loadUnit = loadUnit.toUpperCase(java.util.Locale.ROOT);
			if (!java.util.Set.of("TABLE", "PAGE", "COLUMN", "DEFAULT").contains(loadUnit))
				throw new IllegalArgumentException("Unsupported SAP HANA load unit: " + loadUnit);
			if (!"TABLE".equals(loadUnit) && !"DEFAULT".equals(loadUnit)) {
				if (table.getTableDataStoreType() != null && table.getTableDataStoreType().isRow())
					throw new IllegalArgumentException(
							"SAP HANA PAGE/COLUMN load units require a column table: " + table.getName());
				builder._add(" ")._add(loadUnit)._add(" LOADABLE");
			}
		}
	}

	private void addPartitioning(final Table table, final SapHanaSqlBuilder builder) {
		var partitioning = table.getPartitioning();
		if (partitioning == null)
			return;
		if (table.getTableDataStoreType() != null && table.getTableDataStoreType().isRow())
			throw new IllegalArgumentException("SAP HANA partitioning requires a column table: " + table.getName());
		var type = partitioning.getPartitioningType();
		if (type != PartitioningType.Hash && type != PartitioningType.Range && type != PartitioningType.RoundRobin)
			throw new IllegalArgumentException("Unsupported SAP HANA partitioning type: " + type);
		builder._add(" PARTITION BY ")._add(type);
		if (type != PartitioningType.RoundRobin) {
			if (partitioning.getPartitioningColumns().isEmpty())
				throw new IllegalArgumentException("SAP HANA partitioning requires columns: " + table.getName());
			builder._add(" (");
			int position = 0;
			for (var column : partitioning.getPartitioningColumns()) {
				builder.comma(position++ > 0);
				SapHanaPartitionKey.resolve(table, column.getName()).append(builder);
			}
			builder._add(")");
		}
		if (type != PartitioningType.Range) {
			if (partitioning.getPartitionSize() == null || partitioning.getPartitionSize() < 1)
				throw new IllegalArgumentException("SAP HANA partition count must be positive: " + table.getName());
			builder._add(" PARTITIONS ")._add(partitioning.getPartitionSize());
			return;
		}
		if (partitioning.getPartitions().isEmpty())
			throw new IllegalArgumentException("SAP HANA RANGE partitioning requires ranges: " + table.getName());
		if (partitioning.getPartitioningColumns().size() != 1)
			throw new IllegalArgumentException("SAP HANA RANGE partitioning requires one column: " + table.getName());
		builder._add(" (");
		int position = 0;
		for (var partition : partitioning.getPartitions()) {
			String low = partition.getLowValue();
			String high = partition.getHighValue();
			if (low != null && low.isBlank())
				low = null;
			if (high != null && high.isBlank())
				high = null;
			if (low != null && !partition.isLowValueInclusive()
					|| high != null && partition.isHighValueInclusive() && !high.equals(low))
				throw new IllegalArgumentException(
						"SAP HANA ranges require inclusive lower and exclusive upper bounds: " + table.getName());
			builder.comma(position++ > 0)._add("PARTITION ");
			if (low == null && high == null || "OTHERS".equalsIgnoreCase(low) || "OTHERS".equalsIgnoreCase(high)) {
				builder._add("OTHERS");
			} else if (low != null && low.equals(high)) {
				builder._add("VALUE = ");
				addRangeValue(table, low, builder);
			} else {
				if (low != null) {
					addRangeValue(table, low, builder);
					builder._add(" <= ");
				}
				builder._add("VALUES");
				if (high != null) {
					builder._add(" < ");
					addRangeValue(table, high, builder);
				}
			}
			SapHanaPartitionOptions.append(partition,
					SapHanaPartitionKey.resolve(table, partitioning.getPartitioningColumns().getFirst().getName()),
					builder, false);
		}
		builder._add(")");
	}

	private void addRangeValue(final Table table, final String value, final SapHanaSqlBuilder builder) {
		SapHanaPartitionKey.resolve(table, table.getPartitioning().getPartitioningColumns().getFirst().getName())
				.appendValue(value, builder);
	}

	private void addSubPartitioning(final Table table, final SapHanaSqlBuilder builder) {
		var partitioning = table.getPartitioning();
		if (partitioning == null || partitioning.getSubPartitioningType() == null)
			return;
		var type = partitioning.getSubPartitioningType();
		if (type != PartitioningType.Range
				&& !(type == PartitioningType.Hash && partitioning.getPartitioningType() == PartitioningType.Hash))
			throw new IllegalArgumentException("Unsupported SAP HANA subpartitioning combination: "
					+ partitioning.getPartitioningType() + "/" + type);
		var columns = partitioning.getSubPartitioningColumns();
		if (columns.isEmpty() || type == PartitioningType.Range && columns.size() != 1)
			throw new IllegalArgumentException(
					"SAP HANA subpartitioning requires columns (one for RANGE): " + table.getName());
		builder._add(", ")._add(type)._add(" (");
		int position = 0;
		for (var column : columns) {
			builder.comma(position++ > 0);
			SapHanaPartitionKey.resolve(table, column.getName()).append(builder);
		}
		builder._add(")");
		if (type == PartitioningType.Hash) {
			if (partitioning.getSubPartitionSize() == null || partitioning.getSubPartitionSize() < 1)
				throw new IllegalArgumentException("SAP HANA subpartition count must be positive: " + table.getName());
			for (var parent : partitioning.getPartitions()) {
				if (!parent.getSubPartitions().isEmpty()
						&& parent.getSubPartitions().size() != partitioning.getSubPartitionSize())
					throw new IllegalArgumentException(
							"SAP HANA heterogeneous subpartition counts require explicit DDL: " + table.getName());
			}
			builder._add(" PARTITIONS ")._add(partitioning.getSubPartitionSize());
			return;
		}
		if (partitioning.getPartitions().isEmpty()
				|| partitioning.getPartitions().getFirst().getSubPartitions().isEmpty())
			throw new IllegalArgumentException("SAP HANA RANGE subpartitioning requires ranges: " + table.getName());
		var ranges = partitioning.getPartitions().getFirst().getSubPartitions();
		for (var parent : partitioning.getPartitions()) {
			if (parent.getSubPartitions().size() != ranges.size())
				throw new IllegalArgumentException(
						"SAP HANA heterogeneous subpartitions require explicit DDL: " + table.getName());
			for (int i = 0; i < ranges.size(); i++) {
				if (!java.util.Objects.equals(ranges.get(i).getLowValue(),
						parent.getSubPartitions().get(i).getLowValue())
						|| !java.util.Objects.equals(ranges.get(i).getHighValue(),
								parent.getSubPartitions().get(i).getHighValue())
						|| ranges.get(i).isLowValueInclusive() != parent.getSubPartitions().get(i).isLowValueInclusive()
						|| ranges.get(i).isHighValueInclusive() != parent.getSubPartitions().get(i)
								.isHighValueInclusive()
						|| !SapHanaPartitionOptions.same(ranges.get(i), parent.getSubPartitions().get(i)))
					throw new IllegalArgumentException(
							"SAP HANA heterogeneous subpartitions require explicit DDL: " + table.getName());
			}
		}
		builder._add(" (");
		position = 0;
		for (var range : ranges) {
			String low = range.getLowValue();
			String high = range.getHighValue();
			if (low != null && low.isBlank())
				low = null;
			if (high != null && high.isBlank())
				high = null;
			if (low != null && !range.isLowValueInclusive()
					|| high != null && range.isHighValueInclusive() && !high.equals(low))
				throw new IllegalArgumentException(
						"SAP HANA subranges require inclusive lower and exclusive upper bounds: " + table.getName());
			builder.comma(position++ > 0)._add("PARTITION ");
			var column = SapHanaPartitionKey.resolve(table, columns.getFirst().getName());
			if (low == null && high == null || "OTHERS".equalsIgnoreCase(low) || "OTHERS".equalsIgnoreCase(high))
				builder._add("OTHERS");
			else if (low != null && low.equals(high)) {
				builder._add("VALUE = ");
				column.appendValue(low, builder);
			} else {
				if (low != null) {
					column.appendValue(low, builder);
					builder._add(" <= ");
				}
				builder._add("VALUES");
				if (high != null) {
					builder._add(" < ");
					column.appendValue(high, builder);
				}
			}
			SapHanaPartitionOptions.append(range, column, builder, false);
		}
		builder._add(")");
	}
}
