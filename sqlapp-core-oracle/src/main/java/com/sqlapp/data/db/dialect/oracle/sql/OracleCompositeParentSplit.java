/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.List;
import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.*;

/**
 * RANGE/LIST-HASH parent split/merge with fixed child routing and explicit
 * segment restoration.
 */
final class OracleCompositeParentSplit {
	private OracleCompositeParentSplit() {
	}

	private static void require(boolean valid, String message) {
		if (!valid)
			throw new IllegalArgumentException(message);
	}

	static OracleTablePartitionMaintenance.Plan create(Table before, Table after, Dialect dialect, boolean decorate,
			Supplier<OracleSqlBuilder> builders) {
		require((before.getPartitioning().getPartitioningType() == PartitioningType.Range
				|| before.getPartitioning().getPartitioningType() == PartitioningType.List)
				&& before.getPartitioning().getSubPartitioningType() == PartitioningType.Hash
				&& !before.isCompression(),
				"Composite parent SPLIT/MERGE currently requires uncompressed RANGE/LIST-HASH");
		boolean merge = before.getPartitioning().getPartitions()
				.size() == after.getPartitioning().getPartitions().size() + 1;
		Table small = merge ? after : before, large = merge ? before : after;
		require(large.getPartitioning().getPartitions().size() == small.getPartitioning().getPartitions().size() + 1,
				"Split/merge exactly two adjacent composite parents");
		OracleSqlBuilder sql = builders.get();
		Table oldProjection = project(before), projection = project(after);
		OracleLocalPartitionSplit.add(oldProjection, projection, dialect, decorate, sql);
		int position = 0;
		while (position < small.getPartitioning().getPartitions().size()
				&& OracleTablePartitionMaintenance.same(oldProjection.getPartitioning().getPartitions().get(position),
						projection.getPartitioning().getPartitions().get(position), dialect))
			position++;
		require(position < small.getPartitioning().getPartitions().size(), "No parent split/merge found");
		children(small.getPartitioning().getPartitions().get(position),
				large.getPartitioning().getPartitions().get(position), dialect, false);
		children(small.getPartitioning().getPartitions().get(position),
				large.getPartitioning().getPartitions().get(position + 1), dialect, false);
		for (Index index : before.getIndexes()) {
			OracleTablePartitionMaintenance.ordinary(index);
			if (!"LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")))
				continue;
			require(!index.isCompression() && !after.getIndexes().get(index.getName()).isCompression(),
					"Composite parent maintenance requires uncompressed Local indexes");
			Index a = small.getIndexes().get(index.getName()), b = large.getIndexes().get(index.getName());
			children(a.getPartitioning().getPartitions().get(position),
					b.getPartitioning().getPartitions().get(position), dialect, true);
			children(a.getPartitioning().getPartitions().get(position),
					b.getPartitioning().getPartitions().get(position + 1), dialect, true);
			checkShape(before.getPartitioning(), index.getPartitioning());
			checkShape(after.getPartitioning(), after.getIndexes().get(index.getName()).getPartitioning());
		}
		for (Table table : List.of(before, after)) {
			unique(table.getPartitioning());
			for (Index index : table.getIndexes())
				if ("LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")))
					unique(index.getPartitioning());
		}
		// Child metadata of unaffected parents must not disappear in the projection.
		for (int i = 0; i < small.getPartitioning().getPartitions().size(); i++) {
			if (i == position)
				continue;
			int j = i < position ? i : i + 1;
			require(OracleTablePartitionMaintenance.same(small.getPartitioning().getPartitions().get(i),
					large.getPartitioning().getPartitions().get(j), dialect),
					"Unaffected table children must remain unchanged");
			for (Index index : small.getIndexes())
				if ("LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")))
					require(OracleTablePartitionMaintenance.same(index.getPartitioning().getPartitions().get(i),
							large.getIndexes().get(index.getName()).getPartitioning().getPartitions().get(j), dialect),
							"Unaffected index children must remain unchanged");
		}
		return new OracleTablePartitionMaintenance.Plan(List.of(sql.toString(),
				OracleCompositeSegmentNames.create(after, position, merge ? 1 : 2, dialect, builders)), null);
	}

	private static void checkShape(Partitioning table, Partitioning index) {
		require(index != null && index.getPartitioningType() == table.getPartitioningType()
				&& index.getSubPartitioningType() == PartitioningType.Hash
				&& index.getPartitions().size() == table.getPartitions().size(),
				"Local composite shape must match its table");
		for (int i = 0; i < table.getPartitions().size(); i++) {
			require(java.util.Objects.equals(table.getPartitions().get(i).getHighValue(),
					index.getPartitions().get(i).getHighValue()), "Local parent boundaries must match their table");
			require(index.getPartitions().get(i).getSubPartitions().size() == table.getPartitions().get(i)
					.getSubPartitions().size(), "Local child counts must match its table");
		}
	}

	private static void children(Partition source, Partition target, Dialect dialect, boolean index) {
		require(!source.isCompression() && !target.isCompression() && !source.getSubPartitions().isEmpty()
				&& source.getSubPartitions().size() == target.getSubPartitions().size(),
				"Parent split/merge must preserve each HASH child count");
		for (int i = 0; i < source.getSubPartitions().size(); i++) {
			SubPartition a = source.getSubPartitions().get(i), b = target.getSubPartitions().get(i), copy = b.clone();
			copy.setName(a.getName());
			copy.setSchemaName(a.getSchemaName());
			SubPartition observed = a.clone();
			observed.getStatistics().clear();
			copy.getStatistics().clear();
			var changes = observed.diff(copy).toDifference().getChangedProperties(dialect);
			boolean uncompressed = index ? "DISABLED".equals(OracleIndexPartitionCompression.mode(a, "DISABLED"))
					: !a.isCompression() && a.getCompressionType() == null;
			require(a.getHighValue() == null && b.getHighValue() == null && uncompressed && changes.isEmpty(),
					"Preserve HASH child attributes and routing; only names may change: " + changes.keySet());
			if (index)
				require("USABLE".equals(a.getSpecifics().get("oracle.index.status")),
						"Affected Local children must be explicitly USABLE");
		}
	}

	private static void unique(Partitioning info) {
		var parents = new java.util.HashSet<String>();
		var children = new java.util.HashSet<String>();
		for (Partition parent : info.getPartitions()) {
			require(parent.getName() != null && !parent.getName().isBlank()
					&& parents.add(parent.getName().toUpperCase(java.util.Locale.ROOT)), "Parent names must be unique");
			for (SubPartition child : parent.getSubPartitions())
				require(child.getName() != null && !child.getName().isBlank()
						&& children.add(child.getName().toUpperCase(java.util.Locale.ROOT)),
						"Child names must be unique throughout table/index");
		}
	}

	private static Table project(Table table) {
		Table copy = table.clone();
		strip(copy.getPartitioning(), false);
		for (Index index : copy.getIndexes())
			if ("LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")))
				strip(index.getPartitioning(), true);
		return copy;
	}

	private static void strip(Partitioning info, boolean index) {
		require(info != null, "Local composite partitioning is required");
		info.setSubPartitioningType((PartitioningType) null);
		info.setSubPartitionSize(null);
		info.getSubPartitioningColumns().clear();
		for (Partition parent : info.getPartitions()) {
			parent.getStatistics().clear(); // Observed row/block counts are not DDL attributes.
			parent.getSubPartitions().clear();
			if (index) {
				String state = parent.getSpecifics().get("oracle.index.status");
				require(state == null || "N/A".equals(state) || "USABLE".equals(state),
						"Composite index parents cannot be UNUSABLE");
				parent.getSpecifics().put("oracle.index.status", "USABLE");
			}
		}
	}
}
