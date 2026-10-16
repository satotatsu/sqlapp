/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.Oracle10g;
import com.sqlapp.data.db.dialect.oracle.Oracle12c;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.*;

/** Isolated composite partition removal; no database access or row-discarding HASH DROP. */
final class OracleCompositePartitionMaintenance {
	private OracleCompositePartitionMaintenance() { }
	private static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
	private static boolean same(AbstractPartition<?> a, AbstractPartition<?> b, Dialect dialect) {
		if (a instanceof Partition left && b instanceof Partition right) return sameTyped(left, right, dialect);
		if (a instanceof SubPartition left && b instanceof SubPartition right) return sameTyped(left, right, dialect);
		return false;
	}
	private static <T extends AbstractPartition<T>> boolean sameTyped(T a, T b, Dialect dialect) {
		var left = a.clone(); var right = b.clone(); left.setSchemaName("SCHEMA"); right.setSchemaName("SCHEMA");
		return left.diff(right).toDifference().getChangedProperties(dialect).isEmpty();
	}
	private static int removed(List<? extends AbstractPartition<?>> before, List<? extends AbstractPartition<?>> after, Dialect dialect) {
		if (before.size() != after.size() + 1 || after.isEmpty()) return -1;
		for (int position = 0; position < before.size(); position++) {
			boolean match = true;
			for (int i = 0; i < after.size(); i++) if (!same(before.get(i < position ? i : i + 1), after.get(i), dialect)) { match = false; break; }
			if (match) return position;
		}
		return -1;
	}
	private static void shape(Partitioning a, Partitioning b, Dialect dialect) {
		require(a != null && b != null && a.getPartitioningType() != null && a.getSubPartitioningType() != null
				&& Set.of(PartitioningType.Range, PartitioningType.List, PartitioningType.Hash).contains(a.getPartitioningType())
				&& Set.of(PartitioningType.Range, PartitioningType.List, PartitioningType.Hash).contains(a.getSubPartitioningType()), "Composite maintenance requires RANGE/LIST/HASH partition types");
		var left = a.clone(); var right = b.clone(); left.getPartitions().clear(); right.getPartitions().clear();
		left.setPartitionSize(0); right.setPartitionSize(0);
		require(left.diff(right).toDifference().getChangedProperties(dialect).isEmpty(), "Composite keys, types, templates and defaults must remain unchanged");
	}
	private static void parent(Partition a, Partition b, Dialect dialect) {
		Partition left = a.clone(), right = b.clone(); left.getSubPartitions().clear(); right.getSubPartitions().clear();
		require(same(left, right, dialect), "Parent partition attributes must remain unchanged");
	}
	static OracleTablePartitionMaintenance.Plan create(Table before, Table after, Dialect dialect, boolean decorate, boolean allowDrop, Supplier<OracleSqlBuilder> builders) {
		require(dialect instanceof Oracle10g && !OracleTablePartitionMaintenance.iot(before)
				&& before.getTableType() != Table.TableType.Temporary, "Composite maintenance requires a supported heap table");
		Partitioning oldInfo = before.getPartitioning(), info = after.getPartitioning(); shape(oldInfo, info, dialect);
		if (oldInfo.getPartitioningType() != PartitioningType.Range || oldInfo.getSubPartitioningType() == PartitioningType.Range)
			require(dialect instanceof Oracle12c, "This composite partition combination requires Oracle 12c or later in this planner");
		for (String property : before.diff(after).toDifference().getChangedProperties(dialect).keySet())
			require(Set.of(SchemaObjectProperties.PARTITIONING.getLabel(), SchemaObjectProperties.INDEXES.getLabel()).contains(property), "Composite maintenance must be isolated: " + property);
		require(allowDrop, "Table options prohibit dropping/coalescing partitions");
		require(before.getIndexes().size() == after.getIndexes().size(), "Index additions/removals must be separate");
		int root = removed(oldInfo.getPartitions(), info.getPartitions(), dialect), parentPosition = -1, child = -1;
		if (root < 0) {
			require(oldInfo.getPartitions().size() == info.getPartitions().size(), "Composite maintenance requires one removed partition or subpartition");
			for (int i = 0; i < oldInfo.getPartitions().size(); i++) {
				Partition a = oldInfo.getPartitions().get(i), b = info.getPartitions().get(i);
				if (same(a, b, dialect)) continue;
				require(parentPosition < 0, "Only one parent partition may change"); parent(a, b, dialect);
				child = removed(a.getSubPartitions(), b.getSubPartitions(), dialect);
				require(child >= 0, "Remove exactly one subpartition and preserve all survivors"); parentPosition = i;
			}
			require(parentPosition >= 0, "No composite partition removal found");
		}
		boolean coalesce = root >= 0 ? oldInfo.getPartitioningType() == PartitioningType.Hash : oldInfo.getSubPartitioningType() == PartitioningType.Hash;
		if (coalesce) require(root >= 0 ? root == oldInfo.getPartitions().size() - 1
				: child == oldInfo.getPartitions().get(parentPosition).getSubPartitions().size() - 1, "HASH COALESCE removes only the last partition/subpartition");
		for (Index index : before.getIndexes()) {
			OracleTablePartitionMaintenance.ordinary(index); Index target = after.getIndexes().get(index.getName());
			require(target != null, "Index identity must remain unchanged");
			if (!"LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality"))) {
				require(index.diff(target).toDifference().getChangedProperties(dialect).isEmpty(), "Global indexes must remain unchanged"); continue;
			}
			for (String property : index.diff(target).toDifference().getChangedProperties(dialect).keySet())
				require(SchemaObjectProperties.PARTITIONING.getLabel().equals(property), "Local index attributes must remain unchanged");
			Partitioning a = index.getPartitioning(), b = target.getPartitioning(); shape(a, b, dialect);
			require(a.getPartitioningType() == oldInfo.getPartitioningType() && a.getSubPartitioningType() == oldInfo.getSubPartitioningType()
					&& a.getPartitions().size() == oldInfo.getPartitions().size() && b.getPartitions().size() == info.getPartitions().size(), "Local index composite shape must match its table");
			for (int i = 0; i < a.getPartitions().size(); i++) require(a.getPartitions().get(i).getSubPartitions().size() == oldInfo.getPartitions().get(i).getSubPartitions().size(), "Local subpartition counts must match the table");
			if (root >= 0) require(removed(a.getPartitions(), b.getPartitions(), dialect) == root, "Remove the corresponding Local index partition");
			else for (int i = 0; i < a.getPartitions().size(); i++) {
				Partition left = a.getPartitions().get(i), right = b.getPartitions().get(i);
				if (i == parentPosition) { parent(left, right, dialect); require(removed(left.getSubPartitions(), right.getSubPartitions(), dialect) == child, "Remove the corresponding Local index subpartition"); }
				else require(same(left, right, dialect), "Unaffected Local index partitions must remain unchanged");
			}
			if (coalesce) for (Partition part : a.getPartitions()) for (SubPartition sub : part.getSubPartitions())
				require("USABLE".equals(sub.getSpecifics().get("oracle.index.status")), "HASH COALESCE requires explicitly USABLE Local subpartitions");
		}
		OracleSqlBuilder sql = builders.get().alter().table().space().name(before, decorate).space();
		AbstractPartition<?> deleted = root >= 0 ? oldInfo.getPartitions().get(root) : oldInfo.getPartitions().get(parentPosition).getSubPartitions().get(child);
		require(deleted.getName() != null && !deleted.getName().isBlank(), "Removed partition/subpartition must have a name");
		if (coalesce) {
			if (root >= 0) sql._add("COALESCE PARTITION");
			else sql._add("MODIFY PARTITION ").name(oldInfo.getPartitions().get(parentPosition).getName())._add(" COALESCE SUBPARTITION");
		} else {
			if (root < 0) {
				long matches = oldInfo.getPartitions().stream().flatMap(part -> part.getSubPartitions().stream()).filter(part -> deleted.getName().equalsIgnoreCase(part.getName())).count();
				require(matches == 1, "Subpartition name must resolve uniquely within the table");
			}
			sql._add(root >= 0 ? "DROP PARTITION " : "DROP SUBPARTITION ").name(deleted.getName());
		}
		sql._add(" UPDATE INDEXES");
		return new OracleTablePartitionMaintenance.Plan(List.of(sql.toString()), coalesce ? null : deleted);
	}
}
