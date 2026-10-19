/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import static com.sqlapp.data.db.dialect.oracle.sql.OracleCompositeSegmentNames.identifier;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.Oracle10g;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.*;

/**
 * Isolated composite partition maintenance; no database access or
 * row-discarding HASH DROP.
 */
final class OracleCompositePartitionMaintenance {
	private OracleCompositePartitionMaintenance() {
	}

	private static void require(boolean valid, String message) {
		if (!valid)
			throw new IllegalArgumentException(message);
	}

	private static boolean same(AbstractPartition<?> a, AbstractPartition<?> b, Dialect dialect) {
		if (a instanceof Partition left && b instanceof Partition right)
			return sameTyped(left, right, dialect);
		if (a instanceof SubPartition left && b instanceof SubPartition right)
			return sameTyped(left, right, dialect);
		return false;
	}

	private static <T extends AbstractPartition<T>> boolean sameTyped(T a, T b, Dialect dialect) {
		var left = a.clone();
		var right = b.clone();
		left.setSchemaName("SCHEMA");
		right.setSchemaName("SCHEMA");
		return left.diff(right).toDifference().getChangedProperties(dialect).isEmpty();
	}

	private static int removed(List<? extends AbstractPartition<?>> before, List<? extends AbstractPartition<?>> after,
			Dialect dialect) {
		if (before.size() != after.size() + 1 || after.isEmpty())
			return -1;
		for (int position = 0; position < before.size(); position++) {
			boolean match = true;
			for (int i = 0; i < after.size(); i++)
				if (!same(before.get(i < position ? i : i + 1), after.get(i), dialect)) {
					match = false;
					break;
				}
			if (match)
				return position;
		}
		return -1;
	}

	private static void shape(Partitioning a, Partitioning b, Dialect dialect) {
		require(a != null && b != null && a.getPartitioningType() != null && a.getSubPartitioningType() != null
				&& Set.of(PartitioningType.Range, PartitioningType.List, PartitioningType.Hash)
						.contains(a.getPartitioningType())
				&& Set.of(PartitioningType.Range, PartitioningType.List, PartitioningType.Hash)
						.contains(a.getSubPartitioningType()),
				"Composite maintenance requires RANGE/LIST/HASH partition types");
		var left = a.clone();
		var right = b.clone();
		left.getPartitions().clear();
		right.getPartitions().clear();
		left.setPartitionSize(0);
		right.setPartitionSize(0);
		require(left.diff(right).toDifference().getChangedProperties(dialect).isEmpty(),
				"Composite keys, types, templates and defaults must remain unchanged");
	}

	private static void parent(Partition a, Partition b, Dialect dialect) {
		Partition left = a.clone(), right = b.clone();
		left.getSubPartitions().clear();
		right.getSubPartitions().clear();
		require(same(left, right, dialect), "Parent partition attributes must remain unchanged");
	}

