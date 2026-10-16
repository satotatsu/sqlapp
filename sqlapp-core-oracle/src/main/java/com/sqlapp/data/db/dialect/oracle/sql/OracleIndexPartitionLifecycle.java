/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.AbstractPartition;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Partitioning;

/** State transitions of physical index segments, with unchanged structure. */
final class OracleIndexPartitionLifecycle {
	private OracleIndexPartitionLifecycle() {
	}

	private static final String STATUS = OracleAlterIndexFactory.STATUS;

	record Change(String name, boolean subpartition, boolean rebuild, AbstractPartition<?> target, boolean tablespace,
			boolean compression, boolean modifyCompression, boolean localHash) {
	}

	static void addCreateState(AbstractPartition<?> partition, boolean compositeParent, OracleSqlBuilder builder) {
		String status = partition.getSpecifics().get(STATUS);
		if (status == null || "USABLE".equals(status))
			return;
		if ("N/A".equals(status) && compositeParent)
			return;
		if ("UNUSABLE".equals(status) && !compositeParent)
			builder.space()._add("UNUSABLE");
		else
			throw new IllegalArgumentException(
					"Unsupported index partition status: " + partition.getName() + "=" + status);
	}

	static boolean changed(Index before, Index after) {
		return !states(before.getPartitioning()).equals(states(after.getPartitioning()));
	}

	private static Map<List<String>, List<Object>> states(Partitioning info) {
		Map<List<String>, List<Object>> result = new HashMap<>();
		if (info == null)
			return result;
		for (var partition : info.getPartitions()) {
			result.put(List.of(partition.getName()), attributes(partition));
			for (var sub : partition.getSubPartitions()) {
				result.put(List.of(partition.getName(), sub.getName()), attributes(sub));
			}
		}
		return result;
	}

	private static List<Object> attributes(AbstractPartition<?> partition) {
		return java.util.Arrays.asList(partition.getSpecifics().get(STATUS), partition.getTableSpaceName(),
				partition.isCompression(), partition.getCompressionType(),
				partition.getSpecifics().get("oracle.index.compression"));
	}

	private static void clearAttributes(AbstractPartition<?> partition) {
		partition.getSpecifics().remove(STATUS);
		partition.getSpecifics().remove("oracle.index.compression");
		partition.setTableSpaceName(null);
		partition.setCompression(false);
		partition.setCompressionType(null);
	}

	private static Partitioning normalized(Partitioning info) {
		Partitioning copy = info.clone();
		for (var partition : copy.getPartitions()) {
			clearAttributes(partition);
			for (var sub : partition.getSubPartitions())
				clearAttributes(sub);
		}
		return copy;
	}

	static boolean structureChanged(Index before, Index after, Dialect dialect) {
		return before.getPartitioning() != null && after.getPartitioning() != null
				&& !normalized(before.getPartitioning()).diff(normalized(after.getPartitioning())).toDifference()
						.getChangedProperties(dialect).isEmpty();
	}

	static void validateRecreation(Index before, Index after) {
		if ("LOCAL".equalsIgnoreCase(before.getSpecifics().get("oracle.index.locality"))
				|| "LOCAL".equalsIgnoreCase(after.getSpecifics().get("oracle.index.locality")))
			throw new IllegalArgumentException(
					"Local index partition structure follows the base table; change its table partitioning instead");
		for (var original : before.getPartitioning().getPartitions()) {
			var target = after.getPartitioning().getPartitions().get(original.getName());
			if (target != null && !attributes(original).equals(attributes(target)))
				throw new IllegalArgumentException(
						"Partition structure and physical/state changes must be separate operations");
		}
		if (before.getTable() != null)
			for (var constraint : before.getTable().getConstraints())
				if (constraint instanceof com.sqlapp.data.schemas.UniqueConstraint unique
						&& Objects.equals(unique.getIndexName(), before.getName()))
					throw new IllegalArgumentException(
							"Cannot recreate an index backing a primary/unique constraint: " + before.getName());
	}

