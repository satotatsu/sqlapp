/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.Oracle10g;
import com.sqlapp.data.db.dialect.oracle.Oracle12c;
import com.sqlapp.data.db.dialect.oracle.util.OracleIndexDefinition;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Table;

/**
 * Coordinated table/local-index maintenance plans. These generate SQL, never
 * execute it.
 */
final class OracleTablePartitionMaintenance {
	record Plan(List<String> statements, com.sqlapp.data.schemas.AbstractPartition<?> removed) {
	}

	private OracleTablePartitionMaintenance() {
	}

	static boolean iot(Table table) {
		return "INDEX".equals(table.getSpecifics().get("oracle.table.organization"));
	}

	private static boolean local(Index index) {
		return "LOCAL".equalsIgnoreCase(index.getSpecifics().get("oracle.index.locality"));
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new IllegalArgumentException(message);
	}

	static boolean same(Partition a, Partition b, Dialect dialect) {
		Partition left = a.clone(), right = b.clone();
		left.setSchemaName("SCHEMA");
		right.setSchemaName("SCHEMA");
		return left.diff(right).toDifference().getChangedProperties(dialect).isEmpty();
	}

	private static void shape(Partitioning a, Partitioning b, Dialect dialect) {
		require(a != null && b != null
				&& (a.getPartitioningType() == PartitioningType.Range
						|| a.getPartitioningType() == PartitioningType.List
						|| a.getPartitioningType() == PartitioningType.Hash)
				&& b.getPartitioningType() == a.getPartitioningType() && a.getSubPartitioningType() == null
				&& b.getSubPartitioningType() == null,
				"Partition maintenance requires simple RANGE, LIST or HASH partitioning");
		Partitioning left = a.clone(), right = b.clone();
		left.getPartitions().clear();
		right.getPartitions().clear();
		left.setPartitionSize(0);
		right.setPartitionSize(0);
		require(left.diff(right).toDifference().getChangedProperties(dialect).isEmpty(),
				"Partition keys and defaults must remain unchanged");
	}

	private static void isolated(Table a, Table b, Dialect dialect) {
		require(a.getTableType() != Table.TableType.Temporary,
				"Temporary tables do not support range partition maintenance");
		require(dialect instanceof Oracle10g, "Table/index partition maintenance requires Oracle 10g or later");
		if (iot(a))
			require(dialect instanceof Oracle12c, "IOT partition maintenance requires Oracle 12c or later");
		for (String property : a.diff(b).toDifference().getChangedProperties(dialect).keySet())
			require(Set.of(SchemaObjectProperties.PARTITIONING.getLabel(), SchemaObjectProperties.INDEXES.getLabel())
					.contains(property),
					"Partition maintenance must be isolated from other table changes: " + property);
		shape(a.getPartitioning(), b.getPartitioning(), dialect);
		if (iot(a))
			require(a.getPartitioning().getPartitioningType() == PartitioningType.Range,
					"IOT maintenance requires RANGE partitioning");
		require(a.getIndexes().size() == b.getIndexes().size(),
				"Index additions/removals must be separate from partition maintenance");
	}

	static void ordinary(Index index) {
		OracleRebuildIndexFactory.validateOrdinaryIndex(index);
		require(!index.getSpecifics().containsKey(OracleIndexDefinition.DEFINITION)
				&& !index.getSpecifics().containsKey(OracleIndexDefinition.REQUIRED)
				&& !index.getSpecifics().containsKey("oracle.index.unmodeledType")
				&& !"true".equalsIgnoreCase(index.getSpecifics().get("oracle.index.join")),
				"Special indexes require their dedicated maintenance plan");
	}

	private static void indexShape(Index a, Index b, Dialect dialect) {
		ordinary(a);
		for (String property : a.diff(b).toDifference().getChangedProperties(dialect).keySet())
			require(SchemaObjectProperties.PARTITIONING.getLabel().equals(property),
					"Index attributes must remain unchanged");
		shape(a.getPartitioning(), b.getPartitioning(), dialect);
	}

	private static boolean prefix(Partitioning a, Partitioning b, int count, Dialect dialect) {
		for (int i = 0; i < count; i++)
			if (!same(a.getPartitions().get(i), b.getPartitions().get(i), dialect))
				return false;
		return true;
	}

