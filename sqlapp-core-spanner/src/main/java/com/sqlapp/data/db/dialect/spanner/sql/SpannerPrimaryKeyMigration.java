/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;

/** Plans a primary-key replacement for a GoogleSQL table. */
public final class SpannerPrimaryKeyMigration {
	private SpannerPrimaryKeyMigration() {
	}

	/** Execute in order with source writes paused, and require matching counts before cutover. */
	public record Plan(String createSql, String copySql, String countSql, String cutoverSql) {
	}

	/** Moves one schema object to staging after copying and verifying the data. */
	public record DependencyMove(String dropSourceSql, String createStagingSql, String dropStagingSql,
			String recreateSourceSql) {
	}

	/** Drop before the name swap, then add on the table using the original name. */
	public record ForeignKeyMove(String dropSql, String addSql) {
	}

	/** The moves and foreign-key changes run while applications are paused. */
	public record ReplacementPlan(Plan table, List<DependencyMove> moves, List<ForeignKeyMove> foreignKeys) {
		public ReplacementPlan {
			moves = List.copyOf(moves);
			foreignKeys = List.copyOf(foreignKeys);
		}

		/** Execute in order after verifying the copied rows, with applications still paused. */
		public List<String> cutoverStatements() {
			final var statements = new ArrayList<String>();
			for (final var foreignKey : foreignKeys)
				statements.add(foreignKey.dropSql());
			for (final var move : moves) {
				statements.add(move.dropSourceSql());
				statements.add(move.createStagingSql());
			}
			statements.add(table.cutoverSql());
			for (final var foreignKey : foreignKeys)
				statements.add(foreignKey.addSql());
			return List.copyOf(statements);
		}
	}

	/**
	 * Plans replacement while preserving ordinary user indexes, named CHECK
	 * constraints and modeled foreign keys. Keep applications paused through
	 * the moves and rename.
	 */
	public static ReplacementPlan planWithDependencies(final Table source, final List<String> primaryKey,
			final String stagingName, final String backupName) {
		return planWithDependencies(source, primaryKey, stagingName, backupName, List.of());
	}

	/** Supply referencing tables outside the source schema if they are not resolved in the model. */
	public static ReplacementPlan planWithDependencies(final Table source, final List<String> primaryKey,
			final String stagingName, final String backupName, final List<Table> referencingTables) {
		final Plan table = plan(source, primaryKey, stagingName, backupName, true);
		final var inbound = inboundKeys(source, referencingTables);
		for (final var key : inbound)
			requireReferencedKey(source, primaryKey, key);
		final var staging = source.clone().setName(stagingName);
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setCreateIfNotExists(false);
		registry.getOptions().setDropIfExists(false);
		final var moves = new ArrayList<DependencyMove>();
		final var foreignKeys = new ArrayList<ForeignKeyMove>();
		for (final var index : source.getIndexes()) {
			if (SpannerIndexValidation.isManaged(index)
					&& (!inbound.isEmpty()
							|| source.getConstraints().stream().anyMatch(ForeignKeyConstraint.class::isInstance)))
				continue;
			SpannerIndexValidation.requireStandaloneIndex(index);
			if (index.getIndexType() != null && index.getIndexType() != IndexType.BTree
					|| index.getSpecifics().get(SpannerCreateIndexFactory.PARENT_TABLE_NAME) != null)
				throw new UnsupportedOperationException("Spanner primary-key replacement requires an ordinary index: "
						+ index.getName());
			final String drop = registry.createSql(index, SqlType.DROP).getFirst().getSqlText();
			moves.add(new DependencyMove(drop,
					registry.createSql(staging.getIndexes().get(index.getName()), SqlType.CREATE).getFirst().getSqlText(),
					drop, registry.createSql(index, SqlType.CREATE).getFirst().getSqlText()));
		}
		for (final var constraint : source.getConstraints()) {
			if (constraint instanceof ForeignKeyConstraint key) {
				if (references(source, key))
					requireReferencedKey(source, primaryKey, key);
				foreignKeys.add(foreignKeyMove(registry, source, key));
				continue;
			}
			if (!(constraint instanceof CheckConstraint))
				continue;
			final var without = source.clone();
			without.getConstraints().remove(without.getConstraints().get(constraint.getName()));
			final String drop = registry.createSql(source.diff(without), SqlType.ALTER).getFirst().getSqlText();
			final String add = registry.createSql(without.diff(source), SqlType.ALTER).getFirst().getSqlText();
			final var stagingWithout = without.clone().setName(stagingName);
			moves.add(new DependencyMove(drop,
					registry.createSql(stagingWithout.diff(staging), SqlType.ALTER).getFirst().getSqlText(),
					registry.createSql(staging.diff(stagingWithout), SqlType.ALTER).getFirst().getSqlText(), add));
		}
		for (final var key : inbound)
			foreignKeys.add(foreignKeyMove(registry, key.getTable(), key));
		return new ReplacementPlan(table, moves, foreignKeys);
	}