	static List<Change> changes(Index before, Index after, Dialect dialect) {
		Partitioning oldInfo = before.getPartitioning(), newInfo = after.getPartitioning();
		if (oldInfo == null || newInfo == null || !normalized(oldInfo).diff(normalized(newInfo)).toDifference()
				.getChangedProperties(dialect).isEmpty())
			throw new IllegalArgumentException(
					"Index partition state changes require unchanged partition structure and attributes");
		List<Change> result = new ArrayList<>();
		for (var target : newInfo.getPartitions()) {
			var original = oldInfo.getPartitions().get(target.getName());
			if (!Objects.equals(original.getSpecifics().get(STATUS), target.getSpecifics().get(STATUS))) {
				if (!target.getSubPartitions().isEmpty() || newInfo.getSubPartitioningType() != null)
					throw new IllegalArgumentException(
							"Change composite index subpartitions individually: " + target.getName());
			}
			String oldMode = OracleIndexPartitionCompression.mode(original,
					OracleIndexPartitionCompression.rootMode(before));
			String newMode = OracleIndexPartitionCompression.mode(target,
					OracleIndexPartitionCompression.rootMode(after));
			boolean composite = !target.getSubPartitions().isEmpty() || newInfo.getSubPartitioningType() != null;
			if (composite) {
				if (!Objects.equals(original.getTableSpaceName(), target.getTableSpaceName()))
					throw new IllegalArgumentException(
							"Move physical subpartitions individually instead of their composite parent");
				boolean compression = !oldMode.equals(newMode);
				if (compression) {
					String root = OracleIndexPartitionCompression.rootMode(before);
					if ("DISABLED".equals(root) || "ENABLED".equals(newMode) && !"ENABLED".equals(root)
							|| newMode.startsWith("ADVANCED") && !root.startsWith("ADVANCED"))
						throw new IllegalArgumentException(
								"Composite partition compression must use the existing index compression family");
					result.add(new Change(target.getName(), false, false, target, false, true, true, false));
				}
				for (var sub : target.getSubPartitions()) {
					if (!newMode.equals(OracleIndexPartitionCompression.mode(sub, newMode)))
						throw new IllegalArgumentException(
								"Subpartition compression must match its parent: " + sub.getName());
					add(result, original.getSubPartitions().get(sub.getName()), sub, true, before, oldMode, newMode,
							compression);
				}
			} else
				add(result, original, target, false, before, oldMode, newMode, false);
		}
		return result;
	}

	private static void add(List<Change> result, AbstractPartition<?> before, AbstractPartition<?> after, boolean sub,
			Index index, String oldMode, String newMode, boolean parentCompressionChanged) {
		String original = before.getSpecifics().get(STATUS), target = after.getSpecifics().get(STATUS);
		boolean tablespace = !Objects.equals(before.getTableSpaceName(), after.getTableSpaceName());
		boolean compression = !oldMode.equals(newMode);
		if (sub && !parentCompressionChanged && !OracleIndexPartitionCompression.mode(before, oldMode)
				.equals(OracleIndexPartitionCompression.mode(after, newMode)))
			throw new IllegalArgumentException(
					"Change compression on the parent partition, not an individual subpartition");
		boolean localHash = "LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")) && (sub
				? index.getPartitioning().getSubPartitioningType()
				: index.getPartitioning().getPartitioningType()) == com.sqlapp.data.schemas.PartitioningType.Hash;
		if (!sub && compression && localHash)
			throw new IllegalArgumentException("Local hash partition rebuild supports only TABLESPACE options");
		boolean physical = tablespace || compression || parentCompressionChanged;
		if (physical) {
			if (!"USABLE".equals(target))
				throw new IllegalArgumentException("Physical rebuild requires an explicit USABLE target segment");
			if (original != null && !"USABLE".equals(original) && !"UNUSABLE".equals(original))
				throw new IllegalArgumentException("Unsupported source segment status: " + original);
			result.add(
					new Change(after.getName(), sub, true, after, tablespace, compression && !sub, false, localHash));
			return;
		}
		if (Objects.equals(original, target))
			return;
		if ("UNUSABLE".equals(target) && (original == null || "USABLE".equals(original)))
			result.add(new Change(after.getName(), sub, false, after, false, false, false, localHash));
		else if ("USABLE".equals(target) && "UNUSABLE".equals(original))
			result.add(new Change(after.getName(), sub, true, after, false, false, false, localHash));
		else
			throw new IllegalArgumentException(
					"Unsupported index partition status transition: " + original + " -> " + target);
	}
}