	private static int removed(Partitioning a, Partitioning b, Dialect dialect) {
		if (a.getPartitions().size() != b.getPartitions().size() + 1 || b.getPartitions().isEmpty())
			return -1;
		for (int position = 0; position < a.getPartitions().size(); position++) {
			boolean match = true;
			for (int i = 0; i < b.getPartitions().size(); i++)
				if (!same(a.getPartitions().get(i < position ? i : i + 1), b.getPartitions().get(i), dialect)) {
					match = false;
					break;
				}
			if (match)
				return position;
		}
		return -1;
	}

	static Plan create(Table before, Table after, Dialect dialect, boolean decorate, boolean allowAdd,
			boolean allowDrop, Supplier<OracleSqlBuilder> builders) {
		if (before.getPartitioning() != null && before.getPartitioning().getSubPartitioningType() != null)
			return OracleCompositePartitionMaintenance.create(before, after, dialect, decorate, allowAdd, allowDrop,
					builders);
		isolated(before, after, dialect);
		Partitioning oldInfo = before.getPartitioning(), info = after.getPartitioning();
		int oldCount = oldInfo.getPartitions().size(), newCount = info.getPartitions().size();
		boolean hash = oldInfo.getPartitioningType() == PartitioningType.Hash;
		if (hash) {
			require(!before.isCompression(), "HASH maintenance requires an uncompressed table");
			for (Partition part : oldInfo.getPartitions())
				newPartition(part, false, dialect, true);
		}
		if (newCount == oldCount + 1 && prefix(oldInfo, info, oldCount, dialect)) {
			require(allowAdd, "Table options prohibit adding partitions");
			return append(before, after, dialect, decorate, builders);
		}
		int position = removed(oldInfo, info, dialect);
		if (position >= 0) {
			require(allowDrop, "Table options prohibit dropping/coalescing partitions");
			if (hash)
				require(position == oldCount - 1, "HASH COALESCE removes only the last partition");
			return drop(before, after, position, dialect, decorate, builders);
		}
		if (hash)
			throw new IllegalArgumentException("HASH maintenance requires one appended or last coalesced partition");
		if (iot(before)) {
			if (oldCount == newCount)
				return moveIot(before, after, dialect, decorate, builders);
			return splitOrMergeIot(before, after, dialect, decorate, builders);
		}
		return null; // Existing heap SPLIT/MERGE planner owns the remaining supported shapes.
	}

	private static void newPartition(Partition part, boolean index, Dialect dialect) {
		newPartition(part, index, dialect, false);
	}

	private static void newPartition(Partition part, boolean index, Dialect dialect, boolean hash) {
		Partition expected = new Partition(part.getName()).setHighValue(part.getHighValue())
				.setTableSpaceName(part.getTableSpaceName());
		if (index)
			expected.getSpecifics().put("oracle.index.status", "USABLE");
		require(part.getName() != null && !part.getName().isBlank()
				&& (hash ? part.getHighValue() == null : part.getHighValue() != null && !part.getHighValue().isBlank()),
				"Partition requires a name and a boundary only for RANGE/LIST");
		require(same(expected, part, dialect),
				"New partition supports only boundary, tablespace and explicitly USABLE index state");
	}

	private static OracleSqlBuilder table(Table before, boolean decorate, Supplier<OracleSqlBuilder> builders) {
		return builders.get().alter().table().space().name(before, decorate).space();
	}

