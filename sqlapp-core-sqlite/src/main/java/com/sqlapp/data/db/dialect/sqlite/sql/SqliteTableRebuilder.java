/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.util.CommonUtils;

/** Explicit, transaction-owning reconstruction of SQLite Schema tables. */
public final class SqliteTableRebuilder {
	/**
	 * Executed statements and verified row count; transaction and FK checks
	 * succeeded.
	 */
	public record Result(List<SqlOperation> operations, long rowCount) {
		public Result {
			operations = List.copyOf(operations);
		}
	}

	/**
	 * Identity changes require an explicit choice to permit native reassignment.
	 */
	public enum RowIdentity {
		PRESERVE, REGENERATE
	}

	/** Cross-module settings that cannot be represented need an explicit reset. */
	public enum ModuleSettings {
		PRESERVE_COMPATIBLE, DESTINATION_DEFAULTS
	}

	/**
	 * Optional controls for the Schema entry point; ordinary rebuilds need none.
	 */
	public static final class Options {
		private List<String> tables = List.of();
		private final Map<String, Map<String, String>> mappings = new LinkedHashMap<>();
		private final Map<String, RowIdentity> identities = new LinkedHashMap<>();
		private final Map<String, ModuleSettings> conversions = new LinkedHashMap<>();
		private final Map<String, Recovery> recoveries = new LinkedHashMap<>();
		private Schema temporary;

		/**
		 * Rebuild only these tables; by default rebuild every table in the supplied
		 * Schema.
		 */
		public Options tables(String... names) {
			tables = List.of(names);
			return this;
		}

		/** Target-column to source-column identifiers for one table. */
		public Options sourceColumns(String table, Map<String, String> columns) {
			requireName(table);
			mappings.put(table, new LinkedHashMap<>(Objects.requireNonNull(columns)));
			return this;
		}

		/**
		 * Permit rowid/IDENTITY changes for this table; native SQLite copies existing
		 * named values.
		 */
		public Options rowIdentity(String table, RowIdentity mode) {
			requireName(table);
			identities.put(table, Objects.requireNonNull(mode));
			return this;
		}

		/**
		 * Convert within the FTS or RTree family, retaining portable runtime settings.
		 */
		public Options convertModule(String table) {
			return convertModule(table, ModuleSettings.PRESERVE_COMPATIBLE);
		}

		/**
		 * Explicitly choose whether to retain portable settings or use destination
		 * defaults.
		 */
		public Options convertModule(String table, ModuleSettings settings) {
			requireName(table);
			conversions.put(table, Objects.requireNonNull(settings, "settings"));
			return this;
		}

		/**
		 * Authoritative content for contentless FTS5; infer an accessible native rowid.
		 */
		public Options recoverContent(String table, Table source) {
			return recoverContent(table, source, null);
		}

		/**
		 * The source supplies original visible column names and explicit integer
		 * document IDs.
		 */
		public Options recoverContent(String table, Table source, String rowIdColumn) {
			return recoverContent(table, source, rowIdColumn, Map.of());
		}

		/**
		 * Original FTS-column to authoritative source-column identifiers; omitted
		 * columns keep their names.
		 */
		public Options recoverContent(String table, Table source, String rowIdColumn, Map<String, String> columns) {
			requireName(table);
			Objects.requireNonNull(source, "source");
			requireName(source.getName());
			if (rowIdColumn != null)
				requireName(rowIdColumn);
			Objects.requireNonNull(columns, "columns");
			columns.forEach((original, content) -> {
				requireName(original);
				requireName(content);
			});
			recoveries.put(table, new Recovery(source.clone(), rowIdColumn, Map.copyOf(columns)));
			return this;
		}

		/**
		 * Complete final TEMP views/triggers; TEMP tables are not rebuilt by this
		 * option.
		 */
		public Options temporaryDependencies(Schema schema) {
			temporary = Objects.requireNonNull(schema).clone();
			return this;
		}
	}

	/**
	 * Executed statements and retained row counts by table name, after a single
	 * commit.
	 */
	public record BatchResult(List<SqlOperation> operations, Map<String, Long> rowCounts) {
		public BatchResult {
			operations = List.copyOf(operations);
			rowCounts = Map.copyOf(rowCounts);
		}
	}

	/**
	 * Reconstruct all tables and replace views/triggers with the supplied final
	 * Schema.
	 */
	public BatchResult rebuild(Connection connection, Schema target) throws SQLException {
		return rebuild(connection, target, new Options());
	}

	/**
	 * Reconstruct selected tables and final dependent definitions in one owned
	 * transaction.
	 */
	public BatchResult rebuild(Connection connection, Schema requested, Options options) throws SQLException {
		Objects.requireNonNull(requested, "schema");
		Objects.requireNonNull(options, "options");
		var desired = requested.clone();
		var targets = new ArrayList<Table>();
		if (options.tables.isEmpty())
			targets.addAll(desired.getTables());
		else
			for (String name : options.tables) {
				requireName(name);
				var table = desired.getTables().get(name);
				if (table == null)
					throw new IllegalArgumentException("SQLite rebuild target table is missing: " + name);
				targets.add(table);
			}
		return execute(connection, targets, desired, options);
	}