	private static List<ForeignKeyConstraint> inboundKeys(final Table source, final List<Table> referencingTables) {
		final var candidates = new ArrayList<ForeignKeyConstraint>(source.getChildRelations());
		final var tables = new ArrayList<>(referencingTables);
		if (source.getSchema() != null)
			tables.addAll(source.getSchema().getTables());
		for (final var table : tables) {
			if (Objects.toString(source.getSchemaName(), "").equalsIgnoreCase(Objects.toString(table.getSchemaName(), ""))
					&& source.getName().equalsIgnoreCase(
							table.getSpecifics().get(SpannerCreateTableFactory.PARENT_TABLE_NAME)))
				throw new UnsupportedOperationException("Spanner migration cannot replace an interleave parent: "
						+ source.getName());
			for (final var constraint : table.getConstraints())
				if (constraint instanceof ForeignKeyConstraint key && references(source, key))
					candidates.add(key);
		}
		final var inbound = new LinkedHashMap<List<String>, ForeignKeyConstraint>();
		for (final var key : candidates) {
			final var owner = key.getTable();
			if (owner == null || key.getName() == null || key.getName().isBlank())
				throw new IllegalArgumentException("Spanner migration requires a named foreign key and its owner");
			if (!qualifiedName(source).equalsIgnoreCase(qualifiedName(owner)))
				inbound.putIfAbsent(List.of(qualifiedName(owner).toLowerCase(Locale.ROOT),
						key.getName().toLowerCase(Locale.ROOT)), key);
		}
		return List.copyOf(inbound.values());
	}

	private static boolean references(final Table source, final ForeignKeyConstraint key) {
		return source.getName().equalsIgnoreCase(key.getRelatedTableName())
				&& Objects.toString(source.getSchemaName(), "")
						.equalsIgnoreCase(Objects.toString(key.getRelatedTableSchemaName(), ""));
	}

	private static void requireReferencedKey(final Table source, final List<String> primaryKey,
			final ForeignKeyConstraint foreignKey) {
		final var referenced = foreignKey.getRelatedColumns().stream().map(AbstractNamedObject::getName).toList();
		if (referenced.equals(primaryKey) || source.getIndexes().stream().anyMatch(index -> index.isUnique()
				&& !SpannerIndexValidation.isManaged(index)
				&& !Boolean.TRUE.equals(index.getSpecifics().get(SpannerCreateIndexFactory.IS_NULL_FILTERED, Boolean.class))
				&& (index.getWhere() == null || index.getWhere().isBlank())
				&& referenced.equals(index.getColumns().stream().map(AbstractNamedObject::getName).toList())))
			return;
		throw new IllegalArgumentException("Keep a unique key on " + referenced + " for Spanner foreign key "
				+ foreignKey.getName());
	}

	private static ForeignKeyMove foreignKeyMove(final SqlFactoryRegistry registry, final Table owner,
			final ForeignKeyConstraint key) {
		final var without = owner.clone();
		without.getConstraints().remove(without.getConstraints().get(key.getName()));
		return new ForeignKeyMove(registry.createSql(owner.diff(without), SqlType.ALTER).getFirst().getSqlText(),
				registry.createSql(without.diff(owner), SqlType.ALTER).getFirst().getSqlText());
	}

