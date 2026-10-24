/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Table;

/**
 * Heterogeneous RANGE syntax preserves each parent's own ranges and load units.
 */
final class SapHanaRangePartitions {
	private SapHanaRangePartitions() {
	}

	static boolean applies(Table table) {
		var p = table.getPartitioning();
		if (p != null && p.getPartitioningType() == PartitioningType.Hash
				&& p.getSubPartitioningType() == PartitioningType.Range && p.getPartitionSize() != null
				&& !p.getPartitions().isEmpty() && p.getPartitions().size() == p.getPartitionSize()) {
			var first = p.getPartitions().getFirst().getSubPartitions();
			String inherited = inheritedUnit(table.getSpecifics().get("LOAD_UNIT"));
			for (var parent : p.getPartitions()) {
				if (blank(parent.getLowValue()) != null || blank(parent.getHighValue()) != null)
					return true;
				String unit = explicitUnit(parent);
				if (unit != null && !unit.equalsIgnoreCase(inherited))
					return true;
				if (parent.getSubPartitions().size() != first.size())
					return true;
				for (int i = 0; i < first.size(); i++) {
					var child = parent.getSubPartitions().get(i);
					if (SapHanaPartitionOptions.requiresHeterogeneous(child))
						return true;
					unit = explicitUnit(child);
					if (unit != null && !unit.equalsIgnoreCase(inherited))
						return true;
					if (!java.util.Objects.equals(first.get(i).getLowValue(), child.getLowValue())
							|| !java.util.Objects.equals(first.get(i).getHighValue(), child.getHighValue())
							|| first.get(i).isLowValueInclusive() != child.isLowValueInclusive()
							|| first.get(i).isHighValueInclusive() != child.isHighValueInclusive())
						return true;
					if (!SapHanaPartitionOptions.same(first.get(i), child))
						return true;
				}
			}
			return false;
		}
		return p != null && p.getPartitioningType() == PartitioningType.Range
				&& (p.getSubPartitioningType() == PartitioningType.Range
						|| p.getSubPartitioningType() == PartitioningType.Hash || p.getPartitions().stream().anyMatch(
								r -> explicitUnit(r) != null || SapHanaPartitionOptions.requiresHeterogeneous(r)));
	}