	/**
	 * Copies same-named physical columns and preserves row identity. New columns
	 * use their defaults. Restores and validates the owning schema's views and
	 * triggers; never invoked as an implicit ALTER fallback.
	 */
	public Result rebuild(Connection connection, Table requestedTarget) throws SQLException {
		return rebuild(connection, requestedTarget, Map.of());
	}

	/**
	 * Optional target-column to source-column mappings; values are identifiers, not
	 * SQL.
	 */
	public Result rebuild(Connection connection, Table requestedTarget, Map<String, String> sourceColumns)
			throws SQLException {
		Objects.requireNonNull(requestedTarget, "target");
		var result = execute(connection, List.of(requestedTarget), null,
				new Options().sourceColumns(requestedTarget.getName(), sourceColumns));
		return new Result(result.operations(), result.rowCounts().get(requestedTarget.getName()));
	}

	private BatchResult execute(Connection connection, List<Table> requestedTargets, Schema desired, Options options)
			throws SQLException {
		Objects.requireNonNull(connection, "connection");
		if (requestedTargets.isEmpty())
			throw new IllegalArgumentException("SQLite rebuild requires at least one target table");
		var targets = requestedTargets.stream().map(Table::clone).toList();
		String database = targets.get(0).getSchemaName();
		requireName(database);
		var names = new java.util.HashSet<String>();
		for (var target : targets) {
			requireName(target.getSchemaName());
			requireName(target.getName());
			if (!database.equalsIgnoreCase(target.getSchemaName())
					|| !names.add(target.getName().toLowerCase(Locale.ROOT)))
				throw new IllegalArgumentException(
						"SQLite batch rebuild requires distinct tables in a single database");
		}
		for (var key : options.mappings.keySet())
			if (!names.contains(key.toLowerCase(Locale.ROOT)))
				throw new IllegalArgumentException("SQLite column mapping names an unselected table: " + key);
		for (var key : options.identities.keySet())
			if (!names.contains(key.toLowerCase(Locale.ROOT)))
				throw new IllegalArgumentException("SQLite row identity option names an unselected table: " + key);
		for (var key : options.conversions.keySet())
			if (!names.contains(key.toLowerCase(Locale.ROOT)))
				throw new IllegalArgumentException("SQLite module conversion names an unselected table: " + key);
		for (var key : options.recoveries.keySet())
			if (!names.contains(key.toLowerCase(Locale.ROOT)))
				throw new IllegalArgumentException("SQLite content recovery names an unselected table: " + key);
		if (options.temporary != null
				&& (!"temp".equalsIgnoreCase(options.temporary.getName()) || "temp".equalsIgnoreCase(database)))
			throw new IllegalArgumentException(
					"Temporary dependencies require a temp Schema and a non-temp target database");
		if (!connection.getAutoCommit())
			throw new IllegalArgumentException(
					"SQLite table rebuild owns its transaction; use an auto-commit connection");
		var resolved = DialectResolver.getInstance().getDialect(connection);
		if (!(resolved instanceof Sqlite dialect) || !dialect.supportsForeignKeyCheck())
			throw new IllegalArgumentException("SQLite table rebuild requires SQLite 3.7.16 or later");
		for (var target : targets)
			target.setDialect(dialect);
		if (pragma(connection, "ignore_check_constraints") != 0 || pragma(connection, "writable_schema") != 0)
			throw new IllegalArgumentException(
					"SQLite table rebuild requires CHECK enforcement and writable_schema=OFF");
		int foreignKeys = pragma(connection, "foreign_keys");
		boolean started = false, resolvedTransaction = false, committed = false;
		Throwable failure = null;
		var operations = new ArrayList<SqlOperation>();
		var exactCopies = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<SqlOperation, Boolean>());
		var rowCounts = new LinkedHashMap<String, Long>();
		try {
			run(connection, "PRAGMA foreign_keys=OFF");
			if (pragma(connection, "foreign_keys") != 0)
				throw new SQLException("Cannot disable SQLite foreign keys before starting rebuild transaction");
			try (var statement = connection.createStatement();
					var result = statement.executeQuery("PRAGMA " + quote(database) + ".journal_mode")) {
				if (!result.next() || "off".equalsIgnoreCase(result.getString(1)))
					throw new IllegalArgumentException(
							"SQLite table rebuild requires rollback journaling; journal_mode=OFF is unsupported");
			}
			connection.setAutoCommit(false);
			started = true;
			// TEMP triggers can bind to any attached database. Fetch owners in schema
			// batches rather than issuing metadata queries once per trigger.
			Map<String, Schema> schemas = new LinkedHashMap<>();
			var schema = readSchema(connection, dialect, database);
			for (var target : targets)
				if (schema.getTables().get(target.getName()) == null)
					throw new IllegalArgumentException(
							"SQLite rebuild source table does not exist or is a shadow table: " + target.getName());
			schemas.put(schema.getName().toLowerCase(Locale.ROOT), schema);
			var contentSchemas = new LinkedHashMap<String, Schema>();
			contentSchemas.putAll(schemas);
			for (var recovery : options.recoveries.values()) {
				String name = recovery.source().getSchemaName() == null ? database : recovery.source().getSchemaName();
				requireName(name);
				String key = name.toLowerCase(Locale.ROOT);
				if (!contentSchemas.containsKey(key))
					contentSchemas.put(key, readSchema(connection, dialect, name));
			}
			var dependencies = new ArrayList<Schema>();
			dependencies.add(schema);
			if (!"temp".equalsIgnoreCase(schema.getName()) && (options.temporary != null || scalar(connection,
					"SELECT count(*) FROM temp.sqlite_master WHERE type IN ('view','trigger')") != 0)) {
				try (var statement = connection.createStatement();
						var result = statement.executeQuery("PRAGMA temp.journal_mode")) {
					if (!result.next() || "off".equalsIgnoreCase(result.getString(1)))
						throw new IllegalArgumentException(
								"SQLite TEMP dependency reconstruction requires rollback journaling");
				}
				var temporary = contentSchemas.get("temp");
				if (temporary == null)
					temporary = readSchema(connection, dialect, "temp");
				schemas.put("temp", temporary);
				dependencies.add(temporary);
			}
			var finalDependencies = new ArrayList<Schema>();
			for (var dependent : dependencies) {
				var finalSchema = dependent.clone();
				Schema definitions = dependent == schema ? desired : options.temporary;
				if (definitions != null) {
					finalSchema.getViews().clear();
					finalSchema.getTriggers().clear();
					for (var view : definitions.getViews())
						finalSchema.getViews().add(view.clone());
					for (var trigger : definitions.getTriggers())
						finalSchema.getTriggers().add(trigger.clone());
				}
				if (dependent == schema)
					for (var table : targets) {
						finalSchema.getTables().remove(table.getName());
						finalSchema.getTables().add(table.clone());
					}
				finalDependencies.add(finalSchema);
				schemas.put(finalSchema.getName().toLowerCase(Locale.ROOT), finalSchema);
			}
			for (var dependent : finalDependencies)
				for (var trigger : dependent.getTriggers()) {
					requireName(trigger.getTableSchemaName());
					String name = trigger.getTableSchemaName().toLowerCase(Locale.ROOT);
					if (!schemas.containsKey(name))
						schemas.put(name, readSchema(connection, dialect, trigger.getTableSchemaName()));
				}
			// Older readers cannot distinguish shadow tables from ordinary tables.
			if (!dialect.supportsTableList() && schema.getTables().stream()
					.anyMatch(table -> "true".equals(table.getSpecifics().get("sqlite.virtual"))))
				throw new IllegalArgumentException(
						"SQLite table rebuild currently requires a schema without virtual tables or their shadow tables");
			checkForeignKeys(connection, dialect, database);
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setCreateIfNotExists(false);
			var prepared = new ArrayList<Prepared>();
			for (var target : targets) {
				var original = schema.getTables().get(target.getName());
				if (original == null)
					throw new IllegalArgumentException(
							"SQLite rebuild source table does not exist: " + target.getName());
				validateTable(original);
				validateTable(target);
				Recovery recovery = option(options.recoveries, target.getName(), null);
				if (contentless(original) && recovery == null)
					throw new IllegalArgumentException(
							"SQLite contentless FTS5 requires Options.recoverContent with an authoritative content source");
				if (recovery != null && (!contentless(original) || !"fts5".equals(module(original))))
					throw new IllegalArgumentException(
							"SQLite recoverContent is only valid for a contentless FTS5 source");
				ModuleSettings conversion = option(options.conversions, target.getName(), null);
				validateConversion(connection, original, target, conversion);
				var mappings = resolveMappings(original, target, option(options.mappings, target.getName(), Map.of()));
				boolean preserve = option(options.identities, target.getName(),
						RowIdentity.PRESERVE) == RowIdentity.PRESERVE;
				var copiedIdentities = identities(target).stream().map(name -> mappings.getOrDefault(name, name))
						.toList();
				if (preserve && (withoutRowid(original) != withoutRowid(target)
						|| !identities(original).equals(copiedIdentities)))
					throw new IllegalArgumentException(
							"SQLite rebuild identity changes require explicit RowIdentity.REGENERATE: "
									+ target.getName());
				if (module(original) != null && module(original).startsWith("rtree") && preserve
						&& !original.getColumns().get(0).getName().equals(mappings.getOrDefault(
								target.getColumns().get(0).getName(), target.getColumns().get(0).getName())))
					throw new IllegalArgumentException(
							"SQLite RTree key changes require explicit RowIdentity.REGENERATE");
				long rows = scalar(connection, "SELECT count(*) FROM " + identity(original));
				rowCounts.put(target.getName(), rows);
				Table scratch = target.clone();
				scratch.setName("sqlapp_rebuild_" + UUID.randomUUID().toString().replace("-", ""));
				scratch.getIndexes().clear();
				preserveSelfReferences(target, scratch);
				Table copySource = original;
				String copyRowId = null;
				checkVirtual(connection, original, operations);
				if (recovery != null) {
					String name = recovery.source().getSchemaName() == null ? database
							: recovery.source().getSchemaName();
					var contentSchema = contentSchemas.get(name.toLowerCase(Locale.ROOT));
					copySource = contentSchema.getTables().get(recovery.source().getName());
					if (copySource == null)
						copySource = contentSchema.getViews().get(recovery.source().getName());
					if (copySource == null || module(copySource) != null)
						throw new IllegalArgumentException(
								"SQLite recovery content source must be an existing ordinary table or view");
					for (var column : mappings.values())
						if (original.getColumns().get(column).isHidden())
							throw new IllegalArgumentException(
									"SQLite recovery mappings must reference original visible columns");
					copyRowId = SqliteContentRecovery.rowId(copySource, recovery.rowIdColumn());
					var verified = SqliteContentRecovery.verify(connection, dialect, original, copySource, copyRowId,
							recovery.columns(), operations);
					copySource = verified.table();
					copyRowId = verified.rowId();
				}
				prepared.add(new Prepared(original, target, scratch, mappings, preserve,
						autoIncrement(original) && autoIncrement(target)
								&& identities(original).equals(copiedIdentities) ? sequence(connection, original)
										: null,
						virtualConfiguration(connection, original, target, conversion), copySource, copyRowId));
			}
			// Drop triggers before views, since DROP VIEW implicitly removes its triggers.
			// Restoring after the copy also avoids duplicate application-side effects.
			int pending = operations.size();
			for (var dependent : dependencies)
				for (var trigger : dependent.getTriggers())
					operations.addAll(registry.createSql(trigger, SqlType.DROP));
			for (var dependent : dependencies)
				for (var view : dependent.getViews())
					operations.addAll(registry.createSql(view, SqlType.DROP));
			for (var item : prepared) {
				operations.addAll(registry.createSql(item.scratch(), SqlType.CREATE));
				operations.add(copy(item));
				if (item.copyRowId() != null)
					operations.addAll(registry.createSql(item.copySource(), SqlType.DROP));
				if (!Objects.equals(module(item.original()), module(item.target()))
						&& isRtree(module(item.original()))) {
					var layout = copyLayout(item);
					String input = String.join(",", layout.inputs().stream().map(SqliteTableRebuilder::quote).toList());
					String output = String.join(",",
							layout.columns().stream().map(SqliteTableRebuilder::quote).toList());
					var check = new SqlOperation(
							"SELECT count(*) FROM (SELECT " + input + " FROM " + identity(item.original())
									+ " EXCEPT SELECT " + output + " FROM " + identity(item.scratch()) + ")",
							SqlType.SELECT, item.original(), item.scratch());
					operations.add(check);
					exactCopies.add(check);
				}
			}
			// Stage every source before any DROP, including external-content FTS sources.
			for (var item : prepared)
				operations.addAll(registry.createSql(item.original(), SqlType.DROP));
			for (var item : prepared) {
				if (module(item.target()) != null)
					operations.add(new SqlOperation(
							"ALTER TABLE " + identity(item.scratch()) + " RENAME TO " + quote(item.target().getName()),
							SqlType.ALTER, item.scratch(), item.target()));
				else {
					Table renamed = item.scratch().clone();
					renamed.setName(item.target().getName());
					operations.addAll(registry.createSql(item.scratch().diff(renamed), SqlType.ALTER));
				}
				for (var index : item.target().getIndexes())
					operations.addAll(registry.createSql(index, SqlType.CREATE));
				operations.addAll(item.configuration());
			}
			for (var dependent : finalDependencies)
				for (var view : dependent.getViews())
					operations.addAll(registry.createSql(view, SqlType.CREATE));
			for (var dependent : finalDependencies)
				for (var trigger : dependent.getTriggers())
					operations.addAll(registry.createSql(trigger, SqlType.CREATE));
			for (var operation : operations.subList(pending, operations.size())) {
				try {
					if (exactCopies.contains(operation)) {
						if (scalar(connection, operation.getSqlText()) != 0)
							throw new SQLException(
									"SQLite RTree module conversion would lose coordinate or copied value precision");
					} else
						run(connection, operation.getSqlText());
				} catch (SQLException exception) {
					throw new SQLException("SQLite rebuild failed while executing: " + operation.getSqlText(),
							exception);
				}
			}
			for (var item : prepared) {
				restoreSequence(connection, item, operations);
				if (scalar(connection, "SELECT count(*) FROM " + identity(item.target())) != rowCounts
						.get(item.target().getName()))
					throw new SQLException("SQLite rebuild row count changed; rolling back");
				checkVirtual(connection, item.target(), operations);
			}
			// CREATE VIEW/TRIGGER alone does not validate all references in SQLite.
			for (var dependent : finalDependencies)
				for (var view : dependent.getViews()) {
					try (var statement = connection.createStatement();
							var result = statement.executeQuery("SELECT * FROM " + identity(view) + " LIMIT 0")) {
						// Newly supplied queries can infer columns. Reuse this validation result
						// instead of issuing per-view metadata queries for trigger compilation.
						var metadata = result.getMetaData();
						view.getColumns().clear();
						for (int i = 1; i <= metadata.getColumnCount(); i++)
							view.getColumns().add(new Column(metadata.getColumnLabel(i)));
					}
				}
			var owners = new java.util.LinkedHashSet<String>();
			for (var dependent : finalDependencies)
				for (var trigger : dependent.getTriggers()) {
					var ownerSchema = trigger.getTableSchemaName() == null ? null
							: schemas.get(trigger.getTableSchemaName().toLowerCase(Locale.ROOT));
					var owner = ownerSchema == null ? null : ownerSchema.getTables().get(trigger.getTableName());
					if (owner == null && ownerSchema != null)
						owner = ownerSchema.getViews().get(trigger.getTableName());
					if (owner == null)
						throw new SQLException(
								"SQLite trigger owner is absent from rebuild schema: " + trigger.getName());
					for (String column : com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl
							.triggerUpdateColumns(String.join("\n", trigger.getDefinition())))
						if (owner.getColumns().get(column) == null)
							throw new SQLException("SQLite UPDATE OF trigger references a missing column: "
									+ trigger.getName() + "." + column);
					String event = com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl
							.triggerEvent(String.join("\n", trigger.getDefinition())).get(1);
					if (!owners.add(
							owner.getSchemaName().toLowerCase(Locale.ROOT) + "\0" + owner.getName() + "\0" + event))
						continue;
					if (event.equals("INSERT"))
						compile(connection, "INSERT INTO " + identity(owner) + " DEFAULT VALUES");
					else if (event.equals("DELETE"))
						compile(connection, "DELETE FROM " + identity(owner) + " WHERE 0");
					else {
						var assignments = owner.getColumns().stream()
								.filter(column -> !column.isHidden() && CommonUtils.isEmpty(column.getFormula()))
								.map(column -> quote(column.getName()) + "=" + quote(column.getName())).toList();
						if (!assignments.isEmpty())
							compile(connection,
									"UPDATE " + identity(owner) + " SET " + String.join(",", assignments) + " WHERE 0");
					}
				}
			checkForeignKeys(connection, dialect, database);
			String integrity = new SqliteIntegrityCheckBuilder(dialect).schema(database).build();
			try (var statement = connection.createStatement(); var result = statement.executeQuery(integrity)) {
				if (!result.next() || !"ok".equals(result.getString(1)) || result.next())
					throw new SQLException("SQLite integrity_check rejected rebuilt schema; rolling back");
			}
			connection.commit();
			resolvedTransaction = true;
			committed = true;
			return new BatchResult(operations, rowCounts);
		} catch (SQLException | RuntimeException | Error exception) {
			failure = exception;
			if (started) {
				try {
					connection.rollback();
					resolvedTransaction = true;
				} catch (SQLException rollback) {
					exception.addSuppressed(rollback);
				}
			}
			throw exception;
		} finally {
			// Never implicitly commit after a failed rollback.
			if (!started || resolvedTransaction) {
				try {
					if (started)
						connection.setAutoCommit(true);
					run(connection, "PRAGMA foreign_keys=" + foreignKeys);
					if (pragma(connection, "foreign_keys") != foreignKeys)
						throw new SQLException("SQLite rebuild could not restore foreign key connection settings");
				} catch (SQLException cleanup) {
					if (failure != null)
						failure.addSuppressed(cleanup);
					else if (committed)
						throw new SQLException("SQLite rebuild committed, but restoring connection settings failed",
								cleanup);
					else
						throw cleanup;
				}
			}
		}
	}

	private static Schema readSchema(Connection connection, Sqlite dialect, String name) {
		var reader = dialect.getCatalogReader().getSchemaReader();
		reader.setSchemaName(name);
		var schemas = reader.getAllFull(connection);
		if (schemas.size() != 1)
			throw new IllegalArgumentException("SQLite rebuild database must already exist: " + name);
		return schemas.get(0);
	}

	private record Prepared(Table original, Table target, Table scratch, Map<String, String> mappings, boolean preserve,
			Long sequence, List<SqlOperation> configuration, Table copySource, String copyRowId) {
	}

	private record Recovery(Table source, String rowIdColumn, Map<String, String> columns) {
	}

	private record CopyLayout(List<String> columns, List<String> inputs) {
	}

	private static CopyLayout copyLayout(Prepared item) {
		var original = item.original();
		var target = item.target();
		var mappings = item.mappings();
		var copied = target.getColumns().stream()
				.filter(column -> !column.isHidden() && CommonUtils.isEmpty(column.getFormula()))
				.filter(column -> mappings.containsKey(column.getName())
						|| original.getColumns().get(column.getName()) != null)
				.map(Column::getName).toList();
		var columns = new ArrayList<>(copied);
		var inputs = new ArrayList<>(copied.stream().map(name -> mappings.getOrDefault(name, name)).toList());
		String sourceLanguage = languageId(original), targetLanguage = languageId(target);
		if (sourceLanguage != null && targetLanguage == null && Objects.equals(module(original), module(target)))
			throw new IllegalArgumentException(
					"SQLite FTS4 rebuild must retain languageid to preserve language-specific indexes");
		if (targetLanguage != null && (sourceLanguage != null || mappings.containsKey(targetLanguage))) {
			columns.add(targetLanguage);
			inputs.add(mappings.getOrDefault(targetLanguage, sourceLanguage));
		}
		if (item.preserve() && !withoutRowid(original) && identities(original).isEmpty()
				&& (module(original) == null || !module(original).startsWith("rtree"))) {
			String alias = List.of("rowid", "_rowid_", "oid").stream()
					.filter(name -> original.getColumns().get(name) == null && target.getColumns().get(name) == null)
					.findFirst().orElseThrow(
							() -> new IllegalArgumentException("SQLite rebuild cannot preserve an inaccessible rowid"));
			columns.add(0, alias);
			inputs.add(0, item.copyRowId() == null ? alias : item.copyRowId());
		}
		if (columns.isEmpty())
			throw new IllegalArgumentException("SQLite rebuild requires at least one unambiguous copy column");
		return new CopyLayout(columns, inputs);
	}

	private static SqlOperation copy(Prepared item) {
		var layout = copyLayout(item);
		String outputs = String.join(",", layout.columns().stream().map(SqliteTableRebuilder::quote).toList());
		String values = String.join(",", layout.inputs().stream().map(SqliteTableRebuilder::quote).toList());
		return new SqlOperation("INSERT OR ABORT INTO " + identity(item.scratch()) + "(" + outputs + ") SELECT "
				+ values + " FROM " + identity(item.copySource()), SqlType.INSERT, item.original(), item.scratch());
	}

	private static void restoreSequence(Connection connection, Prepared item, List<SqlOperation> operations)
			throws SQLException {
		if (item.sequence() == null)
			return;
		var target = item.target();
		String sequenceTable = quote(target.getSchemaName()) + ".sqlite_sequence", name = literal(target.getName());
		String sql = "UPDATE " + sequenceTable + " SET seq=MAX(COALESCE(seq,0)," + item.sequence() + ") WHERE name="
				+ name;
		run(connection, sql);
		operations.add(new SqlOperation(sql, SqlType.UPDATE, target));
		sql = "INSERT INTO " + sequenceTable + "(name,seq) SELECT " + name + "," + item.sequence()
				+ " WHERE NOT EXISTS(SELECT 1 FROM " + sequenceTable + " WHERE name=" + name + ")";
		run(connection, sql);
		operations.add(new SqlOperation(sql, SqlType.INSERT, target));
	}

	private static <T> T option(Map<String, T> options, String name, T fallback) {
		T result = fallback;
		boolean found = false;
		for (var entry : options.entrySet())
			if (entry.getKey().equalsIgnoreCase(name)) {
				if (found)
					throw new IllegalArgumentException(
							"SQLite rebuild option names the same table more than once: " + name);
				found = true;
				result = entry.getValue();
			}
		return result;
	}

	private static Long sequence(Connection connection, Table original) throws SQLException {
		try (var query = connection.prepareStatement(
				"SELECT seq FROM " + quote(original.getSchemaName()) + ".sqlite_sequence WHERE name=?")) {
			query.setString(1, original.getName());
			try (var result = query.executeQuery()) {
				if (!result.next())
					return null;
				Object value = result.getObject(1);
				if (!(value instanceof Integer || value instanceof Long))
					throw new IllegalArgumentException(
							"SQLite rebuild requires an integer AUTOINCREMENT high-water mark");
				if (result.next())
					throw new IllegalArgumentException("SQLite rebuild found duplicate sqlite_sequence entries");
				return ((Number) value).longValue();
			}
		}
	}

	private static String module(Table table) {
		String module = com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl
				.virtualTableModule(String.join("\n", table.getDefinition()));
		return module == null ? null : module.toLowerCase(Locale.ROOT);
	}

	private static boolean isFts(String module) {
		return "fts3".equals(module) || "fts4".equals(module) || "fts5".equals(module);
	}

	private static boolean isRtree(String module) {
		return "rtree".equals(module) || "rtree_i32".equals(module);
	}

	private static boolean contentless(Table table) {
		return isFts(module(table)) && "".equals(com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl
				.virtualTableOption(String.join("\n", table.getDefinition()), "content"));
	}

	private static void validateConversion(Connection connection, Table original, Table target, ModuleSettings settings)
			throws SQLException {
		String source = module(original), destination = module(target);
		if (Objects.equals(source, destination)) {
			if (settings != null)
				throw new IllegalArgumentException(
						"SQLite module conversion requires different source and destination modules: "
								+ target.getName());
			return;
		}
		if (settings == null)
			throw new IllegalArgumentException(
					"SQLite module changes require Options.convertModule: " + target.getName());
		if (!(isFts(source) && isFts(destination) || isRtree(source) && isRtree(destination)))
			throw new IllegalArgumentException("SQLite module conversion must stay within the FTS or RTree family");
		String language = languageId(original);
		if (language != null && !"fts4".equals(destination) && scalar(connection,
				"SELECT count(*) FROM " + identity(original) + " WHERE " + quote(language) + "<>0") != 0)
			throw new IllegalArgumentException(
					"SQLite FTS conversion cannot preserve nonzero languageid partitions in the destination module");
	}

	private static List<SqlOperation> virtualConfiguration(Connection connection, Table original, Table target,
			ModuleSettings conversion) throws SQLException {
		if (conversion != null) {
			if (!isFts(module(original)) || conversion == ModuleSettings.DESTINATION_DEFAULTS)
				return List.of();
			int automerge;
			if ("fts5".equals(module(original))) {
				var configuration = fts5Configuration(connection, original);
				for (var key : configuration.keySet())
					if (!key.equals("automerge"))
						throw new IllegalArgumentException("SQLite FTS setting cannot be converted: " + key
								+ "; explicitly choose ModuleSettings.DESTINATION_DEFAULTS to reset runtime settings");
				String value = configuration.getOrDefault("automerge", "4");
				try {
					automerge = Integer.parseInt(value);
				} catch (NumberFormatException exception) {
					throw new IllegalArgumentException("SQLite FTS automerge is not a native integer: " + value,
							exception);
				}
				if (automerge < 0 || automerge > 16 || automerge == 1)
					throw new IllegalArgumentException(
							"SQLite FTS5 automerge value is not portable to FTS3/4: " + value);
			} else {
				Integer value = legacyFtsAutomerge(connection, original);
				automerge = value == null ? 0 : value == 1 ? 8 : value;
			}
			String sql = "INSERT INTO " + identity(target) + "(" + quote(target.getName())
					+ ("fts5".equals(module(target)) ? ",rank) VALUES('automerge'," + automerge + ")"
							: ") VALUES(" + literal("automerge=" + automerge) + ")");
			return List.of(new SqlOperation(sql, SqlType.INSERT, target));
		}
		if (java.util.Set.of("fts3", "fts4").contains(Objects.toString(module(original), "")))
			return legacyFtsConfiguration(connection, original, target);
		if (!"fts5".equals(module(original)))
			return List.of();
		var result = new ArrayList<SqlOperation>();
		for (var entry : fts5Configuration(connection, original).entrySet())
			result.add(
					new SqlOperation(
							"INSERT INTO " + identity(target) + "(" + quote(target.getName()) + ",rank) VALUES("
									+ literal(entry.getKey()) + "," + literal(entry.getValue()) + ")",
							SqlType.INSERT, target));
		return result;
	}

	private static Map<String, String> fts5Configuration(Connection connection, Table original) throws SQLException {
		var result = new LinkedHashMap<String, String>();
		// Runtime FTS settings are not present in CREATE DDL. Read once per FTS index;
		// only module configuration is read, never raw shadow-table data copied back.
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT k,v FROM " + quote(original.getSchemaName()) + "."
						+ quote(original.getName() + "_config") + " WHERE k<>'version' ORDER BY k")) {
			while (rows.next())
				result.put(rows.getString(1), rows.getString(2));
		}
		return result;
	}

	private static String languageId(Table table) {
		return "fts4".equals(module(table)) ? com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl
				.virtualTableOption(String.join("\n", table.getDefinition()), "languageid") : null;
	}

	private static List<SqlOperation> legacyFtsConfiguration(Connection connection, Table original, Table target)
			throws SQLException {
		Integer value = legacyFtsAutomerge(connection, original);
		if (value == null)
			return List.of();
		String sql = "INSERT INTO " + identity(target) + "(" + quote(target.getName()) + ") VALUES("
				+ literal("automerge=" + value) + ")";
		return List.of(new SqlOperation(sql, SqlType.INSERT, target));
	}

	private static Integer legacyFtsAutomerge(Connection connection, Table original) throws SQLException {
		String stat = original.getName() + "_stat";
		// FTS3 creates its stat table only when persistent merging is configured.
		try (var query = connection.prepareStatement("SELECT count(*) FROM " + quote(original.getSchemaName())
				+ ".sqlite_master WHERE type='table' AND name=?")) {
			query.setString(1, stat);
			try (var rows = query.executeQuery()) {
				if (!rows.next() || rows.getInt(1) == 0)
					return null;
			}
		}
		// FTS_STAT_AUTOINCRMERGE (id=2) stores the native integer setting. Index
		// segments, merge hints and statistics are regenerated by the module.
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery(
						"SELECT value FROM " + quote(original.getSchemaName()) + "." + quote(stat) + " WHERE id=2")) {
			if (!rows.next())
				return null;
			Object value = rows.getObject(1);
			if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0
					|| ((Number) value).longValue() > 16)
				throw new IllegalArgumentException(
						"SQLite FTS3/4 automerge setting must be a native integer between 0 and 16");
			return ((Number) value).intValue();
		}
	}

	private static void checkVirtual(Connection connection, Table table, List<SqlOperation> operations)
			throws SQLException {
		String module = module(table);
		if ("fts5".equals(module)) {
			String sql = "INSERT INTO " + identity(table) + "(" + quote(table.getName())
					+ ",rank) VALUES('integrity-check',1)";
			run(connection, sql);
			operations.add(new SqlOperation(sql, SqlType.INSERT, table));
		} else if ("fts3".equals(module) || "fts4".equals(module)) {
			String sql = "INSERT INTO " + identity(table) + "(" + quote(table.getName())
					+ ") VALUES('integrity-check')";
			run(connection, sql);
			operations.add(new SqlOperation(sql, SqlType.INSERT, table));
		} else if (module != null && module.startsWith("rtree")) {
			String sql = "SELECT rtreecheck(" + literal(table.getSchemaName()) + "," + literal(table.getName()) + ")";
			try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
				if (!result.next() || !"ok".equals(result.getString(1)) || result.next())
					throw new SQLException("SQLite RTree integrity check failed: " + table.getName());
			}
			operations.add(new SqlOperation(sql, SqlType.SELECT, table));
		}
	}

	private static String literal(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private static Map<String, String> resolveMappings(Table original, Table target, Map<String, String> requested) {
		Map<String, String> resolved = new LinkedHashMap<>();
		for (var entry : requested.entrySet()) {
			requireName(entry.getKey());
			requireName(entry.getValue());
			var output = target.getColumns().get(entry.getKey());
			var input = original.getColumns().get(entry.getValue());
			if (output == null || input == null || output.isHidden() && !output.getName().equals(languageId(target))
					|| !CommonUtils.isEmpty(output.getFormula()))
				throw new IllegalArgumentException(
						"SQLite rebuild mapping requires an existing source and writable target column: " + entry);
			if (resolved.putIfAbsent(output.getName(), input.getName()) != null)
				throw new IllegalArgumentException(
						"SQLite rebuild mapping resolves the same target column more than once: " + output.getName());
		}
		return resolved;
	}

	private static void validateTable(Table table) {
		String module = module(table);
		if (module != null) {
			if (!java.util.Set.of("fts3", "fts4", "fts5", "rtree", "rtree_i32").contains(module))
				throw new IllegalArgumentException(
						"SQLite rebuild supports FTS3/4/5 and RTree modules only: " + module);
			String definition = String.join("\n", table.getDefinition());
			var columns = com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl.virtualTableColumns(definition);
			var modeled = table.getColumns().stream().filter(column -> !column.isHidden()).map(Column::getName)
					.toList();
			if (!columns.equals(modeled) || !table.getConstraints().isEmpty() || !table.getIndexes().isEmpty())
				throw new IllegalArgumentException(
						"SQLite virtual-table model must match its complete CREATE definition without separate constraints/indexes");
			if ("fts5".equals(module) && numericOption(definition, "contentless_unindexed", 0) != 0)
				throw new IllegalArgumentException(
						"SQLite contentless_unindexed reconstruction is unsupported: bundled SQLite 3.53.1 leaves its content shadow table behind on DROP");
			if (contentless(table) && (!"fts5".equals(module) || numericOption(definition, "columnsize", 1) == 0))
				throw new IllegalArgumentException(
						"SQLite contentless recovery requires FTS5 with columnsize=1 to enumerate and verify document IDs");
		} else if ("true".equals(table.getSpecifics().get("sqlite.virtual"))
				|| table.getColumns().stream().anyMatch(Column::isHidden))
			throw new IllegalArgumentException(
					"SQLite rebuild requires ordinary columns or a complete supported virtual-table definition");
		for (var constraint : table.getConstraints()) {
			if (constraint instanceof ForeignKeyConstraint foreignKey) {
				if (foreignKey.getRelatedTableSchemaName() != null
						&& !table.getSchemaName().equalsIgnoreCase(foreignKey.getRelatedTableSchemaName()))
					throw new IllegalArgumentException(
							"SQLite foreign keys cannot reference another attached database");
			}
		}
	}

	private static int numericOption(String definition, String name, int fallback) {
		String value = com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl.virtualTableOption(definition, name);
		if (value == null)
			return fallback;
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("SQLite " + name + " option must be an integer", exception);
		}
	}

	private static void preserveSelfReferences(Table target, Table scratch) {
		var sourceKeys = target.getConstraints().getForeignKeyConstraints();
		var scratchKeys = scratch.getConstraints().getForeignKeyConstraints();
		for (int i = 0; i < sourceKeys.size(); i++) {
			var source = sourceKeys.get(i);
			if (!target.getName().equalsIgnoreCase(source.getRelatedTableName()))
				continue;
			// Reference the final identity even while the replacement has a scratch name.
			// Separate referenced-column models prevent clone/rename from rebinding it.
			Table parent = new Table(target.getName()).setSchemaName(target.getSchemaName());
			Column[] columns = source
					.getRelatedColumns().stream().map(column -> new Column(column.getName())
							.setSchemaName(target.getSchemaName()).setTableName(target.getName()))
					.toArray(Column[]::new);
			for (var column : columns)
				parent.getColumns().add(column);
			var key = scratchKeys.get(i);
			key.getRelatedColumns().clear();
			key.setRelatedTableName(target.getName());
			key.setRelatedTableSchemaName(target.getSchemaName());
			key.getRelatedColumns().addAll(columns);
		}
	}

	private static List<String> identities(Table table) {
		return table.getColumns().stream().filter(Column::isIdentity).map(Column::getName).toList();
	}

	private static boolean withoutRowid(Table table) {
		return "true".equals(table.getSpecifics().get("without_rowid"));
	}

	private static boolean autoIncrement(Table table) {
		return table.getColumns().stream()
				.anyMatch(column -> "true".equals(column.getSpecifics().get("sqlite.autoincrement")));
	}

	private static String identity(com.sqlapp.data.schemas.AbstractSchemaObject<?> table) {
		return quote(table.getSchemaName()) + "." + quote(table.getName());
	}

	private static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	private static void requireName(String name) {
		if (name == null || name.isBlank() || name.indexOf('\0') >= 0)
			throw new IllegalArgumentException(
					"SQLite rebuild requires explicit nonempty database/table names without NUL");
	}

	private static void run(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static long scalar(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
			if (!rows.next())
				throw new SQLException("SQLite query returned no value: " + sql);
			return rows.getLong(1);
		}
	}

	private static int pragma(Connection connection, String name) throws SQLException {
		return (int) scalar(connection, "PRAGMA " + name);
	}

	private static void compile(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("EXPLAIN " + sql)) {
			while (rows.next()) {
				/* Compile the entire program without executing any DML. */ }
		}
	}

	private static void checkForeignKeys(Connection connection, Sqlite dialect, String database) throws SQLException {
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery(new SqliteForeignKeyCheckBuilder(dialect).schema(database).build())) {
			if (rows.next())
				throw new SQLException("SQLite foreign_key_check failed for " + database + ": " + rows.getString(1));
		}
	}
}
