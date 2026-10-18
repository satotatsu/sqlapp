/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.Objects;
import java.util.Set;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.Oracle10g;
import com.sqlapp.data.db.dialect.oracle.util.OracleIndexDefinition;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.Table;

/** A single range/list split with explicit, usable local index segments. */
final class OracleLocalPartitionSplit {
	private OracleLocalPartitionSplit() {
	}

	static boolean hasLocalIndexes(Table table) {
		return table.getIndexes().stream()
				.anyMatch(index -> "LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")));
	}

	private static Partition normalized(Partition partition) {
		Partition copy = partition.clone();
		copy.setName("PARTITION");
		copy.setSchemaName("SCHEMA");
		copy.setHighValue("BOUNDARY");
		copy.setTableSpaceName("TABLESPACE");
		return copy;
	}

	private static void same(Partition before, Partition after, Dialect dialect) {
		if (!before.diff(after).toDifference().getChangedProperties(dialect).isEmpty())
			throw new IllegalArgumentException("Unaffected partition attributes must remain unchanged: "
					+ before.getName() + " " + before.diff(after).toDifference().getChangedProperties(dialect));
	}

	static void add(Table before, Table after, Dialect dialect, boolean decorate, OracleSqlBuilder builder) {
		add(before, after, dialect, decorate, builder, false);
	}

	static void add(Table before, Table after, Dialect dialect, boolean decorate, OracleSqlBuilder builder,
			boolean subpartition) {
		if (before.getPartitioning() != null && after.getPartitioning() != null && before.getPartitioning()
				.getPartitions().size() == after.getPartitioning().getPartitions().size() + 1) {
			merge(before, after, dialect, decorate, builder, subpartition);
			return;
		}
		if (!(dialect instanceof Oracle10g) || "INDEX".equals(before.getSpecifics().get("oracle.table.organization")))
			throw new IllegalArgumentException("Local index range splitting requires a supported heap table dialect");
		var changes = before.diff(after).toDifference().getChangedProperties(dialect);
		for (String property : changes.keySet())
			if (!Set.of(SchemaObjectProperties.PARTITIONING.getLabel(), SchemaObjectProperties.INDEXES.getLabel())
					.contains(property))
				throw new IllegalArgumentException(
						"Table partition split must be isolated from other table changes: " + property);
		Partitioning oldInfo = before.getPartitioning(), newInfo = after.getPartitioning();
		if (oldInfo == null || newInfo == null
				|| (oldInfo.getPartitioningType() != PartitioningType.Range
						&& oldInfo.getPartitioningType() != PartitioningType.List)
				|| newInfo.getPartitioningType() != oldInfo.getPartitioningType()
				|| oldInfo.getSubPartitioningType() != null || newInfo.getSubPartitioningType() != null
				|| newInfo.getPartitions().size() != oldInfo.getPartitions().size() + 1)
			throw new IllegalArgumentException(
					"Local index maintenance requires one simple RANGE or LIST partition split");
		boolean list = oldInfo.getPartitioningType() == PartitioningType.List;
		if (list && oldInfo.getPartitioningColumns().size() != 1)
			throw new IllegalArgumentException("LIST split/merge requires one partition key column");
		Partitioning oldShape = oldInfo.clone(), newShape = newInfo.clone();
		oldShape.getPartitions().clear();
		newShape.getPartitions().clear();
		oldShape.setPartitionSize(0);
		newShape.setPartitionSize(0);
		if (!oldShape.diff(newShape).toDifference().getChangedProperties(dialect).isEmpty())
			throw new IllegalArgumentException("Partition keys and options must remain unchanged");
		int position = 0;
		while (position < oldInfo.getPartitions().size()
				&& oldInfo.getPartitions().get(position).equals(newInfo.getPartitions().get(position)))
			position++;
		if (position == oldInfo.getPartitions().size())
			throw new IllegalArgumentException(
					"Appending a partition is not a split; use an explicit table partition ADD plan");
		Partition original = oldInfo.getPartitions().get(position), left = newInfo.getPartitions().get(position),
				right = newInfo.getPartitions().get(position + 1);
		if (original.getHighValue() == null || original.getHighValue().isBlank())
			throw new IllegalArgumentException("Original partition upper boundary is required");
		if (list)
			OracleListPartitionValues.split(original.getHighValue(), left.getHighValue(), right.getHighValue());
		else if (left.getHighValue() == null || left.getHighValue().isBlank()
				|| "MAXVALUE".equalsIgnoreCase(left.getHighValue())
				|| Objects.equals(left.getHighValue(), original.getHighValue())
				|| !Objects.equals(right.getHighValue(), original.getHighValue()))
			throw new IllegalArgumentException("Split must preserve the original upper bound in its right partition");
		same(normalized(original), normalized(left), dialect);
		same(normalized(original), normalized(right), dialect);
		for (int i = position + 1; i < oldInfo.getPartitions().size(); i++)
			same(oldInfo.getPartitions().get(i), newInfo.getPartitions().get(i + 1), dialect);
		if (before.getIndexes().size() != after.getIndexes().size())
			throw new IllegalArgumentException("Index additions/removals must be separate from a split");
		builder.alter().table().space().name(before, decorate).space()
				._add(subpartition ? "SPLIT SUBPARTITION " : "SPLIT PARTITION ").name(original.getName()).space()
				._add(list ? "VALUES (" : "AT (")._add(left.getHighValue())._add(") INTO (");
		partition(left, builder, subpartition);
		builder.comma();
		partition(right, builder, subpartition);
		builder._add(") UPDATE INDEXES");
		boolean first = true;
		for (Index oldIndex : before.getIndexes()) {
			Index target = after.getIndexes().get(oldIndex.getName());
			if (target == null)
				throw new IllegalArgumentException("Index identity must remain unchanged");
			OracleRebuildIndexFactory.validateOrdinaryIndex(oldIndex);
			if (oldIndex.getSpecifics().containsKey(OracleIndexDefinition.DEFINITION)
					|| oldIndex.getSpecifics().containsKey(OracleIndexDefinition.REQUIRED)
					|| oldIndex.getSpecifics().containsKey("oracle.index.unmodeledType")
					|| "true".equalsIgnoreCase(oldIndex.getSpecifics().get("oracle.index.join")))
				throw new IllegalArgumentException(
						"Special index partition maintenance requires its dedicated planner");
			if (!"LOCAL".equalsIgnoreCase(oldIndex.getSpecifics().get("oracle.index.locality"))) {
				if (!oldIndex.diff(target).toDifference().getChangedProperties(dialect).isEmpty())
					throw new IllegalArgumentException("Global index changes must be separate from table splitting");
				continue;
			}
			for (String property : oldIndex.diff(target).toDifference().getChangedProperties(dialect).keySet())
				if (!SchemaObjectProperties.PARTITIONING.getLabel().equals(property))
					throw new IllegalArgumentException("Local index attributes must remain unchanged");
			Partitioning oldParts = oldIndex.getPartitioning(), parts = target.getPartitioning();
			if (oldParts == null || parts == null || oldParts.getPartitions().size() != oldInfo.getPartitions().size()
					|| parts.getPartitions().size() != newInfo.getPartitions().size()
					|| parts.getSubPartitioningType() != null)
				throw new IllegalArgumentException(
						"Local index partitions must correspond positionally to the table partitions");
			Partitioning oldIndexShape = oldParts.clone(), newIndexShape = parts.clone();
			oldIndexShape.getPartitions().clear();
			newIndexShape.getPartitions().clear();
			oldIndexShape.setPartitionSize(0);
			newIndexShape.setPartitionSize(0);
			if (!oldIndexShape.diff(newIndexShape).toDifference().getChangedProperties(dialect).isEmpty())
				throw new IllegalArgumentException("Local index partition keys and options must remain unchanged");
			for (int i = 0; i < position; i++)
				same(oldParts.getPartitions().get(i), parts.getPartitions().get(i), dialect);
			for (int i = position + 1; i < oldParts.getPartitions().size(); i++)
				same(oldParts.getPartitions().get(i), parts.getPartitions().get(i + 1), dialect);
			Partition source = oldParts.getPartitions().get(position), a = parts.getPartitions().get(position),
					b = parts.getPartitions().get(position + 1);
			if (list) {
				if (oldParts.getPartitioningType() != PartitioningType.List)
					throw new IllegalArgumentException("Local index partition type must match the LIST table");
				OracleListPartitionValues.same(original.getHighValue(), source.getHighValue());
				OracleListPartitionValues.same(left.getHighValue(), a.getHighValue());
				OracleListPartitionValues.same(right.getHighValue(), b.getHighValue());
			}
			for (Partition part : java.util.List.of(source, a, b)) {
				if (!"USABLE".equals(part.getSpecifics().get("oracle.index.status")) || part.isCompression()
						|| !part.getSubPartitions().isEmpty())
					throw new IllegalArgumentException(
							"Split local index segments must be simple, uncompressed and explicitly USABLE");
			}
			same(normalized(source), normalized(a), dialect);
			same(normalized(source), normalized(b), dialect);
			if (!Objects.equals(oldIndex.getSchemaName(), before.getSchemaName()))
				throw new IllegalArgumentException("Cross-schema local index split requires explicit owner handling");
			if (first)
				builder._add(" (");
			builder.comma(!first).name(oldIndex, false).space()._add("(");
			first = false;
			partition(a, builder, subpartition);
			builder.comma();
			partition(b, builder, subpartition);
			builder._add(")");
		}
		if (!first)
			builder._add(")");
	}

	private static void merge(Table before, Table after, Dialect dialect, boolean decorate, OracleSqlBuilder builder,
			boolean subpartition) {
		// A merge has the same boundary/storage invariants as its inverse split.
		add(after, before, dialect, decorate, new OracleSqlBuilder(dialect), subpartition);
		int position = 0;
		while (position < after.getPartitioning().getPartitions().size() && before.getPartitioning().getPartitions()
				.get(position).equals(after.getPartitioning().getPartitions().get(position)))
			position++;
		Partition first = before.getPartitioning().getPartitions().get(position),
				second = before.getPartitioning().getPartitions().get(position + 1);
		builder.alter().table().space().name(before, decorate).space()
				._add(subpartition ? "MERGE SUBPARTITIONS " : "MERGE PARTITIONS ").name(first.getName()).comma()
				.name(second.getName()).space()._add("INTO ");
		partition(after.getPartitioning().getPartitions().get(position), builder, subpartition);
		builder._add(" UPDATE INDEXES");
		boolean comma = false;
		for (Index index : after.getIndexes()) {
			if (!"LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")))
				continue;
			if (!comma)
				builder._add(" (");
			builder.comma(comma).name(index, false).space()._add("(");
			comma = true;
			partition(index.getPartitioning().getPartitions().get(position), builder, subpartition);
			builder._add(")");
		}
		if (comma)
			builder._add(")");
	}

	private static void partition(Partition partition, OracleSqlBuilder builder, boolean subpartition) {
		if (partition.getName() == null || partition.getName().isBlank())
			throw new IllegalArgumentException("Partition name is required");
		builder._add(subpartition ? "SUBPARTITION " : "PARTITION ").name(partition.getName());
		if (partition.getTableSpaceName() != null)
			builder.space()._add("TABLESPACE ").name(partition.getTableSpaceName());
	}
}
