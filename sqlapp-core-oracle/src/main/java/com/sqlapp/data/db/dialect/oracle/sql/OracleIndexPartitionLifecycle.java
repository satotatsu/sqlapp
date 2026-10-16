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
	private OracleIndexPartitionLifecycle() { }
	private static final String STATUS = OracleAlterIndexFactory.STATUS;
	record Change(String name, boolean subpartition, boolean rebuild) { }

	static void addCreateState(AbstractPartition<?> partition, boolean compositeParent, OracleSqlBuilder builder) {
		String status = partition.getSpecifics().get(STATUS);
		if (status == null || "USABLE".equals(status)) return;
		if ("N/A".equals(status) && compositeParent) return;
		if ("UNUSABLE".equals(status) && !compositeParent) builder.space()._add("UNUSABLE");
		else throw new IllegalArgumentException("Unsupported index partition status: " + partition.getName() + "=" + status);
	}

	static boolean changed(Index before, Index after) {
		return !states(before.getPartitioning()).equals(states(after.getPartitioning()));
	}

	private static Map<List<String>, String> states(Partitioning info) {
		Map<List<String>, String> result = new HashMap<>();
		if (info == null) return result;
		for (var partition : info.getPartitions()) {
			String status = partition.getSpecifics().get(STATUS);
			if (status != null) result.put(List.of(partition.getName()), status);
			for (var sub : partition.getSubPartitions()) {
				status = sub.getSpecifics().get(STATUS);
				if (status != null) result.put(List.of(partition.getName(), sub.getName()), status);
			}
		}
		return result;
	}

	private static Partitioning normalized(Partitioning info) {
		Partitioning copy = info.clone();
		for (var partition : copy.getPartitions()) {
			partition.getSpecifics().remove(STATUS);
			for (var sub : partition.getSubPartitions()) sub.getSpecifics().remove(STATUS);
		}
		return copy;
	}

	static List<Change> changes(Index before, Index after, Dialect dialect) {
		Partitioning oldInfo = before.getPartitioning(), newInfo = after.getPartitioning();
		if (oldInfo == null || newInfo == null || !normalized(oldInfo).diff(normalized(newInfo))
				.toDifference().getChangedProperties(dialect).isEmpty())
			throw new IllegalArgumentException("Index partition state changes require unchanged partition structure and attributes");
		List<Change> result = new ArrayList<>();
		for (var target : newInfo.getPartitions()) {
			var original = oldInfo.getPartitions().get(target.getName());
			if (!Objects.equals(original.getSpecifics().get(STATUS), target.getSpecifics().get(STATUS))) {
				if (!target.getSubPartitions().isEmpty() || newInfo.getSubPartitioningType() != null)
					throw new IllegalArgumentException("Change composite index subpartitions individually: " + target.getName());
				add(result, original, target, false);
			}
			for (var sub : target.getSubPartitions()) add(result, original.getSubPartitions().get(sub.getName()), sub, true);
		}
		return result;
	}

	private static void add(List<Change> result, AbstractPartition<?> before, AbstractPartition<?> after, boolean sub) {
		String original = before.getSpecifics().get(STATUS), target = after.getSpecifics().get(STATUS);
		if (Objects.equals(original, target)) return;
		if ("UNUSABLE".equals(target) && (original == null || "USABLE".equals(original)))
			result.add(new Change(after.getName(), sub, false));
		else if ("USABLE".equals(target) && "UNUSABLE".equals(original))
			result.add(new Change(after.getName(), sub, true));
		else throw new IllegalArgumentException("Unsupported index partition status transition: " + original + " -> " + target);
	}
}