	static void append(Table table, SapHanaSqlBuilder builder) {
		var p = table.getPartitioning();
		boolean hash = p.getPartitioningType() == PartitioningType.Hash;
		if (table.getTableDataStoreType() != null && table.getTableDataStoreType().isRow())
			throw new IllegalArgumentException("SAP HANA partitioning requires a column table: " + table.getName());
		if (p.getSubPartitioningType() != null && p.getSubPartitioningType() != PartitioningType.Range
				&& p.getSubPartitioningType() != PartitioningType.Hash)
			throw new IllegalArgumentException(
					"Unsupported SAP HANA heterogeneous subpartitioning: " + p.getSubPartitioningType());
		if (p.getPartitioningColumns().isEmpty() || !hash && p.getPartitioningColumns().size() != 1
				|| p.getPartitions().isEmpty())
			throw new IllegalArgumentException(
					"SAP HANA partitioning requires columns (one for RANGE) and partitions: " + table.getName());
		for (var part : p.getPartitioningColumns())
			column(table, part.getName());
		var column = column(table, p.getPartitioningColumns().getFirst().getName());
		SapHanaPartitionKey subColumn = null;
		if (p.getSubPartitioningType() != null) {
			if (p.getSubPartitioningColumns().isEmpty() || p.getSubPartitioningType() == PartitioningType.Range
					&& p.getSubPartitioningColumns().size() != 1)
				throw new IllegalArgumentException(
						"SAP HANA subpartitioning requires columns (one for RANGE): " + table.getName());
			for (var sub : p.getSubPartitioningColumns())
				column(table, sub.getName());
			subColumn = column(table, p.getSubPartitioningColumns().getFirst().getName());
		}
		builder._add(" PARTITION BY ")._add(hash ? "HASH" : "RANGE")._add(" (");
		int key = 0;
		for (var part : p.getPartitioningColumns()) {
			builder.comma(key++ > 0);
			SapHanaPartitionKey.resolve(table, part.getName()).append(builder);
		}
		builder._add(")");
		if (hash) {
			if (p.getPartitionSize() == null || p.getPartitionSize() < 1
					|| p.getPartitions().size() != p.getPartitionSize())
				throw new IllegalArgumentException(
						"SAP HANA heterogeneous HASH requires every parent bucket: " + table.getName());
			builder._add(" PARTITIONS ")._add(p.getPartitionSize());
		}
		builder._add(" (");
		boolean countOnly = p.getPartitions().stream().allMatch(r -> r.getSubPartitions().isEmpty());
		if (hash && p.getSubPartitioningType() == PartitioningType.Range && countOnly)
			throw new IllegalArgumentException("SAP HANA HASH-RANGE requires subpartition ranges: " + table.getName());
		int i = 0;
		for (var parent : p.getPartitions()) {
			builder.comma(i++ > 0)._add("(");
			if (hash) {
				if (blank(parent.getLowValue()) != null || blank(parent.getHighValue()) != null)
					throw new IllegalArgumentException(
							"SAP HANA HASH parents cannot have RANGE bounds: " + parent.getName());
				String unit = explicitUnit(parent);
				if (unit != null && !unit.equalsIgnoreCase(inheritedUnit(table.getSpecifics().get("LOAD_UNIT"))))
					throw new IllegalArgumentException(
							"SAP HANA HASH parent load units must inherit the table: " + parent.getName());
				builder._add("PARTITION ")._add(i);
			} else
				range(parent, column, builder);
			builder._add(")");
			if (p.getSubPartitioningType() == PartitioningType.Hash) {
				int count = parent.getSubPartitions().size();
				if (count == 0 && countOnly) {
					if (p.getSubPartitionSize() == null || p.getSubPartitionSize() < 1)
						throw new IllegalArgumentException(
								"SAP HANA HASH subpartition count must be positive: " + table.getName());
					count = p.getSubPartitionSize();
				}
				if (count > 0) {
					String inherited = explicitUnit(parent);
					if (inherited == null)
						inherited = table.getSpecifics().get("LOAD_UNIT");
					inherited = inheritedUnit(inherited);
					for (var child : parent.getSubPartitions()) {
						if (blank(child.getLowValue()) != null || blank(child.getHighValue()) != null)
							throw new IllegalArgumentException(
									"SAP HANA HASH buckets cannot have RANGE bounds: " + child.getName());
						String unit = explicitUnit(child);
						if (unit != null && !unit.equalsIgnoreCase(inherited))
							throw new IllegalArgumentException(
									"SAP HANA HASH bucket load units must inherit their parent: " + child.getName());
					}
					builder._add(" SUBPARTITION BY HASH (");
					int j = 0;
					for (var sub : p.getSubPartitioningColumns()) {
						builder.comma(j++ > 0);
						SapHanaPartitionKey.resolve(table, sub.getName()).append(builder);
					}
					builder._add(") PARTITIONS ")._add(count);
				}
			} else if (!parent.getSubPartitions().isEmpty()) {
				if (subColumn == null)
					throw new IllegalArgumentException(
							"SAP HANA subranges require RANGE subpartitioning: " + table.getName());
				builder._add(" SUBPARTITION BY RANGE (");
				SapHanaPartitionKey.resolve(table, p.getSubPartitioningColumns().getFirst().getName()).append(builder);
				builder._add(") (");
				int j = 0;
				for (var child : parent.getSubPartitions()) {
					builder.comma(j++ > 0);
					range(child, subColumn, builder);
				}
				builder._add(")");
			}
		}
		builder._add(")");
	}

	private static SapHanaPartitionKey column(Table table, String name) {
		return SapHanaPartitionKey.resolve(table, name);
	}

	private static void range(AbstractPartition<?> range, SapHanaPartitionKey column, SapHanaSqlBuilder builder) {
		String low = blank(range.getLowValue()), high = blank(range.getHighValue());
		if (low != null && !range.isLowValueInclusive()
				|| high != null && range.isHighValueInclusive() && !high.equals(low))
			throw new IllegalArgumentException("SAP HANA ranges require inclusive lower and exclusive upper bounds");
		builder._add("PARTITION ");
		if (low == null && high == null || "OTHERS".equalsIgnoreCase(low) || "OTHERS".equalsIgnoreCase(high))
			builder._add("OTHERS");
		else if (low != null && low.equals(high)) {
			builder._add("VALUE = ");
			value(column, low, builder);
		} else {
			if (low != null) {
				value(column, low, builder);
				builder._add(" <= ");
			}
			builder._add("VALUES");
			if (high != null) {
				builder._add(" < ");
				value(column, high, builder);
			}
		}
		SapHanaPartitionOptions.append(range, column, builder, true);
		String unit = explicitUnit(range);
		if (unit != null)
			builder._add(" ")._add(unit)._add(" LOADABLE");
	}

	private static void value(SapHanaPartitionKey column, String value, SapHanaSqlBuilder builder) {
		column.appendValue(value, builder);
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String inheritedUnit(String unit) {
		return unit == null || unit.isBlank() || "DEFAULT".equalsIgnoreCase(unit) || "TABLE".equalsIgnoreCase(unit)
				? "COLUMN"
				: unit;
	}

	private static String explicitUnit(AbstractPartition<?> range) {
		String unit = blank(range.getSpecifics().get("LOAD_UNIT"));
		if (unit == null)
			return null;
		unit = unit.toUpperCase(java.util.Locale.ROOT);
		if ("DEFAULT".equals(unit) || "TABLE".equals(unit))
			return null;
		if (!"PAGE".equals(unit) && !"COLUMN".equals(unit))
			throw new IllegalArgumentException("Unsupported SAP HANA partition load unit: " + unit);
		return unit;
	}
}
