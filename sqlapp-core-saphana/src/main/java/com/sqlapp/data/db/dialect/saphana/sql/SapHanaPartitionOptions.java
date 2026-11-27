/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Table;

/** Options shared by balanced and heterogeneous RANGE generation. */
final class SapHanaPartitionOptions {
	private SapHanaPartitionOptions() {
	}

	static void validate(Table table) {
		var p = table.getPartitioning();
		if (p == null)
			return;
		for (var parent : p.getPartitions()) {
			unsupported(parent);
			validateAgingLoadUnit(parent, table.getSpecifics().get("LOAD_UNIT"));
			if (p.getPartitioningType() != PartitioningType.Range)
				nonRange(parent,
						p.getPartitioningType() == PartitioningType.Hash && p.getSubPartitioningType() == null);
			if (p.getPartitioningType() == PartitioningType.Hash
					&& parent.getSpecifics().get("NUMA_NODE_INDEXES") != null
					&& (p.getPartitionSize() == null || p.getPartitions().size() != p.getPartitionSize()
							|| text(parent.getLowValue()) != null || text(parent.getHighValue()) != null))
				throw new IllegalArgumentException(
						"SAP HANA HASH NUMA preferences require every bucket in ordinal order without RANGE bounds");
			for (var child : parent.getSubPartitions()) {
				unsupported(child);
				validateAgingLoadUnit(child, parent.getSpecifics().get("LOAD_UNIT"),
						table.getSpecifics().get("LOAD_UNIT"));
				if (p.getSubPartitioningType() != PartitioningType.Range)
					nonRange(child, false);
			}
		}
	}

	private static void unsupported(AbstractPartition<?> partition) {
		String storage = text(partition.getSpecifics().get("STORAGE_TYPE"));
		if (storage != null && !"DEFAULT".equalsIgnoreCase(storage))
			throw new IllegalArgumentException(
					"SAP HANA partition storage placement requires explicit migration DDL: " + storage);
		String direction = text(partition.getSpecifics().get("DYNAMIC_RANGE_DIRECTION"));
		if (direction != null && !"INCREASING".equalsIgnoreCase(direction))
			throw new IllegalArgumentException(
					"SAP HANA dynamic direction requires explicit CREATE TABLE DDL: " + direction);
		String movable = text(partition.getSpecifics().get("IS_MOVABLE"));
		if (movable != null && !"DEFAULT".equalsIgnoreCase(movable))
			throw new IllegalArgumentException(
					"SAP HANA partition movability requires explicit CREATE TABLE DDL: " + movable);
	}

	private static void validateAgingLoadUnit(AbstractPartition<?> partition, String... inherited) {
		if (text(partition.getSpecifics().get("DYNAMIC_DISTANCE_INTERVAL")) == null)
			return;
		var units = new java.util.ArrayList<String>();
		units.add(partition.getSpecifics().get("LOAD_UNIT"));
		java.util.Collections.addAll(units, inherited);
		for (String unit : units) {
			if ("COLUMN".equalsIgnoreCase(text(unit)))
				throw new IllegalArgumentException(
						"SAP HANA dynamic aging cannot use explicit or inherited COLUMN LOADABLE; use DEFAULT or PAGE");
			if ("PAGE".equalsIgnoreCase(text(unit)))
				return;
		}
	}

	private static void nonRange(AbstractPartition<?> partition, boolean hashNuma) {
		if (Boolean.FALSE.equals(flag(partition, "INSERT")) || Boolean.TRUE.equals(flag(partition, "IS_DYNAMIC"))
				|| text(partition.getSpecifics().get("DYNAMIC_RANGE_INTERVAL")) != null)
			throw new IllegalArgumentException(
					"SAP HANA INSERT OFF and dynamic policies require RANGE partitions: " + partition.getName());
		for (String name : java.util.List.of("PERSISTENT_MEMORY", "GROUP_TYPE", "SUB_TYPE", "GROUP_NAME",
				"NUMA_NODE_INDEXES", "DYNAMIC_DISTANCE_INTERVAL", "DYNAMIC_DISTANCE_LOAD_UNIT")) {
			if (hashNuma && name.equals("NUMA_NODE_INDEXES"))
				continue;
			if (text(partition.getSpecifics().get(name)) != null)
				throw new IllegalArgumentException("SAP HANA partition " + name
						+ " requires RANGE partitions or explicit DDL: " + partition.getName());
		}
	}