	/**
	 * Builds an explicit plan and retains the original under backupName. The caller
	 * must verify that both new names are free and no external objects depend on the table.
	 */
	public static Plan plan(final Table source, final List<String> primaryKey, final String stagingName,
			final String backupName) {
		return plan(source, primaryKey, stagingName, backupName, false);
	}

	private static Plan plan(final Table source, final List<String> primaryKey, final String stagingName,
			final String backupName, final boolean moveDependencies) {
		final var names = new HashSet<String>();
		for (final String name : new String[] { source.getName(), stagingName, backupName }) {
			if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]{0,127}")
					|| !names.add(name.toLowerCase(Locale.ROOT)))
				throw new IllegalArgumentException("Spanner migration requires three distinct valid table names");
		}
		final var oldKey = source.getPrimaryKeyConstraint();
		if (oldKey == null || (!moveDependencies && !source.getIndexes().isEmpty())
				|| (!moveDependencies && !source.getChildRelations().isEmpty())
				|| source.getConstraints().stream().anyMatch(constraint -> constraint != oldKey
						&& (!moveDependencies || (!(constraint instanceof CheckConstraint)
								&& !(constraint instanceof ForeignKeyConstraint key && key.getRelatedTableName() != null))
								|| constraint.getName() == null || constraint.getName().isBlank()))
				|| source.getSpecifics().get(SpannerCreateTableFactory.PARENT_TABLE_NAME) != null
				|| source.getSpecifics().get(SpannerCreateTableFactory.ROW_DELETION_POLICY_EXPRESSION) != null
				|| source.getColumns().stream().anyMatch(column -> column.isIdentity() || column.isHidden()
						|| column.getFormula() != null && !column.getFormula().isBlank()))
			throw new UnsupportedOperationException("Spanner primary-key replacement requires a plain independent table");

		final var replacement = source.clone().setName(stagingName);
		replacement.getIndexes().clear();
		final var keys = new HashSet<String>();
		for (final String name : primaryKey) {
			final var column = replacement.getColumns().get(name);
			if (column == null || !keys.add(column.getName().toLowerCase(Locale.ROOT)))
				throw new IllegalArgumentException("Missing or repeated Spanner primary-key column: " + name);
			if (column.getArrayDimension() != 0)
				throw new IllegalArgumentException("Spanner replacement key requires an existing scalar: " + name);
			column.setNotNull(true);
		}
		if (primaryKey.isEmpty() || oldKey.getColumns().stream().map(column -> column.getName()).toList().equals(primaryKey))
			throw new IllegalArgumentException("Specify a different nonempty Spanner primary key");
		replacement.getConstraints().clear();
		replacement.getConstraints().addPrimaryKeyConstraint(oldKey.getName(), primaryKey.toArray(String[]::new));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setCreateIfNotExists(false);
		final String create = registry.createSql(replacement, SqlType.CREATE).getFirst().getSqlText();
		final String original = qualifiedName(source);
		final String staging = qualifiedName(replacement);
		final var columns = new StringJoiner(", ");
		for (final var column : source.getColumns())
			columns.add(DialectHolder.defaultDialect.createSqlBuilder().name(column).toString());
		return new Plan(create, "INSERT INTO " + staging + " (" + columns + ") SELECT " + columns + " FROM " + original,
				"SELECT (SELECT COUNT(*) FROM " + original + "), (SELECT COUNT(*) FROM " + staging + ")",
				"RENAME TABLE " + renameName(source, source.getName()) + " TO " + renameName(source, backupName)
						+ ", " + renameName(source, stagingName) + " TO " + renameName(source, source.getName()));
	}

	private static String qualifiedName(final Table table) {
		return DialectHolder.defaultDialect.createSqlBuilder().name(table, true).toString();
	}

	private static String renameName(final Table source, final String name) {
		final String schema = source.getSchemaName();
		if (schema == null || schema.isEmpty())
			return "`" + name + "`";
		if (!schema.matches("[A-Za-z][A-Za-z0-9_]{0,127}"))
			throw new IllegalArgumentException("Invalid Spanner schema name: " + schema);
		// Multi-table RENAME parses the qualified name as one identifier.
		return "`" + schema + "." + name + "`";
	}
}
