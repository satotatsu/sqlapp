/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.sqlapp.data.db.dialect.oracle.Oracle11g;
import com.sqlapp.data.db.dialect.oracle.Oracle11gR2;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaObjectProperties;

/** Isolated index visibility and usability transitions. */
public class OracleAlterIndexFactory extends SimpleSqlFactory<Index, OracleSqlBuilder> {
	static final String VISIBILITY = "oracle.index.visibility";
	static final String STATUS = "oracle.index.status";
	static boolean hasStateChange(Index before, Index after) {
		return !Objects.equals(before.getName(), after.getName())
				|| !Objects.equals(before.getSpecifics().get(VISIBILITY), after.getSpecifics().get(VISIBILITY))
				|| !Objects.equals(before.getSpecifics().get(STATUS), after.getSpecifics().get(STATUS))
				|| OracleIndexPartitionLifecycle.changed(before, after);
	}

	@Override public List<SqlOperation> createSql(Index index) {
		throw new IllegalArgumentException("ALTER INDEX requires original and target models; use createDiffSql");
	}

	@Override public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Index before = difference.getOriginal(Index.class), after = difference.getTarget(Index.class);
		if (before == null || after == null) throw new IllegalArgumentException("ALTER INDEX requires original and target models");
		var changes = difference.toDifference().getChangedProperties(getDialect());
		for (String property : changes.keySet()) {
			if (!Set.of(SchemaProperties.SPECIFICS.getLabel(), SchemaObjectProperties.PARTITIONING.getLabel(), VISIBILITY, STATUS,
					SchemaProperties.NAME.getLabel(), SchemaProperties.TABLE_SPACE_NAME.getLabel(), SchemaProperties.COMPRESSION.getLabel(), SchemaProperties.COMPRESSION_TYPE.getLabel(), "oracle.index.prefixLength").contains(property))
				throw new IllegalArgumentException("Index state changes cannot be combined with structural changes: " + property);
		}
		var keys = new java.util.HashSet<>(before.getSpecifics().keySet()); keys.addAll(after.getSpecifics().keySet());
		for (String key : keys) {
			if (!Set.of(VISIBILITY, STATUS, "oracle.index.prefixLength").contains(key) && !Objects.equals(before.getSpecifics().get(key), after.getSpecifics().get(key)))
				throw new IllegalArgumentException("Unsupported simultaneous index property change: " + key);
		}
		List<SqlOperation> result = new ArrayList<>();
		boolean physical = OracleIndexRebuildOptions.physicalChange(before, after);
		if (!hasStateChange(before, after) && !physical) return result;
		OracleRebuildIndexFactory.validateOrdinaryIndex(before);
		if (physical || changes.containsKey(SchemaObjectProperties.PARTITIONING.getLabel())
				|| !Objects.equals(before.getSpecifics().get(STATUS), after.getSpecifics().get(STATUS)))
			OracleRebuildIndexFactory.validateNonTemporary(before);
		if (physical) {
			if (before.getPartitioning() != null) {
				if (OracleIndexRebuildOptions.compressionChange(before, after))
					throw new IllegalArgumentException("Change partition compression individually instead of rebuilding the entire index");
				OracleSqlBuilder builder = createSqlBuilder();
				builder.alter().index().space().name(before, getOptions().isDecorateSchemaName()).space()._add("MODIFY DEFAULT ATTRIBUTES");
				OracleIndexRebuildOptions.tablespace(after.getTableSpaceName(), builder);
				result.add(createOperation(builder.toString(), SqlType.ALTER, before, after));
			} else {
				OracleRebuildIndexFactory.validateRebuild(before);
				if ("UNUSABLE".equals(after.getSpecifics().get(STATUS)))
					throw new IllegalArgumentException("Physical rebuild requires a usable target index");
				result.add(rebuild(before, after));
			}
		}
		if (changes.containsKey(SchemaObjectProperties.PARTITIONING.getLabel())) {
			for (var change : OracleIndexPartitionLifecycle.changes(before, after, getDialect())) {
				if (change.rebuild()) OracleRebuildIndexFactory.validateNonTemporary(before);
				OracleSqlBuilder builder = createSqlBuilder();
				builder.alter().index().space().name(before, getOptions().isDecorateSchemaName()).space()
					._add(change.rebuild() ? "REBUILD " : "MODIFY ")
					._add(change.subpartition() ? "SUBPARTITION " : "PARTITION ").name(change.name());
				if (change.modifyCompression()) OracleIndexPartitionCompression.add(after, change.target(), getDialect(), builder);
				else if (!change.rebuild()) builder.space()._add("UNUSABLE");
				if (change.tablespace()) OracleIndexRebuildOptions.tablespace(change.target().getTableSpaceName(), builder);
				if (change.compression() && !change.modifyCompression()) OracleIndexPartitionCompression.add(after, change.target(), getDialect(), builder);
				if (change.rebuild()) OracleIndexRebuildOptions.online(before, getDialect(),
						getTableOptions().getOnlineIndex().test(after.getTable(), after), change.subpartition(), change.localHash(), builder);
				result.add(createOperation(builder.toString(), change.rebuild() ? SqlType.REBUILD : SqlType.ALTER, before, after));
			}
		}
		if (!Objects.equals(before.getSpecifics().get(STATUS), after.getSpecifics().get(STATUS))) {
			String oldStatus = before.getSpecifics().get(STATUS), newStatus = after.getSpecifics().get(STATUS);
			if (before.getPartitioning() != null) throw new IllegalArgumentException("Partitioned index state requires partition operations: " + before.getName());
			if ("UNUSABLE".equals(newStatus) && (oldStatus == null || "VALID".equals(oldStatus)))
				result.add(operation(before, after, "UNUSABLE", SqlType.ALTER));
			else if ("VALID".equals(newStatus) && ("UNUSABLE".equals(oldStatus) || physical && oldStatus == null)) {
				OracleRebuildIndexFactory.validateRebuild(before);
				if (!physical) result.add(rebuild(before, after));
			} else throw new IllegalArgumentException("Unsupported index status transition: " + oldStatus + " -> " + newStatus);
		}
		if (!Objects.equals(before.getSpecifics().get(VISIBILITY), after.getSpecifics().get(VISIBILITY))) {
			if (!(getDialect() instanceof Oracle11g) && !(getDialect() instanceof Oracle11gR2))
				throw new IllegalArgumentException("Index visibility requires Oracle 11g or later");
			String visibility = after.getSpecifics().get(VISIBILITY);
			if (!"VISIBLE".equals(visibility) && !"INVISIBLE".equals(visibility))
				throw new IllegalArgumentException("Target index visibility must be VISIBLE or INVISIBLE");
			result.add(operation(before, after, visibility, SqlType.ALTER));
		}
		if (!Objects.equals(before.getName(), after.getName())) {
			if (after.getName() == null || after.getName().isBlank()) throw new IllegalArgumentException("Target index name is required");
			OracleSqlBuilder builder = createSqlBuilder();
			builder.alter().index().space().name(before, getOptions().isDecorateSchemaName()).space()._add("RENAME TO").space().name(after, false);
			result.add(createOperation(builder.toString(), SqlType.ALTER, before, after));
		}
		return result;
	}

	private SqlOperation rebuild(Index before, Index after) {
		OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().index().space().name(before, getOptions().isDecorateSchemaName()).space()._add("REBUILD");
		if (!Objects.equals(before.getTableSpaceName(), after.getTableSpaceName())) OracleIndexRebuildOptions.tablespace(after.getTableSpaceName(), builder);
		if (OracleIndexRebuildOptions.compressionChange(before, after)) OracleIndexRebuildOptions.compression(after, getDialect(), builder);
		OracleIndexRebuildOptions.online(before, getDialect(), getTableOptions().getOnlineIndex().test(after.getTable(), after), false, false, builder);
		return createOperation(builder.toString(), SqlType.REBUILD, before, after);
	}

	private SqlOperation operation(Index before, Index after, String clause, SqlType type) {
		OracleSqlBuilder builder = createSqlBuilder();
		builder.alter().index().space().name(before, getOptions().isDecorateSchemaName()).space()._add(clause);
		return createOperation(builder.toString(), type, before, after);
	}
}