	static boolean same(AbstractPartition<?> left, AbstractPartition<?> right) {
		return java.util.List
				.of("PERSISTENT_MEMORY", "GROUP_TYPE", "SUB_TYPE", "GROUP_NAME", "NUMA_NODE_INDEXES",
						"DYNAMIC_DISTANCE_INTERVAL", "DYNAMIC_DISTANCE_LOAD_UNIT")
				.stream()
				.allMatch(k -> java.util.Objects.equals(left.getSpecifics().get(k), right.getSpecifics().get(k)))
				&& java.util.Objects.equals(flag(left, "INSERT"), flag(right, "INSERT"))
				&& java.util.Objects.equals(flag(left, "IS_DYNAMIC"), flag(right, "IS_DYNAMIC"))
				&& java.util.Objects.equals(left.getStatistics().get("DYNAMIC_RANGE_THRESHOLD"),
						right.getStatistics().get("DYNAMIC_RANGE_THRESHOLD"))
				&& java.util.Objects.equals(left.getSpecifics().get("DYNAMIC_RANGE_INTERVAL"),
						right.getSpecifics().get("DYNAMIC_RANGE_INTERVAL"));
	}

	static boolean requiresHeterogeneous(AbstractPartition<?> partition) {
		return Boolean.FALSE.equals(flag(partition, "INSERT"))
				|| java.util.List
						.of("DYNAMIC_RANGE_INTERVAL", "DYNAMIC_DISTANCE_INTERVAL", "DYNAMIC_DISTANCE_LOAD_UNIT")
						.stream().anyMatch(k -> text(partition.getSpecifics().get(k)) != null)
				|| java.util.List.of("GROUP_TYPE", "SUB_TYPE", "GROUP_NAME").stream().anyMatch(
						k -> partition.getSpecifics().get(k) != null && !partition.getSpecifics().get(k).isEmpty());
	}

	static void append(AbstractPartition<?> partition, SapHanaPartitionKey key, SapHanaSqlBuilder builder,
			boolean heterogeneous) {
		if (!heterogeneous && requiresHeterogeneous(partition))
			throw new IllegalArgumentException(
					"SAP HANA partition options require heterogeneous partitioning or explicit CREATE TABLE DDL: "
							+ partition.getName());
		String interval = text(partition.getSpecifics().get("DYNAMIC_RANGE_INTERVAL"));
		String distance = text(partition.getSpecifics().get("DYNAMIC_DISTANCE_INTERVAL"));
		String distanceUnit = text(partition.getSpecifics().get("DYNAMIC_DISTANCE_LOAD_UNIT"));
		if (distanceUnit != null && (distance == null || !"PAGE".equalsIgnoreCase(distanceUnit)))
			throw new IllegalArgumentException("SAP HANA dynamic aging requires a DISTANCE and PAGE load unit");
		if (distance != null && !Boolean.TRUE.equals(flag(partition, "IS_DYNAMIC")))
			throw new IllegalArgumentException("SAP HANA dynamic aging requires IS_DYNAMIC=TRUE");
		if (interval != null && !Boolean.TRUE.equals(flag(partition, "IS_DYNAMIC")))
			throw new IllegalArgumentException(
					"SAP HANA DYNAMIC_RANGE_INTERVAL requires IS_DYNAMIC=TRUE: " + partition.getName());
		if (Boolean.TRUE.equals(flag(partition, "IS_DYNAMIC"))) {
			String low = text(partition.getLowValue()), high = text(partition.getHighValue());
			if (!(low == null && high == null || "OTHERS".equalsIgnoreCase(low) || "OTHERS".equalsIgnoreCase(high)))
				throw new IllegalArgumentException("SAP HANA DYNAMIC requires an OTHERS range: " + partition.getName());
			if (partition instanceof com.sqlapp.data.schemas.Partition parent && !parent.getSubPartitions().isEmpty())
				throw new IllegalArgumentException("SAP HANA DYNAMIC requires a leaf range: " + partition.getName());
			if (!key.column().isNotNull() || interval == null && key.function() != null)
				throw new IllegalArgumentException(
						"SAP HANA DYNAMIC requires a NOT NULL column; THRESHOLD requires a plain column: "
								+ key.column().getName());
			builder._add(" DYNAMIC");
			Object threshold = partition.getStatistics().get("DYNAMIC_RANGE_THRESHOLD");
			if (interval != null) {
				if (threshold != null)
					throw new IllegalArgumentException("SAP HANA DYNAMIC INTERVAL and THRESHOLD cannot be combined");
				appendInterval(interval, key, builder, " INTERVAL ");
			} else if (threshold != null) {
				long count;
				try {
					count = Long.parseLong(threshold.toString());
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException(
							"SAP HANA DYNAMIC_RANGE_THRESHOLD must be a positive integer: " + threshold, e);
				}
				if (count < 1)
					throw new IllegalArgumentException("SAP HANA DYNAMIC_RANGE_THRESHOLD must be positive: " + count);
				builder._add(" THRESHOLD ")._add(count);
			}
			if (distance != null) {
				appendInterval(distance, key, builder, " DISTANCE ");
				builder._add(" PAGE LOADABLE");
			}
		}
		Boolean insert = flag(partition, "INSERT");
		if (insert != null && heterogeneous)
			builder._add(insert ? " INSERT ON" : " INSERT OFF");
		Boolean persistent = flag(partition, "PERSISTENT_MEMORY");
		if (persistent != null)
			builder._add(persistent ? " PERSISTENT MEMORY ON" : " PERSISTENT MEMORY OFF");
		group(partition, "GROUP_NAME", "NAME", builder);
		group(partition, "GROUP_TYPE", "TYPE", builder);
		group(partition, "SUB_TYPE", "SUBTYPE", builder);
		String numa = text(partition.getSpecifics().get("NUMA_NODE_INDEXES"));
		if (numa != null) {
			builder._add(" NUMA NODE (")._add(numaNodes(numa))._add(")");
		}
	}