	private static Plan append(Table before, Table after, Dialect dialect, boolean decorate,
			Supplier<OracleSqlBuilder> builders) {
		int position = before.getPartitioning().getPartitions().size();
		boolean hash = before.getPartitioning().getPartitioningType() == PartitioningType.Hash;
		require(position > 0 && !before.isCompression(), "Appending requires an existing uncompressed range table");
		String oldBound = before.getPartitioning().getPartitions().get(position - 1).getHighValue();
		require(hash || oldBound != null && !oldBound.isBlank(),
				"Existing highest range partition boundary is required");
		boolean list = before.getPartitioning().getPartitioningType() == PartitioningType.List;
		if (list) {
			for (Partition existing : before.getPartitioning().getPartitions())
				require(existing.getHighValue() != null && !"DEFAULT".equalsIgnoreCase(existing.getHighValue().trim()),
						"Cannot add a LIST partition with an existing DEFAULT partition");
		} else if (!hash)
			require(!oldBound.toUpperCase(java.util.Locale.ROOT).contains("MAXVALUE"),
					"Cannot append after MAXVALUE; split that partition instead");
		Partition added = after.getPartitioning().getPartitions().get(position);
		newPartition(added, false, dialect, hash);
		List<String> statements = new ArrayList<>();
		OracleSqlBuilder sql = table(before, decorate, builders);
		sql._add("ADD PARTITION ").name(added.getName());
		if (!hash)
			sql._add(list ? " VALUES (" : " VALUES LESS THAN (")._add(added.getHighValue())._add(")");
		if (added.getTableSpaceName() != null)
			sql._add(" TABLESPACE ").name(added.getTableSpaceName());
		if (hash)
			sql._add(" UPDATE INDEXES");
		statements.add(sql.toString());
		for (Index index : before.getIndexes()) {
			Index target = after.getIndexes().get(index.getName());
			require(target != null, "Index identity must remain unchanged");
			ordinary(index);
			if (!local(index)) {
				require(index.diff(target).toDifference().getChangedProperties(dialect).isEmpty(),
						"Global indexes must remain unchanged");
				continue;
			}
			indexShape(index, target, dialect);
			if (hash)
				hashIndex(index, dialect);
			require(!index.isCompression() && index.getPartitioning().getPartitions().size() == position
					&& target.getPartitioning().getPartitions().size() == position + 1
					&& prefix(index.getPartitioning(), target.getPartitioning(), position, dialect),
					"Local index partitions must match the appended table partition");
			Partition segment = target.getPartitioning().getPartitions().get(position);
			newPartition(segment, true, dialect, hash);
			require(Objects.equals(segment.getHighValue(), added.getHighValue()),
					"Local index boundary must match its table partition");
			require(index.getPartitioning().getPartitions().get(added.getName()) == null,
					"Automatic local index partition name would collide; choose another table partition name");
			if (!added.getName().equals(segment.getName())) {
				OracleSqlBuilder rename = builders.get();
				rename.alter().index().space().name(index, decorate)._add(" RENAME PARTITION ").name(added.getName())
						._add(" TO ").name(segment.getName());
				statements.add(rename.toString());
			}
			if (segment.getTableSpaceName() != null) {
				OracleSqlBuilder rebuild = builders.get();
				rebuild.alter().index().space().name(index, decorate)._add(" REBUILD PARTITION ")
						.name(segment.getName())._add(" TABLESPACE ").name(segment.getTableSpaceName());
				statements.add(rebuild.toString());
			} // Without an override Oracle inherits the index's default tablespace.
		}
		return new Plan(statements, null);
	}

	private static Plan drop(Table before, Table after, int position, Dialect dialect, boolean decorate,
			Supplier<OracleSqlBuilder> builders) {
		boolean hash = before.getPartitioning().getPartitioningType() == PartitioningType.Hash;
		for (Index index : before.getIndexes()) {
			Index target = after.getIndexes().get(index.getName());
			require(target != null, "Index identity must remain unchanged");
			ordinary(index);
			if (!local(index)) {
				require(index.diff(target).toDifference().getChangedProperties(dialect).isEmpty(),
						"Global indexes must remain unchanged");
				continue;
			}
			indexShape(index, target, dialect);
			if (hash)
				hashIndex(index, dialect);
			require(index.getPartitioning().getPartitions().size() == before.getPartitioning().getPartitions().size()
					&& removed(index.getPartitioning(), target.getPartitioning(), dialect) == position,
					"Remove exactly the corresponding local index partition");
		}
		Partition deleted = before.getPartitioning().getPartitions().get(position);
		if (hash)
			return new Plan(
					List.of(table(before, decorate, builders)._add("COALESCE PARTITION UPDATE INDEXES").toString()),
					null);
		OracleSqlBuilder sql = table(before, decorate, builders);
		sql._add("DROP PARTITION ").name(deleted.getName())
				._add(iot(before) ? " UPDATE GLOBAL INDEXES" : " UPDATE INDEXES");
		return new Plan(List.of(sql.toString()), deleted);
	}

	private static void hashIndex(Index index, Dialect dialect) {
		require(index.getPartitioning().getPartitioningType() == PartitioningType.Hash && !index.isCompression(),
				"HASH table requires an uncompressed HASH Local index");
		for (Partition part : index.getPartitioning().getPartitions())
			newPartition(part, true, dialect, true);
	}