	static OracleTablePartitionMaintenance.Plan create(Table before, Table after, Dialect dialect, boolean decorate,
			boolean allowAdd, boolean allowDrop, Supplier<OracleSqlBuilder> builders) {
		require(dialect instanceof Oracle10g && !OracleTablePartitionMaintenance.iot(before)
				&& before.getTableType() != Table.TableType.Temporary,
				"Composite maintenance requires a supported heap table");
		Partitioning oldInfo = before.getPartitioning(), info = after.getPartitioning();
		shape(oldInfo, info, dialect);
		OraclePartitionVersions.validate(oldInfo.getPartitioningType(), oldInfo.getSubPartitioningType(), dialect);
		for (String property : before.diff(after).toDifference().getChangedProperties(dialect).keySet())
			require(Set.of(SchemaObjectProperties.PARTITIONING.getLabel(), SchemaObjectProperties.INDEXES.getLabel())
					.contains(property), "Composite maintenance must be isolated: " + property);
		require(before.getIndexes().size() == after.getIndexes().size(), "Index additions/removals must be separate");
		if (oldInfo.getPartitions().size() != info.getPartitions().size()
				&& removed(oldInfo.getPartitions(), info.getPartitions(), dialect) < 0) {
			boolean append = info.getPartitions().size() == oldInfo.getPartitions().size() + 1;
			for (int i = 0; append && i < oldInfo.getPartitions().size(); i++)
				append = same(oldInfo.getPartitions().get(i), info.getPartitions().get(i), dialect);
			if (!append)
				return OracleCompositeParentSplit.create(before, after, dialect, decorate, builders);
		}
		if (oldInfo.getPartitions().size() == info.getPartitions().size()) {
			var positions = new java.util.ArrayList<Integer>();
			for (int i = 0; i < info.getPartitions().size(); i++)
				if (!same(oldInfo.getPartitions().get(i), info.getPartitions().get(i), dialect))
					positions.add(i);
			if (positions.size() > 1) {
				for (Index index : before.getIndexes())
					require(after.getIndexes().get(index.getName()) != null
							&& index.diff(after.getIndexes().get(index.getName())).toDifference()
									.getChangedProperties(dialect).isEmpty(),
							"Grouped MOVE requires unchanged indexes");
				var statements = new java.util.ArrayList<String>();
				for (int position : positions) {
					require(oldInfo.getPartitions().get(position).getSubPartitions().size() == info.getPartitions()
							.get(position).getSubPartitions().size(),
							"Multiple parents support only child MOVE/compression changes");
					Table step = before.clone();
					step.getPartitioning().getPartitions().set(position, info.getPartitions().get(position).clone());
					var plan = changeChild(before, step, dialect, decorate, builders);
					require(plan != null && plan.removed() == null, "Grouped child MOVE is required");
					statements.addAll(plan.statements());
				}
				return new OracleTablePartitionMaintenance.Plan(statements, null);
			}
		}
		var childPlan = changeChild(before, after, dialect, decorate, builders);
		if (childPlan != null)
			return childPlan;
		if (info.getPartitions().size() == oldInfo.getPartitions().size() + 1) {
			require(allowAdd, "Table options prohibit adding partitions");
			return appendParent(before, after, dialect, decorate, builders);
		}
		if (oldInfo.getPartitions().size() == info.getPartitions().size())
			for (int i = 0; i < oldInfo.getPartitions().size(); i++)
				if (info.getPartitions().get(i).getSubPartitions()
						.size() == oldInfo.getPartitions().get(i).getSubPartitions().size() + 1) {
					require(allowAdd, "Table options prohibit adding subpartitions");
					return appendSubpartition(before, after, i, dialect, decorate, builders);
				}
		require(allowDrop, "Table options prohibit dropping/coalescing partitions");
		int root = removed(oldInfo.getPartitions(), info.getPartitions(), dialect), parentPosition = -1, child = -1;
		if (root < 0) {
			require(oldInfo.getPartitions().size() == info.getPartitions().size(),
					"Composite maintenance requires one removed partition or subpartition");
			for (int i = 0; i < oldInfo.getPartitions().size(); i++) {
				Partition a = oldInfo.getPartitions().get(i), b = info.getPartitions().get(i);
				if (same(a, b, dialect))
					continue;
				require(parentPosition < 0, "Only one parent partition may change");
				parent(a, b, dialect);
				child = removed(a.getSubPartitions(), b.getSubPartitions(), dialect);
				require(child >= 0, "Remove exactly one subpartition and preserve all survivors");
				parentPosition = i;
			}
			require(parentPosition >= 0, "No composite partition removal found");
		}
		boolean coalesce = root >= 0 ? oldInfo.getPartitioningType() == PartitioningType.Hash
				: oldInfo.getSubPartitioningType() == PartitioningType.Hash;
		if (coalesce)
			require(root >= 0 ? root == oldInfo.getPartitions().size() - 1
					: child == oldInfo.getPartitions().get(parentPosition).getSubPartitions().size() - 1,
					"HASH COALESCE removes only the last partition/subpartition");
		for (Index index : before.getIndexes()) {
			OracleTablePartitionMaintenance.ordinary(index);
			Index target = after.getIndexes().get(index.getName());
			require(target != null, "Index identity must remain unchanged");
			if (!"LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality"))) {
				require(index.diff(target).toDifference().getChangedProperties(dialect).isEmpty(),
						"Global indexes must remain unchanged");
				continue;
			}
			for (String property : index.diff(target).toDifference().getChangedProperties(dialect).keySet())
				require(SchemaObjectProperties.PARTITIONING.getLabel().equals(property),
						"Local index attributes must remain unchanged");
			Partitioning a = index.getPartitioning(), b = target.getPartitioning();
			shape(a, b, dialect);
			require(a.getPartitioningType() == oldInfo.getPartitioningType()
					&& a.getSubPartitioningType() == oldInfo.getSubPartitioningType()
					&& a.getPartitions().size() == oldInfo.getPartitions().size()
					&& b.getPartitions().size() == info.getPartitions().size(),
					"Local index composite shape must match its table");
			for (int i = 0; i < a.getPartitions().size(); i++)
				require(a.getPartitions().get(i).getSubPartitions().size() == oldInfo.getPartitions().get(i)
						.getSubPartitions().size(), "Local subpartition counts must match the table");
			if (root >= 0)
				require(removed(a.getPartitions(), b.getPartitions(), dialect) == root,
						"Remove the corresponding Local index partition");
			else
				for (int i = 0; i < a.getPartitions().size(); i++) {
					Partition left = a.getPartitions().get(i), right = b.getPartitions().get(i);
					if (i == parentPosition) {
						parent(left, right, dialect);
						require(removed(left.getSubPartitions(), right.getSubPartitions(), dialect) == child,
								"Remove the corresponding Local index subpartition");
					} else
						require(same(left, right, dialect), "Unaffected Local index partitions must remain unchanged");
				}
			if (coalesce)
				for (Partition part : a.getPartitions())
					for (SubPartition sub : part.getSubPartitions())
						require("USABLE".equals(sub.getSpecifics().get("oracle.index.status")),
								"HASH COALESCE requires explicitly USABLE Local subpartitions");
		}
		OracleSqlBuilder sql = builders.get().alter().table().space().name(before, decorate).space();
		AbstractPartition<?> deleted = root >= 0 ? oldInfo.getPartitions().get(root)
				: oldInfo.getPartitions().get(parentPosition).getSubPartitions().get(child);
		require(deleted.getName() != null && !deleted.getName().isBlank(),
				"Removed partition/subpartition must have a name");
		if (coalesce) {
			if (root >= 0)
				sql._add("COALESCE PARTITION");
			else
				sql._add("MODIFY PARTITION ").name(oldInfo.getPartitions().get(parentPosition).getName())
						._add(" COALESCE SUBPARTITION");
		} else {
			if (root < 0) {
				long matches = oldInfo.getPartitions().stream().flatMap(part -> part.getSubPartitions().stream())
						.filter(part -> identifier(deleted.getName(), dialect, builders.get())
								.equals(identifier(part.getName(), dialect, builders.get())))
						.count();
				require(matches == 1, "Subpartition name must resolve uniquely within the table");
			}
			sql._add(root >= 0 ? "DROP PARTITION " : "DROP SUBPARTITION ").name(deleted.getName());
		}
		sql._add(" UPDATE INDEXES");
		return new OracleTablePartitionMaintenance.Plan(List.of(sql.toString()), coalesce ? null : deleted);
	}

	private static OracleTablePartitionMaintenance.Plan changeChild(Table before, Table after, Dialect dialect,
			boolean decorate, Supplier<OracleSqlBuilder> builders) {
		var a = before.getPartitioning().getPartitions();
		var b = after.getPartitioning().getPartitions();
		if (a.size() != b.size())
			return null;
		int position = -1;
		for (int i = 0; i < a.size(); i++) {
			if (same(a.get(i), b.get(i), dialect))
				continue;
			require(position < 0, "Only one parent partition may change");
			parent(a.get(i), b.get(i), dialect);
			position = i;
		}
		if (position < 0)
			return null;
		var oldChildren = a.get(position).getSubPartitions();
		var children = b.get(position).getSubPartitions();
		require(!children.isEmpty(), "A parent must retain at least one subpartition");
		if (removed(oldChildren, children, dialect) >= 0)
			return null;
		boolean append = children.size() == oldChildren.size() + 1;
		for (int i = 0; append && i < oldChildren.size(); i++)
			append = same(oldChildren.get(i), children.get(i), dialect);
		if (append)
			return null;
		if (children.size() != oldChildren.size()) {
			String inheritedFree = a.get(position).getSpecifics().get("PCT_FREE");
			if (inheritedFree == null)
				inheritedFree = before.getSpecifics().getOrDefault("PCT_FREE", "10");
			for (var group : List.of(oldChildren, children))
				for (SubPartition child : group) {
					String mode = OracleTablePartitionCompression.clause(child, dialect);
					if ((" COMPRESS FOR OLTP".equals(mode) || " NOCOMPRESS".equals(mode)
							&& (before.isCompression() || a.get(position).isCompression()))
							&& child.getSpecifics().get("PCT_FREE") != null)
						require(inheritedFree.equals(child.getSpecifics().get("PCT_FREE")),
								"Child SPLIT/MERGE must inherit parent PCT_FREE unless BASIC compression forces zero");
				}
		}
		validateChildIndexes(before, after, position, dialect);
		uniqueChildren(after, dialect, builders.get());
		if (children.size() == oldChildren.size()) {
			var movedPositions = new java.util.ArrayList<Integer>();
			for (int i = 0; i < children.size(); i++) {
				SubPartition old = oldChildren.get(i), next = children.get(i);
				if (same(old, next, dialect))
					continue;
				SubPartition copy = next.clone();
				copy.setTableSpaceName(old.getTableSpaceName());
				copy.setCompression(old.isCompression());
				copy.setCompressionType(old.getCompressionType());
				String oldCompression = OracleTablePartitionCompression.clause(old, dialect);
				String targetCompression = OracleTablePartitionCompression.clause(next, dialect);
				if (!oldCompression.equals(targetCompression) || old.isCompression() || next.isCompression()
						|| before.isCompression() || a.get(position).isCompression()) {
					String pctFree = next.getSpecifics().get("PCT_FREE");
					String required = " COMPRESS".equals(targetCompression) ? "0" : old.getSpecifics().get("PCT_FREE");
					require(pctFree == null || java.util.Objects.equals(required, pctFree),
							"Compression changes require PCT_FREE=" + required
									+ "; non-BASIC moves retain source PCT_FREE and Oracle cannot modify it individually");
					copy.getSpecifics().remove("PCT_FREE");
					if (old.getSpecifics().containsKey("PCT_FREE"))
						copy.getSpecifics().put("PCT_FREE", old.getSpecifics().get("PCT_FREE"));
				}
				copy.getSpecifics().remove("COMPRESS_FOR");
				if (old.getSpecifics().containsKey("COMPRESS_FOR"))
					copy.getSpecifics().put("COMPRESS_FOR", old.getSpecifics().get("COMPRESS_FOR"));
				require(same(old, copy, dialect) && (next.getTableSpaceName() == null ? old.getTableSpaceName() == null
						: !next.getTableSpaceName().isBlank()),
						"Subpartition MOVE requires isolated tablespace or compression changes");
				OracleTablePartitionCompression.clause(next, dialect);
				movedPositions.add(i);
			}
			require(!movedPositions.isEmpty(), "No subpartition MOVE found");
			for (Index index : before.getIndexes()) {
				require(index.diff(after.getIndexes().get(index.getName())).toDifference().getChangedProperties(dialect)
						.isEmpty(), "Subpartition MOVE requires unchanged indexes");
				if ("LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")))
					for (int movedPosition : movedPositions)
						require("USABLE".equals(index.getPartitioning().getPartitions().get(position).getSubPartitions()
								.get(movedPosition).getSpecifics().get("oracle.index.status")),
								"Subpartition MOVE requires explicitly USABLE affected Local segments");
			}
			var statements = new java.util.ArrayList<String>();
			for (int movedPosition : movedPositions) {
				SubPartition moved = children.get(movedPosition);
				OracleSqlBuilder sql = builders.get().alter().table().space().name(before, decorate)
						._add(" MOVE SUBPARTITION ").name(moved.getName());
				if (moved.getTableSpaceName() != null)
					sql._add(" TABLESPACE ").name(moved.getTableSpaceName());
				SubPartition old = oldChildren.get(movedPosition);
				if (before.isCompression() || a.get(position).isCompression() || old.isCompression()
						|| moved.isCompression()
						|| !java.util.Objects.equals(old.getCompressionType(), moved.getCompressionType())
						|| old.getSpecifics().containsKey("COMPRESS_FOR")
						|| moved.getSpecifics().containsKey("COMPRESS_FOR")) {
					require(dialect instanceof com.sqlapp.data.db.dialect.oracle.Oracle11gR2,
							"Explicit subpartition compression clauses require Oracle11gR2+");
					sql._add(OracleTablePartitionCompression.clause(moved, dialect));
				}
				sql._add(" UPDATE INDEXES");
				statements.add(sql.toString());
			}
			return new OracleTablePartitionMaintenance.Plan(statements, null);
		}
		require(before.getPartitioning().getSubPartitioningType() != PartitioningType.Hash,
				"HASH subpartitions support ADD/COALESCE/MOVE, not SPLIT/MERGE");
		OracleSqlBuilder sql = builders.get();
		OracleLocalPartitionSplit.add(flatten(before, position), flatten(after, position), dialect, decorate, sql,
				true);
		return new OracleTablePartitionMaintenance.Plan(List.of(sql.toString()), null);
	}

	private static void uniqueChildren(Table table, Dialect dialect, OracleSqlBuilder naming) {
		uniqueChildren(table.getPartitioning(), dialect, naming);
		for (Index index : table.getIndexes())
			if ("LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")))
				uniqueChildren(index.getPartitioning(), dialect, naming);
	}

	private static void uniqueChildren(Partitioning info, Dialect dialect, OracleSqlBuilder naming) {
		var names = new java.util.HashSet<String>();
		for (Partition parent : info.getPartitions())
			for (SubPartition child : parent.getSubPartitions())
				require(child.getName() != null && !child.getName().isBlank()
						&& names.add(identifier(child.getName(), dialect, naming)),
						"Subpartition names must be unique throughout their table/index");
	}

	private static void validateChildIndexes(Table before, Table after, int position, Dialect dialect) {
		for (Index index : before.getIndexes()) {
			OracleTablePartitionMaintenance.ordinary(index);
			Index next = after.getIndexes().get(index.getName());
			require(next != null, "Index identity must remain unchanged");
			if (!"LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality"))) {
				require(index.diff(next).toDifference().getChangedProperties(dialect).isEmpty(),
						"Global indexes must remain unchanged");
				continue;
			}
			for (String property : index.diff(next).toDifference().getChangedProperties(dialect).keySet())
				require(SchemaObjectProperties.PARTITIONING.getLabel().equals(property),
						"Local index attributes must remain unchanged");
			shape(index.getPartitioning(), next.getPartitioning(), dialect);
			localShape(before.getPartitioning(), index.getPartitioning());
			localShape(after.getPartitioning(), next.getPartitioning());
			for (int i = 0; i < index.getPartitioning().getPartitions().size(); i++) {
				Partition old = index.getPartitioning().getPartitions().get(i),
						target = next.getPartitioning().getPartitions().get(i);
				if (i == position)
					parent(old, target, dialect);
				else
					require(same(old, target, dialect), "Unaffected Local index parents must remain unchanged");
			}
		}
	}

	private static void localShape(Partitioning table, Partitioning index) {
		require(index.getPartitioningType() == table.getPartitioningType()
				&& index.getSubPartitioningType() == table.getSubPartitioningType()
				&& index.getPartitions().size() == table.getPartitions().size(),
				"Local composite shape must match its table");
		for (int i = 0; i < table.getPartitions().size(); i++) {
			var a = table.getPartitions().get(i).getSubPartitions();
			var b = index.getPartitions().get(i).getSubPartitions();
			require(a.size() == b.size(), "Local subpartition counts must match the table");
			for (int j = 0; j < a.size(); j++) {
				if (table.getSubPartitioningType() == PartitioningType.List)
					OracleListPartitionValues.same(a.get(j).getHighValue(), b.get(j).getHighValue());
				else
					require(java.util.Objects.equals(a.get(j).getHighValue(), b.get(j).getHighValue()),
							"Local subpartition boundaries must match the table");
			}
		}
	}

	/**
	 * Project one parent's canonical child models into the shared simple split
	 * validator.
	 */
	private static Table flatten(Table table, int position) {
		Table copy = table.clone();
		// Keep the effective compression default when replacing parents with leaves.
		copy.setCompression(
				table.isCompression() || table.getPartitioning().getPartitions().get(position).isCompression());
		flatten(copy.getPartitioning(), position);
		for (Index index : copy.getIndexes())
			if ("LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality")))
				flatten(index.getPartitioning(), position);
		return copy;
	}

	private static void flatten(Partitioning info, int position) {
		var columns = info.getSubPartitioningColumns().stream().map(column -> column.getName()).toList();
		var leaves = info.getPartitions().get(position).getSubPartitions().stream().map(child -> child.toPartition())
				.toList();
		info.setPartitioningType(info.getSubPartitioningType());
		info.setSubPartitioningType((PartitioningType) null);
		info.setPartitionSize(0);
		info.setSubPartitionSize(null);
		info.getPartitioningColumns().clear();
		info.getSubPartitioningColumns().clear();
		for (String column : columns)
			info.getPartitioningColumns().add(column);
		info.getPartitions().clear();
		info.getPartitions().addAll(leaves);
		for (Partition leaf : info.getPartitions())
			leaf.getStatistics().clear();
	}

	private static void simple(SubPartition part, boolean index, PartitioningType type, Dialect dialect) {
		require(part.getName() != null && !part.getName().isBlank(), "Added subpartition requires a name");
		require(type == PartitioningType.Hash ? part.getHighValue() == null
				: part.getHighValue() != null && !part.getHighValue().isBlank(),
				"Subpartition boundary must match its partitioning type");
		SubPartition expected = new SubPartition(part.getName()).setHighValue(part.getHighValue())
				.setTableSpaceName(part.getTableSpaceName());
		if (index) {
			expected.getSpecifics().put("oracle.index.status", "USABLE");
			copyIndexCompression(part, expected);
		} else
			copyTableCompression(part, expected, dialect);
		require(same(expected, part, dialect),
				"Added subpartition supports only name, boundary, tablespace, inherited index compression and explicitly USABLE Local state");
	}

	private static void simpleParent(Partition part, boolean index, Dialect dialect) {
		require(part.getName() != null && !part.getName().isBlank() && part.getHighValue() != null
				&& !part.getHighValue().isBlank(), "Added parent requires a name and boundary");
		Partition expected = new Partition(part.getName()).setHighValue(part.getHighValue())
				.setTableSpaceName(part.getTableSpaceName());
		if (index && part.getSpecifics().containsKey("oracle.index.status")) {
			String status = part.getSpecifics().get("oracle.index.status");
			require("USABLE".equals(status) || "N/A".equals(status), "Added composite index parent cannot be UNUSABLE");
			expected.getSpecifics().put("oracle.index.status", status);
		}
		Partition copy = part.clone();
		if (index)
			copyIndexCompression(part, expected);
		else
			copyTableCompression(part, expected, dialect);
		copy.getSubPartitions().clear();
		require(same(expected, copy, dialect),
				"Added parent supports only name, boundary, tablespace and inherited index compression");
	}

	private static void copyIndexCompression(AbstractPartition<?> source, AbstractPartition<?> target) {
		target.setCompression(source.isCompression());
		target.setCompressionType(source.getCompressionType());
		if (source.getSpecifics().containsKey("oracle.index.compression"))
			target.getSpecifics().put("oracle.index.compression",
					source.getSpecifics().get("oracle.index.compression"));
	}

	private static void copyTableCompression(AbstractPartition<?> source, AbstractPartition<?> target,
			Dialect dialect) {
		String clause = OracleTablePartitionCompression.clause(source, dialect);
		// BASIC compression itself establishes PCTFREE=0; no physical override is
		// needed.
		if (" COMPRESS".equals(clause) && "0".equals(source.getSpecifics().get("PCT_FREE")))
			target.getSpecifics().put("PCT_FREE", "0");
		target.setCompression(source.isCompression());
		target.setCompressionType(source.getCompressionType());
		if (source.getSpecifics().containsKey("COMPRESS_FOR"))
			target.getSpecifics().put("COMPRESS_FOR", source.getSpecifics().get("COMPRESS_FOR"));
	}

	private static void tableCompression(AbstractPartition<?> part, boolean compressedDefault, OracleSqlBuilder sql) {
		if (compressedDefault || part.isCompression() || part.getCompressionType() != null
				|| part.getSpecifics().containsKey("COMPRESS_FOR"))
			sql._add(OracleTablePartitionCompression.clause(part, sql.getDialect()));
	}

	private static void inheritedIndexCompression(Index index, Partition parent, Dialect dialect) {
		OracleIndexCompression.add(index, dialect, new OracleSqlBuilder(dialect));
		String root = OracleIndexPartitionCompression.rootMode(index);
		require(root.equals(OracleIndexPartitionCompression.mode(parent, root)),
				"Added Local segments must inherit index compression");
		for (SubPartition child : parent.getSubPartitions())
			OracleIndexPartitionCompression.validateSubpartition(index, parent, child);
	}

	private static void subpartition(SubPartition part, PartitioningType type, boolean index, OracleSqlBuilder sql) {
		sql._add("SUBPARTITION ").name(part.getName());
		if (!index && type != PartitioningType.Hash)
			sql._add(type == PartitioningType.List ? " VALUES (" : " VALUES LESS THAN (")._add(part.getHighValue())
					._add(")");
		if (part.getTableSpaceName() != null)
			sql._add(" TABLESPACE ").name(part.getTableSpaceName());
	}

	private static OracleTablePartitionMaintenance.Plan appendParent(Table before, Table after, Dialect dialect,
			boolean decorate, Supplier<OracleSqlBuilder> builders) {
		Partitioning oldInfo = before.getPartitioning(), info = after.getPartitioning();
		PartitioningType type = oldInfo.getPartitioningType(), sub = oldInfo.getSubPartitioningType();
		int position = oldInfo.getPartitions().size();
		require(position > 0 && type != PartitioningType.Hash,
				"Composite parent ADD requires an existing RANGE/LIST table");
		Partition added = info.getPartitions().get(position);
		simpleParent(added, false, dialect);
		require(!added.getSubPartitions().isEmpty(), "Composite parent ADD requires explicit subpartitions");
		for (int i = 0; i < position; i++)
			require(same(oldInfo.getPartitions().get(i), info.getPartitions().get(i), dialect),
					"Existing partitions must remain unchanged");
		if (type == PartitioningType.List)
			for (Partition part : oldInfo.getPartitions())
				require(!OracleListPartitionValues.isDefault(part.getHighValue()),
						"Cannot append after a DEFAULT parent");
		else {
			String bound = oldInfo.getPartitions().get(position - 1).getHighValue();
			require(bound != null && !bound.isBlank() && !OracleRangePartitionBound.isUnbounded(bound),
					"Cannot append after a missing boundary or MAXVALUE parent");
		}
		java.util.Set<String> names = new java.util.HashSet<>();
		for (Partition part : oldInfo.getPartitions())
			for (SubPartition child : part.getSubPartitions())
				if (child.getName() != null)
					names.add(identifier(child.getName(), dialect, builders.get()));
		for (SubPartition child : added.getSubPartitions()) {
			simple(child, false, sub, dialect);
			require(names.add(identifier(child.getName(), dialect, builders.get())),
					"New table subpartition names must be unique");
		}
		OracleSqlBuilder sql = builders.get().alter().table().space().name(before, decorate)._add(" ADD PARTITION ")
				.name(added.getName())._add(type == PartitioningType.List ? " VALUES (" : " VALUES LESS THAN (")
				._add(added.getHighValue())._add(")");
		if (added.getTableSpaceName() != null)
			sql._add(" TABLESPACE ").name(added.getTableSpaceName());
		tableCompression(added, before.isCompression(), sql);
		sql._add(" (");
		for (int i = 0; i < added.getSubPartitions().size(); i++) {
			if (i > 0)
				sql._add(", ");
			subpartition(added.getSubPartitions().get(i), sub, false, sql);
			tableCompression(added.getSubPartitions().get(i), added.isCompression(), sql);
		}
		sql._add(") UPDATE INDEXES");
		boolean first = true;
		for (Index index : before.getIndexes()) {
			OracleTablePartitionMaintenance.ordinary(index);
			Index next = after.getIndexes().get(index.getName());
			require(next != null, "Index identity must remain unchanged");
			if (!"LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality"))) {
				require(index.diff(next).toDifference().getChangedProperties(dialect).isEmpty(),
						"Global indexes must remain unchanged");
				continue;
			}
			for (String property : index.diff(next).toDifference().getChangedProperties(dialect).keySet())
				require(SchemaObjectProperties.PARTITIONING.getLabel().equals(property),
						"Local index attributes must remain unchanged");
			Partitioning a = index.getPartitioning(), b = next.getPartitioning();
			shape(a, b, dialect);
			require(a.getPartitioningType() == type && a.getSubPartitioningType() == sub
					&& a.getPartitions().size() == position && b.getPartitions().size() == position + 1,
					"Local composite shape must match its table");
			for (int i = 0; i < position; i++)
				require(same(a.getPartitions().get(i), b.getPartitions().get(i), dialect) && a.getPartitions().get(i)
						.getSubPartitions().size() == oldInfo.getPartitions().get(i).getSubPartitions().size(),
						"Existing Local segments must remain unchanged and match table counts");
			Partition segment = b.getPartitions().get(position);
			inheritedIndexCompression(index, segment, dialect);
			simpleParent(segment, true, dialect);
			require(java.util.Objects.equals(segment.getHighValue(), added.getHighValue())
					&& segment.getSubPartitions().size() == added.getSubPartitions().size(),
					"New Local parent and children must match the table");
			require(java.util.Objects.equals(index.getSchemaName(), before.getSchemaName()),
					"Cross-schema Local maintenance requires explicit owner handling");
			names.clear();
			for (Partition part : a.getPartitions())
				for (SubPartition child : part.getSubPartitions())
					if (child.getName() != null)
						names.add(identifier(child.getName(), dialect, builders.get()));
			sql._add(first ? " (" : ", ").name(index, false)._add(" (PARTITION ").name(segment.getName());
			first = false;
			if (segment.getTableSpaceName() != null)
				sql._add(" TABLESPACE ").name(segment.getTableSpaceName());
			sql._add(" (");
			for (int i = 0; i < segment.getSubPartitions().size(); i++) {
				SubPartition child = segment.getSubPartitions().get(i);
				simple(child, true, sub, dialect);
				require(java.util.Objects.equals(child.getHighValue(), added.getSubPartitions().get(i).getHighValue())
						&& names.add(identifier(child.getName(), dialect, builders.get())),
						"New Local subpartition boundaries/names must match without collisions");
				if (i > 0)
					sql._add(", ");
				subpartition(child, sub, true, sql);
			}
			sql._add("))");
		}
		if (!first)
			sql._add(")");
		return new OracleTablePartitionMaintenance.Plan(List.of(sql.toString()), null);
	}

	private static OracleTablePartitionMaintenance.Plan appendSubpartition(Table before, Table after, int position,
			Dialect dialect, boolean decorate, Supplier<OracleSqlBuilder> builders) {
		Partitioning oldInfo = before.getPartitioning(), info = after.getPartitioning();
		Partition source = oldInfo.getPartitions().get(position), target = info.getPartitions().get(position);
		PartitioningType type = oldInfo.getSubPartitioningType();
		require(oldInfo.getPartitioningType() == PartitioningType.Range
				|| oldInfo.getPartitioningType() == PartitioningType.List && type == PartitioningType.List,
				"Subpartition ADD supports RANGE-* and LIST-LIST in this planner");
		require(!source.getSubPartitions().isEmpty(), "Subpartition ADD requires an existing parent");
		int ordinal = source.getSubPartitions().size();
		SubPartition added = target.getSubPartitions().get(ordinal);
		simple(added, false, type, dialect);
		if (type == PartitioningType.List)
			for (SubPartition part : source.getSubPartitions())
				require(!OracleListPartitionValues.isDefault(part.getHighValue()),
						"Cannot append with an existing DEFAULT subpartition");
		if (type == PartitioningType.Range) {
			String boundary = source.getSubPartitions().get(ordinal - 1).getHighValue();
			require(boundary != null && !boundary.isBlank() && !OracleRangePartitionBound.isUnbounded(boundary),
					"Cannot append after a missing boundary or MAXVALUE");
		}
		long collisions = oldInfo.getPartitions().stream().flatMap(part -> part.getSubPartitions().stream())
				.filter(part -> identifier(added.getName(), dialect, builders.get())
						.equals(identifier(part.getName(), dialect, builders.get())))
				.count();
		require(collisions == 0, "Added subpartition name must be unique within its table");
		Table projected = after.clone();
		projected.getPartitioning().getPartitions().get(position).getSubPartitions().remove(ordinal);
		OracleSqlBuilder sql = builders.get().alter().table().space().name(before, decorate)._add(" MODIFY PARTITION ")
				.name(source.getName())._add(" ADD SUBPARTITION ").name(added.getName());
		if (type != PartitioningType.Hash)
			sql._add(type == PartitioningType.List ? " VALUES (" : " VALUES LESS THAN (")._add(added.getHighValue())
					._add(")");
		if (added.getTableSpaceName() != null)
			sql._add(" TABLESPACE ").name(added.getTableSpaceName());
		tableCompression(added, before.isCompression() || source.isCompression(), sql);
		sql._add(" UPDATE INDEXES");
		boolean first = true;
		for (Index index : before.getIndexes()) {
			OracleTablePartitionMaintenance.ordinary(index);
			Index next = after.getIndexes().get(index.getName());
			require(next != null, "Index identity must remain unchanged");
			if (!"LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality"))) {
				require(index.diff(next).toDifference().getChangedProperties(dialect).isEmpty(),
						"Global indexes must remain unchanged");
				continue;
			}
			for (String property : index.diff(next).toDifference().getChangedProperties(dialect).keySet())
				require(SchemaObjectProperties.PARTITIONING.getLabel().equals(property),
						"Local index attributes must remain unchanged");
			Partitioning a = index.getPartitioning(), b = next.getPartitioning();
			shape(a, b, dialect);
			require(a.getPartitioningType() == oldInfo.getPartitioningType() && a.getSubPartitioningType() == type
					&& a.getPartitions().size() == oldInfo.getPartitions().size()
					&& b.getPartitions().size() == info.getPartitions().size(),
					"Local composite shape must match its table");
			for (int i = 0; i < a.getPartitions().size(); i++)
				require(a.getPartitions().get(i).getSubPartitions().size() == oldInfo.getPartitions().get(i)
						.getSubPartitions().size(), "Local subpartition counts must match the table");
			require(b.getPartitions().get(position).getSubPartitions().size() == ordinal + 1,
					"Append exactly one corresponding Local subpartition");
			SubPartition segment = b.getPartitions().get(position).getSubPartitions().get(ordinal);
			inheritedIndexCompression(index, b.getPartitions().get(position), dialect);
			simple(segment, true, type, dialect);
			require(java.util.Objects.equals(added.getHighValue(), segment.getHighValue()),
					"Local subpartition boundary must match its table");
			require(java.util.Objects.equals(index.getSchemaName(), before.getSchemaName()),
					"Cross-schema Local maintenance requires explicit owner handling");
			require(a.getPartitions().stream().flatMap(part -> part.getSubPartitions().stream())
					.noneMatch(part -> identifier(segment.getName(), dialect, builders.get())
							.equals(identifier(part.getName(), dialect, builders.get()))),
					"Added Local subpartition name would collide");
			if (type == PartitioningType.Hash)
				for (SubPartition part : a.getPartitions().get(position).getSubPartitions())
					require("USABLE".equals(part.getSpecifics().get("oracle.index.status")),
							"HASH ADD requires explicitly USABLE existing Local segments");
			projected.getIndexes().get(index.getName()).getPartitioning().getPartitions().get(position)
					.getSubPartitions().remove(ordinal);
			for (int i = 0; i < a.getPartitions().size(); i++)
				require(same(a.getPartitions().get(i),
						projected.getIndexes().get(index.getName()).getPartitioning().getPartitions().get(i), dialect),
						"Existing Local segments must remain unchanged");
			sql._add(first ? " (" : ", ").name(index, false)._add(" (SUBPARTITION ").name(segment.getName());
			first = false;
			if (segment.getTableSpaceName() != null)
				sql._add(" TABLESPACE ").name(segment.getTableSpaceName());
			sql._add(")");
		}
		if (!first)
			sql._add(")");
		for (int i = 0; i < oldInfo.getPartitions().size(); i++)
			require(same(oldInfo.getPartitions().get(i), projected.getPartitioning().getPartitions().get(i), dialect),
					"Existing table subpartitions must remain unchanged");
		require(before.diff(projected).toDifference().getChangedProperties(dialect).isEmpty(),
				"Subpartition ADD must preserve all existing table and index metadata");
		return new OracleTablePartitionMaintenance.Plan(List.of(sql.toString()), null);
	}
}