	private static void appendInterval(String interval, SapHanaPartitionKey key, SapHanaSqlBuilder builder,
			String keyword) {
		var match = java.util.regex.Pattern.compile("(?i)^([0-9]+)(?:\\s+(YEAR|MONTH|HOUR))?$").matcher(interval);
		if (!match.matches())
			throw new IllegalArgumentException("Invalid SAP HANA dynamic interval/distance: " + interval);
		long count;
		try {
			count = Long.parseLong(match.group(1));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("SAP HANA dynamic interval/distance is too large: " + interval, e);
		}
		if (count < 1)
			throw new IllegalArgumentException("SAP HANA dynamic interval/distance must be positive: " + interval);
		String unit = match.group(2) == null ? null : match.group(2).toUpperCase(java.util.Locale.ROOT);
		if (!java.util.Objects.equals(unit, key.function()) || unit == null && !java.util.Set
				.of(com.sqlapp.data.db.datatype.DataType.TINYINT, com.sqlapp.data.db.datatype.DataType.SMALLINT,
						com.sqlapp.data.db.datatype.DataType.INT, com.sqlapp.data.db.datatype.DataType.BIGINT)
				.contains(key.column().getDataType()))
			throw new IllegalArgumentException(
					"SAP HANA dynamic interval/distance requires an integer key or a matching YEAR/MONTH/HOUR expression: "
							+ interval);
		builder._add(keyword)._add(count);
		if (unit != null)
			builder._add(" ")._add(unit);
	}

	static String numaNodes(String specification) {
		var nodes = new java.util.ArrayList<String>();
		for (String part : specification.split(",", -1)) {
			var match = java.util.regex.Pattern.compile("(?i)^([0-9]+)(?:\\s*TO\\s*([0-9]+))?$").matcher(part.trim());
			if (!match.matches())
				throw new IllegalArgumentException("Invalid SAP HANA NUMA_NODE_INDEXES: " + specification);
			try {
				int low = Integer.parseInt(match.group(1));
				int high = match.group(2) == null ? low : Integer.parseInt(match.group(2));
				if (low > high)
					throw new IllegalArgumentException("SAP HANA NUMA ranges must be ascending: " + part);
				nodes.add(match.group(2) == null ? "'" + low + "'" : "'" + low + "' TO '" + high + "'");
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("SAP HANA NUMA node index is too large: " + part, e);
			}
		}
		return String.join(", ", nodes);
	}

	private static void group(AbstractPartition<?> partition, String key, String keyword, SapHanaSqlBuilder builder) {
		String value = partition.getSpecifics().get(key);
		if (value != null && !value.isEmpty())
			builder._add(" GROUP ")._add(keyword)._add(" '")._add(value.replace("'", "''"))._add("'");
	}

	private static Boolean flag(AbstractPartition<?> partition, String name) {
		String value = text(partition.getSpecifics().get(name));
		if (value == null)
			return null;
		return switch (value.toUpperCase(java.util.Locale.ROOT)) {
		case "TRUE", "ON" -> true;
		case "FALSE", "OFF" -> false;
		default -> throw new IllegalArgumentException("SAP HANA " + name + " must be TRUE/FALSE or ON/OFF: " + value);
		};
	}

	private static String text(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