	private static Plan splitOrMergeIot(Table before, Table after, Dialect dialect, boolean decorate,
			Supplier<OracleSqlBuilder> builders) {
		for (Index index : before.getIndexes()) {
			ordinary(index);
			Index target = after.getIndexes().get(index.getName());
			require(target != null, "Index identity must remain unchanged");
			if (local(index))
				indexShape(index, target, dialect);
			else
				require(index.diff(target).toDifference().getChangedProperties(dialect).isEmpty(),
						"Global secondary indexes must remain unchanged");
		}
		boolean merge = before.getPartitioning().getPartitions()
				.size() == after.getPartitioning().getPartitions().size() + 1;
		Table single = merge ? after : before, pair = merge ? before : after;
		Partitioning one = single.getPartitioning(), two = pair.getPartitioning();
		require(two.getPartitions().size() == one.getPartitions().size() + 1,
				"IOT maintenance requires one range SPLIT or two-partition MERGE");
		int position = 0;
		while (position < one.getPartitions().size()
				&& same(one.getPartitions().get(position), two.getPartitions().get(position), dialect))
			position++;
		require(position < one.getPartitions().size(), "No partition to split/merge");
		Partition source = one.getPartitions().get(position), left = two.getPartitions().get(position),
				right = two.getPartitions().get(position + 1);
		require(source.getHighValue() != null && !source.getHighValue().isBlank(),
				"Original IOT partition upper boundary is required");
		require(left.getHighValue() != null && !left.getHighValue().isBlank()
				&& !left.getHighValue().toUpperCase(java.util.Locale.ROOT).contains("MAXVALUE")
				&& !Objects.equals(left.getHighValue(), source.getHighValue())
				&& Objects.equals(right.getHighValue(), source.getHighValue()),
				"Split/merge must preserve the upper boundary");
		for (Partition part : List.of(left, right)) {
			Partition sourceCopy = source.clone(), copy = part.clone();
			for (Partition normalized : List.of(sourceCopy, copy)) {
				normalized.setName("PARTITION");
				normalized.setHighValue("BOUNDARY");
				normalized.setTableSpaceName("TABLESPACE");
			}
			require(same(sourceCopy, copy, dialect), "IOT split/merge cannot change other partition attributes");
		}
		for (int i = position + 1; i < one.getPartitions().size(); i++)
			require(same(one.getPartitions().get(i), two.getPartitions().get(i + 1), dialect),
					"Unaffected partitions must remain unchanged");
		OracleSqlBuilder sql = table(before, decorate, builders);
		if (merge) {
			sql._add("MERGE PARTITIONS ").name(left.getName()).comma().name(right.getName())._add(" INTO ");
			partition(source, sql);
		} else {
			sql._add("SPLIT PARTITION ").name(source.getName())._add(" AT (")._add(left.getHighValue())
					._add(") INTO (");
			partition(left, sql);
			sql.comma();
			partition(right, sql);
			sql._add(")");
		}
		List<String> statements = new ArrayList<>();
		statements.add(sql.toString());
		String maintenance = OracleIotLocalIndexMaintenance.create(before, after, position, merge, dialect, builders);
		if (maintenance != null)
			statements.add(maintenance);
		return new Plan(statements, null);
	}

	private static Plan moveIot(Table before, Table after, Dialect dialect, boolean decorate,
			Supplier<OracleSqlBuilder> builders) {
		for (Index index : before.getIndexes()) {
			ordinary(index);
			Index target = after.getIndexes().get(index.getName());
			require(target != null && index.diff(target).toDifference().getChangedProperties(dialect).isEmpty(),
					"IOT partition MOVE requires unchanged secondary indexes");
		}
		Partition moved = null;
		for (int i = 0; i < before.getPartitioning().getPartitions().size(); i++) {
			Partition oldPart = before.getPartitioning().getPartitions().get(i),
					part = after.getPartitioning().getPartitions().get(i);
			Partition left = oldPart.clone(), right = part.clone();
			left.setSchemaName("SCHEMA");
			right.setSchemaName("SCHEMA");
			var changes = left.diff(right).toDifference().getChangedProperties(dialect);
			if (changes.isEmpty())
				continue;
			require(moved == null && changes.size() == 1
					&& changes.containsKey(SchemaProperties.TABLE_SPACE_NAME.getLabel())
					&& part.getTableSpaceName() != null && !part.getTableSpaceName().isBlank(),
					"IOT MOVE supports one isolated partition tablespace change");
			moved = part;
		}
		require(moved != null, "No IOT partition tablespace change to apply");
		OracleSqlBuilder sql = table(before, decorate, builders);
		sql._add("MOVE PARTITION ").name(moved.getName())._add(" TABLESPACE ").name(moved.getTableSpaceName());
		return new Plan(List.of(sql.toString()), null);
	}

	private static void partition(Partition part, OracleSqlBuilder sql) {
		require(part.getName() != null && !part.getName().isBlank(), "Partition name is required");
		sql._add("PARTITION ").name(part.getName());
		if (part.getTableSpaceName() != null)
			sql._add(" TABLESPACE ").name(part.getTableSpaceName());
	}
}
