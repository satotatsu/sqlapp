/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/** Explicit HSQLDB table replacement with a retained, verified data backup. */
public final class HsqlTableMigration {
	public enum ConversionPolicy {
		LOSSLESS, ALLOW_LOSSY
	}

	public enum Access {
		EXCLUSIVE
	}

	public enum Phase {
		PREFLIGHT, BACKUP, VALIDATE, REPLACE, RESTORE, VERIFY, COMPLETE, RECOVER, RECOVERED
	}

	public record Command(String sql, SqlType type, String guard) {
		public Command(String sql, SqlType type) {
			this(sql, type, null);
		}
	}

	public record Result(String fingerprint, String backupTable, long rows, String dataFingerprint, Phase phase) {
	}

	public record NamedTypeSnapshot(String kind, String schema, String name, String createSql) {
	}

	public record TableIdentity(String schema, String table) {
	}

	public record Backup(TableIdentity table, String backupTable, long rows, String dataFingerprint) {
	}

	public record DependentView(String schema, String name, String sourceFingerprint, String privileges) {
	}

	public record DependentRoutine(String schema, String name, String specificName, String sourceFingerprint,
			String privileges) {
	}

	public record BatchResult(String fingerprint, Phase phase, List<Backup> backups) {
		public BatchResult {
			backups = List.copyOf(backups);
		}
	}

	public record BatchPlan(String fingerprint, List<Plan> members, List<Command> replace, List<Command> restore,
			List<Command> recovery, List<Command> cleanup, List<DependentView> dependentViews,
			List<DependentRoutine> dependentRoutines) {
		public BatchPlan(String fingerprint, List<Plan> members, List<Command> replace, List<Command> restore,
				List<Command> recovery, List<Command> cleanup, List<DependentView> dependentViews) {
			this(fingerprint, members, replace, restore, recovery, cleanup, dependentViews, List.of());
		}

		public BatchPlan(String fingerprint, List<Plan> members, List<Command> replace, List<Command> restore,
				List<Command> recovery, List<Command> cleanup) {
			this(fingerprint, members, replace, restore, recovery, cleanup, List.of());
		}

		public BatchPlan {
			members = List.copyOf(members);
			replace = List.copyOf(replace);
			restore = List.copyOf(restore);
			recovery = List.copyOf(recovery);
			cleanup = List.copyOf(cleanup);
			if (members.isEmpty() || members.stream().map(p -> new TableIdentity(p.schema(), p.table())).distinct()
					.count() != members.size())
				throw new IllegalArgumentException("A batch requires unique existing tables");
			dependentViews = dependentViews == null ? List.of() : List.copyOf(dependentViews);
			dependentRoutines = dependentRoutines == null ? List.of() : List.copyOf(dependentRoutines);
			var content = new ArrayList<Object>(List.of(members, replace, restore, recovery, cleanup));
			if (!dependentViews.isEmpty())
				content.add(dependentViews);
			if (!dependentRoutines.isEmpty())
				content.add(dependentRoutines);
			String computed = hash(new com.sqlapp.util.JsonConverter().toJsonString(content));
			if (fingerprint != null && !fingerprint.equals(computed))
				throw new IllegalArgumentException("Batch plan fingerprint mismatch");
			fingerprint = computed;
		}
	}

	public static final class BatchFailure extends SQLException {
		private final BatchPlan plan;
		private final Phase phase;
		private final List<Backup> backups;

		BatchFailure(BatchPlan plan, Phase phase, Throwable cause, List<Backup> backups) {
			super("HSQLDB batch migration failed in " + phase + "; inspect retained backups",
					cause instanceof SQLException sql ? sql.getSQLState() : null,
					cause instanceof SQLException sql ? sql.getErrorCode() : 0, cause);
			this.plan = plan;
			this.phase = phase;
			this.backups = List.copyOf(backups);
		}

		public BatchPlan getPlan() {
			return plan;
		}

		public Phase getPhase() {
			return phase;
		}

		public List<Backup> getBackups() {
			return backups;
		}
	}

	private record Scope(java.util.Set<TableIdentity> tables, List<TypeReplacement> changes,
			java.util.Set<HsqlDdlDependencies.ObjectId> dependents) {
	}

	public record TypeReplacement(NamedTypeSnapshot original, NamedTypeSnapshot target, String existenceQuery,
			String originalQuery, String privileges, List<Command> grants) {
		public TypeReplacement {
			grants = List.copyOf(grants);
		}
	}

	/**
	 * Immutable, reviewable SQL phases. Cleanup is separate and never automatic.
	 */
	public record Plan(String schema, String table, String backupTable, String sourceFingerprint,
			String targetFingerprint, String fingerprint, ConversionPolicy conversionPolicy, String sourceSchemaXml,
			String sourcePrivileges, String inboundFingerprint, List<Command> backup, List<String> validationQueries,
			List<Command> replace, List<Command> restore, List<Command> recovery, List<Command> cleanup,
			String backupVerification, String targetVerification, String sourceCount, String backupCount,
			String targetCount, String backupRows, List<NamedTypeSnapshot> namedTypes,
			List<TypeReplacement> typeReplacements) {
		/** Compatibility constructor for plans using unchanged named types. */
		public Plan(String schema, String table, String backupTable, String sourceFingerprint, String targetFingerprint,
				String fingerprint, ConversionPolicy conversionPolicy, String sourceSchemaXml, String sourcePrivileges,
				String inboundFingerprint, List<Command> backup, List<String> validationQueries, List<Command> replace,
				List<Command> restore, List<Command> recovery, List<Command> cleanup, String backupVerification,
				String targetVerification, String sourceCount, String backupCount, String targetCount,
				String backupRows, List<NamedTypeSnapshot> namedTypes) {
			this(schema, table, backupTable, sourceFingerprint, targetFingerprint, fingerprint, conversionPolicy,
					sourceSchemaXml, sourcePrivileges, inboundFingerprint, backup, validationQueries, replace, restore,
					recovery, cleanup, backupVerification, targetVerification, sourceCount, backupCount, targetCount,
					backupRows, namedTypes, List.of());
		}

		/**
		 * Compatibility constructor for plans containing only built-in column types.
		 */
		public Plan(String schema, String table, String backupTable, String sourceFingerprint, String targetFingerprint,
				String fingerprint, ConversionPolicy conversionPolicy, String sourceSchemaXml, String sourcePrivileges,
				String inboundFingerprint, List<Command> backup, List<String> validationQueries, List<Command> replace,
				List<Command> restore, List<Command> recovery, List<Command> cleanup, String backupVerification,
				String targetVerification, String sourceCount, String backupCount, String targetCount,
				String backupRows) {
			this(schema, table, backupTable, sourceFingerprint, targetFingerprint, fingerprint, conversionPolicy,
					sourceSchemaXml, sourcePrivileges, inboundFingerprint, backup, validationQueries, replace, restore,
					recovery, cleanup, backupVerification, targetVerification, sourceCount, backupCount, targetCount,
					backupRows, List.of());
		}

		public Plan {
			backup = List.copyOf(backup);
			validationQueries = List.copyOf(validationQueries);
			replace = List.copyOf(replace);
			restore = List.copyOf(restore);
			recovery = List.copyOf(recovery);
			cleanup = List.copyOf(cleanup);
			if (!hash(sourceSchemaXml).equals(sourceFingerprint))
				throw new IllegalArgumentException("Source metadata fingerprint mismatch");
			namedTypes = namedTypes == null ? List.of() : List.copyOf(namedTypes);
			typeReplacements = typeReplacements == null ? List.of() : List.copyOf(typeReplacements);
			var content = new ArrayList<Object>(java.util.Arrays.asList(schema, table, backupTable, sourceFingerprint,
					targetFingerprint, conversionPolicy, sourcePrivileges, inboundFingerprint, backup,
					validationQueries, replace, restore, recovery, cleanup, backupVerification, targetVerification,
					sourceCount, backupCount, targetCount, backupRows));
			if (!namedTypes.isEmpty())
				content.add(namedTypes);
			if (!typeReplacements.isEmpty())
				content.add(typeReplacements);
			String computed = hash(new com.sqlapp.util.JsonConverter().toJsonString(content));
			if (fingerprint != null && !fingerprint.equals(computed))
				throw new IllegalArgumentException("Migration plan fingerprint mismatch");
			fingerprint = computed;
		}
	}

	/**
	 * Failure retains the phase and full recovery plan; DDL rollback is not
	 * promised.
	 */
	public static final class Failure extends SQLException {
		private final Plan plan;
		private final Phase phase;
		private final String dataFingerprint;

		Failure(Plan plan, Phase phase, Throwable cause, String dataFingerprint) {
			super("HSQLDB table migration failed in " + phase + "; inspect backup " + plan.backupTable(),
					cause instanceof SQLException sql ? sql.getSQLState() : null,
					cause instanceof SQLException sql ? sql.getErrorCode() : 0, cause);
			this.plan = plan;
			this.phase = phase;
			this.dataFingerprint = dataFingerprint;
		}

		public Plan getPlan() {
			return plan;
		}

		public Phase getPhase() {
			return phase;
		}

		public String getDataFingerprint() {
			return dataFingerprint;
		}
	}

	private final SqlFactoryRegistry registry;

	public HsqlTableMigration(Dialect dialect) {
		if (!(dialect instanceof Hsql))
			throw new IllegalArgumentException("An HSQLDB dialect is required");
		registry = dialect.createSqlFactoryRegistry();
	}

	/**
	 * Resolves changed tables and every supplied table using a changed named type.
	 */
	public BatchPlan planAll(Catalog source, Catalog target) {
		return planAll(source, target, ConversionPolicy.LOSSLESS);
	}

	public BatchPlan planAll(Catalog source, Catalog target, ConversionPolicy policy) {
		var selected = new ArrayList<Table>();
		if (!tableIdentities(source).equals(tableIdentities(target)))
			throw new IllegalArgumentException("Batch replacement requires unchanged table identities");
		for (var schema : target.getSchemas())
			for (var table : schema.getTables()) {
				var original = table(source, identity(table));
				if (!original.equals(table) || namedTypes(source, target, original, table).stream()
						.anyMatch(t -> !snapshot(t.kind(), typeObject(target, t)).equals(t)))
					selected.add(table);
			}
		return planAll(source, target, selected, policy);
	}

	public BatchPlan planAll(Catalog source, Catalog target, java.util.Collection<Table> tables,
			ConversionPolicy policy) {
		var selected = new java.util.LinkedHashSet<TableIdentity>();
		var types = new java.util.LinkedHashSet<NamedTypeSnapshot>();
		for (var table : tables) {
			if (!selected.add(identity(table)))
				throw new IllegalArgumentException("Duplicate batch table: " + identity(table));
			types.addAll(namedTypes(source, target, table(source, identity(table)), table));
		}
		if (selected.isEmpty())
			throw new IllegalArgumentException("No affected tables; schema-only changes require a separate migration");
		var changes = typeReplacements(source, target, List.copyOf(types), selected);
		var graph = dependents(source, selected, changes);
		var dependents = graph.objects();
		var views = dependents.stream().filter(id -> "RELATION".equals(id.kind())).toList();
		var routines = dependents.stream().filter(id -> "ROUTINE".equals(id.kind())).toList();
		var generatedRoutines = generatedRoutines(source, target, selected, routines);
		var relations = new java.util.HashSet<>(selected);
		views.forEach(v -> relations.add(new TableIdentity(v.schema(), v.name())));
		validateRoutineExpressions(source, routines, relations);
		validateRoutineExpressions(target, routines, relations);
		var beforeExpressions = new java.util.LinkedHashMap<TableIdentity, TableExpressions>();
		var afterExpressions = new java.util.LinkedHashMap<TableIdentity, TableExpressions>();
		for (var id : selected) {
			beforeExpressions.put(id, tableExpressions(source, table(source, id), routines));
			afterExpressions.put(id, tableExpressions(target, table(target, id), routines));
		}
		var scope = new Scope(java.util.Set.copyOf(selected), changes, java.util.Set.copyOf(dependents));
		var members = tables.stream().map(t -> plan(source, target, t, policy, scope)).toList();
		var sourceKeys = keys(source, selected);
		var targetKeys = keys(target, selected);
		var replace = new ArrayList<Command>();
		var restore = new ArrayList<Command>();
		var recovery = new ArrayList<Command>();
		var snapshots = new ArrayList<DependentView>();
		var routineSnapshots = new ArrayList<DependentRoutine>();
		for (var id : routines) {
			var routine = routine(source, id);
			String permissions = routinePrivileges(source, id.schema(), routine.getName());
			if (!permissions.equals(routinePrivileges(target, id.schema(), routine.getName())))
				throw new IllegalArgumentException("Plan routine privilege changes separately");
			routineSnapshots.add(new DependentRoutine(id.schema(), routine.getName(), id.name(),
					hash(schemaXml(source.getSchemas().get(id.schema()))), permissions));
		}
		for (var id : views) {
			if (!privileges(source, id.schema(), id.name()).equals(privileges(target, id.schema(), id.name())))
				throw new IllegalArgumentException("Plan view privilege changes separately");
			snapshots.add(new DependentView(id.schema(), id.name(),
					hash(schemaXml(source.getSchemas().get(id.schema()))), privileges(source, id.schema(), id.name())));
		}
		dropDependentTriggers(replace, source, selected, dependents);
		for (var expressions : beforeExpressions.values())
			dropExpressions(replace, expressions, false);
		detachRoutines(replace, source, graph.detached(), false);
		for (var id : dependents.reversed())
			if (!generatedRoutines.contains(id))
				replace.add(dropDependent(id));
		for (var key : sourceKeys)
			replace.add(dropKey(key, false));
		for (var id : selected)
			replace.add(new Command("DROP TABLE " + name(table(source, id)), SqlType.DROP));
		for (var id : dependents.reversed())
			if (generatedRoutines.contains(id))
				replace.add(dropDependent(id));
		for (var change : scope.changes()) {
			replace.add(dropType(change.original(), null));
			replace.add(new Command(change.target().createSql(), SqlType.CREATE));
			replace.addAll(change.grants());
		}
		if (generatedRoutines.isEmpty())
			for (var id : selected)
				replace.addAll(createWithoutKeys(afterExpressions.get(id).staged()));
		else
			createBatchObjects(replace, target, afterExpressions.values(), dependents);
		for (var member : members)
			restore.add(insert(table(target, new TableIdentity(member.schema(), member.table())), backupTable(member),
					table(target, new TableIdentity(member.schema(), member.table())).getColumns().stream()
							.filter(HsqlTableMigration::stored).toList(),
					target));
		for (var key : targetKeys)
			restore.addAll(commands(registry.createSql(key, SqlType.CREATE)));
		if (generatedRoutines.isEmpty())
			restoreDependents(restore, target, dependents);
		else
			restoreDependentDetails(restore, target, dependents);
		for (var expressions : afterExpressions.values())
			restoreExpressions(restore, expressions);
		for (var id : selected)
			restore.addAll(details(target, target.getSchemas().get(id.schema()), table(target, id)));
		var allKeys = new java.util.LinkedHashMap<String, com.sqlapp.data.schemas.ForeignKeyConstraint>();
		for (var key : sourceKeys)
			allKeys.put(keyIdentity(key), key);
		for (var key : targetKeys)
			allKeys.put(keyIdentity(key), key);
		dropDependentTriggers(recovery, source, selected, dependents);
		var expressionDrops = new ArrayList<Command>();
		for (var expressions : beforeExpressions.values())
			dropExpressions(expressionDrops, expressions, true);
		for (var expressions : afterExpressions.values())
			dropExpressions(expressionDrops, expressions, true);
		recovery.addAll(new java.util.LinkedHashSet<>(expressionDrops));
		detachRoutines(recovery, source, graph.detached(), true);
		for (var id : dependents.reversed())
			if (!generatedRoutines.contains(id))
				recovery.add(dropDependent(id));
		for (var key : allKeys.values())
			recovery.add(dropKey(key, true));
		for (var id : selected)
			recovery.add(new Command("DROP TABLE " + name(table(source, id)) + " IF EXISTS", SqlType.DROP));
		for (var id : dependents.reversed())
			if (generatedRoutines.contains(id))
				recovery.add(dropDependent(id));
		for (var change : scope.changes())
			addTypeRecovery(recovery, change);
		if (generatedRoutines.isEmpty())
			for (var id : selected)
				recovery.addAll(createWithoutKeys(beforeExpressions.get(id).staged()));
		else
			createBatchObjects(recovery, source, beforeExpressions.values(), dependents);
		for (var member : members) {
			var original = table(source, new TableIdentity(member.schema(), member.table()));
			recovery.add(insert(original, backupTable(member),
					original.getColumns().stream().filter(HsqlTableMigration::stored).toList(), source));
		}
		for (var key : sourceKeys)
			recovery.addAll(commands(registry.createSql(key, SqlType.CREATE)));
		if (generatedRoutines.isEmpty())
			restoreDependents(recovery, source, dependents);
		else
			restoreDependentDetails(recovery, source, dependents);
		for (var expressions : beforeExpressions.values())
			restoreExpressions(recovery, expressions);
		for (var id : selected)
			recovery.addAll(details(source, source.getSchemas().get(id.schema()), table(source, id)));
		return new BatchPlan(null, members, replace, restore, recovery,
				members.stream().flatMap(m -> m.cleanup().stream()).toList(), snapshots, routineSnapshots);
	}

	private record Dependents(List<HsqlDdlDependencies.ObjectId> objects, List<HsqlDdlDependencies.ObjectId> detached) {
	}

	/**
	 * Stable closure; modeled SQL routine bodies can detach cyclic edges safely.
	 */
	private Dependents dependents(Catalog catalog, java.util.Set<TableIdentity> tables, List<TypeReplacement> changes) {
		var affected = new java.util.LinkedHashSet<HsqlDdlDependencies.ObjectId>();
		for (var table : tables)
			affected.add(new HsqlDdlDependencies.ObjectId("RELATION", table.schema(), table.table()));
		var edges = catalog.getSchemas().stream().flatMap(s -> HsqlDdlDependencies.get(s).stream()).toList();
		var remaining = new java.util.LinkedHashSet<HsqlDdlDependencies.ObjectId>();
		for (var schema : catalog.getSchemas())
			for (var routine : routines(schema)) {
				if (changes.isEmpty())
					continue;
				String ddl = commands(registry.createSql(routine, SqlType.CREATE)).stream().map(Command::sql)
						.collect(Collectors.joining("\n"));
				if (changes.stream().anyMatch(c -> HsqlExpressionDependencies.mentionsIdentifier(ddl,
						c.original().schema(), c.original().name()))) {
					var id = routineId(routine);
					affected.add(id);
					remaining.add(id);
				}
			}
		boolean changed;
		do {
			changed = false;
			for (var edge : edges)
				if (affected.contains(edge.prerequisite()) && !affected.contains(edge.object())) {
					var id = edge.object();
					if ("ROUTINE".equals(id.kind()))
						routine(catalog, id);
					else if (!"RELATION".equals(id.kind()) || catalog.getSchemas().get(id.schema()) == null
							|| catalog.getSchemas().get(id.schema()).getViews().get(id.name()) == null)
						throw new IllegalArgumentException("Unresolved dependent object: " + id);
					affected.add(id);
					remaining.add(id);
					changed = true;
				}
		} while (changed);
		var ordered = new ArrayList<HsqlDdlDependencies.ObjectId>();
		var detached = new java.util.LinkedHashSet<HsqlDdlDependencies.ObjectId>();
		while (!remaining.isEmpty()) {
			var ready = remaining.stream().filter(id -> detached.contains(id)
					|| edges.stream().noneMatch(e -> e.object().equals(id) && remaining.contains(e.prerequisite())))
					.toList();
			if (ready.isEmpty()) {
				for (var id : remaining)
					if ("ROUTINE".equals(id.kind())
							&& HsqlDdlDependencies.initializationStub(routine(catalog, id)) != null)
						detached.add(id);
				if (detached.stream().noneMatch(remaining::contains))
					throw new IllegalArgumentException(
							"Cyclic dependent views/routines require modeled SQL signatures and bodies");
				continue;
			}
			ordered.addAll(ready);
			remaining.removeAll(ready);
		}
		return new Dependents(List.copyOf(ordered), List.copyOf(detached));
	}

	private void detachRoutines(List<Command> result, Catalog catalog, List<HsqlDdlDependencies.ObjectId> ids,
			boolean guarded) {
		for (var id : ids) {
			var stub = Objects.requireNonNull(HsqlDdlDependencies.initializationStub(routine(catalog, id)));
			String guard = guarded
					? "SELECT COUNT(*) FROM INFORMATION_SCHEMA.ROUTINES WHERE SPECIFIC_SCHEMA=" + literal(id.schema())
							+ " AND SPECIFIC_NAME=" + literal(id.name())
					: null;
			result.add(new Command("ALTER SPECIFIC ROUTINE " + quote(id.schema()) + "." + quote(id.name()) + " BODY "
					+ String.join("\n", stub.getStatement()), SqlType.ALTER, guard));
		}
	}

	private Command dropDependent(HsqlDdlDependencies.ObjectId id) {
		return new Command(("ROUTINE".equals(id.kind()) ? "DROP SPECIFIC ROUTINE " : "DROP VIEW ") + quote(id.schema())
				+ "." + quote(id.name()) + " IF EXISTS", SqlType.DROP);
	}

	private static List<com.sqlapp.data.schemas.ArgumentRoutine<?>> routines(Schema schema) {
		var result = new ArrayList<com.sqlapp.data.schemas.ArgumentRoutine<?>>();
		result.addAll(schema.getFunctions());
		result.addAll(schema.getProcedures());
		return result;
	}

	private void validateRoutineExpressions(Catalog catalog, List<HsqlDdlDependencies.ObjectId> ids,
			java.util.Set<TableIdentity> relations) {
		var affected = ids.stream().map(id -> routine(catalog, id)).toList();
		var functions = affected.stream().filter(r -> r instanceof com.sqlapp.data.schemas.Function).toList();
		if (affected.isEmpty())
			return;
		for (var schema : catalog.getSchemas()) {
			for (var trigger : schema.getTriggers())
				if (!relations.contains(new TableIdentity(trigger.getTableSchemaName(), trigger.getTableName()))) {
					var body = com.sqlapp.util.CommonUtils.isEmpty(trigger.getDefinition()) ? trigger.getStatement()
							: trigger.getDefinition();
					if (body == null)
						continue;
					for (var ref : HsqlExpressionDependencies.references(String.join("\n", body))) {
						if (ref.sequence())
							continue;
						var parts = ref.name();
						String name = parts.getLast();
						String owner = parts.size() > 1 ? parts.get(parts.size() - 2) : schema.getName();
						if (affected.stream().anyMatch(
								r -> Objects.equals(r.getName(), name) && Objects.equals(r.getSchemaName(), owner)))
							throw new IllegalArgumentException(
									"An unselected table/view trigger uses an affected routine: " + trigger.getName());
					}
				}
			var expressions = new ArrayList<String>();
			for (var table : schema.getTables()) {
				for (var column : table.getColumns()) {
					if (!relations.contains(identity(table)))
						expressions.add(column.getFormula());
					expressions.add(column.getDefaultValue());
				}
				if (!relations.contains(identity(table)))
					for (var check : table.getConstraints().getCheckConstraints())
						expressions.add(check.getExpression());
			}
			for (var domain : schema.getDomains()) {
				expressions.add(domain.getDefaultValue());
				expressions.add(domain.getCheck());
				for (var check : domain.getChecks())
					expressions.add(check.getExpression());
			}
			for (var expression : expressions)
				for (var ref : HsqlExpressionDependencies.references(expression)) {
					if (ref.sequence())
						continue;
					var parts = ref.name();
					String name = parts.getLast();
					String owner = parts.size() > 1 ? parts.get(parts.size() - 2) : schema.getName();
					if (functions.stream().anyMatch(
							f -> Objects.equals(f.getName(), name) && Objects.equals(f.getSchemaName(), owner)))
						throw new IllegalArgumentException(
								"Table/domain expressions using an affected routine require a separate migration: "
										+ owner + "." + name);
				}
		}
	}

	/**
	 * Generated columns keep their routine prerequisites until every referencing
	 * table is gone.
	 */
	private java.util.Set<HsqlDdlDependencies.ObjectId> generatedRoutines(Catalog source, Catalog target,
			java.util.Set<TableIdentity> tables, List<HsqlDdlDependencies.ObjectId> routines) {
		var required = new java.util.LinkedHashSet<HsqlDdlDependencies.ObjectId>();
		for (var catalog : List.of(source, target))
			for (var table : tables)
				for (var column : table(catalog, table).getColumns()) {
					for (var id : routines)
						if (callsRoutine(column.getFormula(), table.schema(), List.of(routine(catalog, id))))
							required.add(id);
				}
		var edges = source.getSchemas().stream().flatMap(s -> HsqlDdlDependencies.get(s).stream()).toList();
		boolean changed;
		do {
			changed = false;
			for (var edge : edges)
				if (required.contains(edge.object()) && routines.contains(edge.prerequisite()))
					changed |= required.add(edge.prerequisite());
		} while (changed);
		for (var catalog : List.of(source, target))
			for (var id : required) {
				var routine = routine(catalog, id);
				if (!(routine instanceof com.sqlapp.data.schemas.Function function)
						|| !Boolean.TRUE.equals(routine.getDeterministic())
						|| routine.getSqlDataAccess() != com.sqlapp.data.schemas.SqlDataAccess.ContainsSql
								&& routine.getSqlDataAccess() != com.sqlapp.data.schemas.SqlDataAccess.NoSql
						|| function.getReturning().getTable() != null)
					throw new IllegalArgumentException(
							"Generated expressions require deterministic scalar routines without SQL data reads: "
									+ id);
			}
		return java.util.Set.copyOf(required);
	}

	private void createBatchObjects(List<Command> result, Catalog catalog,
			java.util.Collection<TableExpressions> tables, List<HsqlDdlDependencies.ObjectId> dependents) {
		var operations = new ArrayList<SqlOperation>();
		for (var expressions : tables) {
			var table = expressions.staged().clone();
			table.getConstraints().removeIf(c -> c instanceof com.sqlapp.data.schemas.ForeignKeyConstraint);
			operations.addAll(registry.createSql(table, SqlType.CREATE));
		}
		for (var id : dependents)
			operations.addAll(registry.createSql("ROUTINE".equals(id.kind()) ? routine(catalog, id)
					: catalog.getSchemas().get(id.schema()).getViews().get(id.name()), SqlType.CREATE));
		result.addAll(commands(HsqlDdlDependencies.order(operations, catalog.getSchemas(), registry)));
	}

	private record TableExpressions(Table complete, Table staged,
			List<com.sqlapp.data.schemas.CheckConstraint> checks) {
	}

	private TableExpressions tableExpressions(Catalog catalog, Table original,
			List<HsqlDdlDependencies.ObjectId> routines) {
		var functions = routines.stream().map(id -> routine(catalog, id))
				.filter(r -> r instanceof com.sqlapp.data.schemas.Function).toList();
		var complete = original.clone().setReadonly(false);
		var checks = complete.getConstraints().getCheckConstraints().stream()
				.filter(c -> callsRoutine(c.getExpression(), original.getSchemaName(), functions)).toList();
		if (!checks.isEmpty() && !com.sqlapp.util.CommonUtils.isEmpty(original.getDefinition()))
			throw new IllegalArgumentException(
					"Deferred table expressions require modeled table components: " + name(original));
		for (var check : checks)
			if (check.getName() == null || check.getName().isBlank())
				check.setName("SQLAPP_CHECK_"
						+ hash(new com.sqlapp.util.JsonConverter().toJsonString(List.of(original.getSchemaName(),
								original.getName(), check.getExpression(), complete.getConstraints().indexOf(check))))
								.substring(0, 20).toUpperCase(java.util.Locale.ROOT));
		var staged = complete.clone();
		for (var check : checks)
			staged.getConstraints().remove(check.getName());
		return new TableExpressions(complete, staged, checks);
	}

	private static boolean callsRoutine(String expression, String schema,
			List<? extends com.sqlapp.data.schemas.ArgumentRoutine<?>> functions) {
		return callsRoutineNames(expression, schema, functions.stream()
				.map(f -> new RoutineName(f.getSchemaName(), f.getName())).collect(Collectors.toSet()));
	}

	private record RoutineName(String schema, String name) {
	}

	private static boolean callsRoutineNames(String expression, String schema, java.util.Set<RoutineName> functions) {
		for (var ref : HsqlExpressionDependencies.references(expression)) {
			if (ref.sequence())
				continue;
			var parts = ref.name();
			String owner = parts.size() > 1 ? parts.get(parts.size() - 2) : schema;
			if (functions.contains(new RoutineName(owner, parts.getLast())))
				return true;
		}
		return false;
	}

	private void dropExpressions(List<Command> result, TableExpressions expressions, boolean guarded) {
		var table = expressions.complete();
		for (var check : expressions.checks()) {
			String guard = guarded
					? "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA="
							+ literal(table.getSchemaName()) + " AND TABLE_NAME=" + literal(table.getName())
							+ " AND CONSTRAINT_NAME=" + literal(check.getName()) + " AND CONSTRAINT_TYPE='CHECK'"
					: null;
			result.add(new Command("ALTER TABLE " + name(table) + " DROP CONSTRAINT " + quote(check.getName()),
					SqlType.ALTER, guard));
		}
	}

	private void restoreExpressions(List<Command> result, TableExpressions expressions) {
		for (var check : expressions.checks())
			result.addAll(commands(registry.createSql(check, SqlType.CREATE)));
	}

	private static HsqlDdlDependencies.ObjectId routineId(com.sqlapp.data.schemas.ArgumentRoutine<?> routine) {
		return new HsqlDdlDependencies.ObjectId("ROUTINE", routine.getSchemaName(),
				Objects.requireNonNull(routine.getSpecificName(), "Complete routine specific names are required"));
	}

	private static com.sqlapp.data.schemas.ArgumentRoutine<?> routine(Catalog catalog,
			HsqlDdlDependencies.ObjectId id) {
		var schema = Objects.requireNonNull(catalog.getSchemas().get(id.schema()),
				"Missing routine schema: " + id.schema());
		return routines(schema).stream().filter(r -> Objects.equals(r.getSpecificName(), id.name())).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unresolved dependent routine: " + id));
	}

	private static String routinePrivileges(Catalog catalog, String schema, String name) {
		var json = new com.sqlapp.util.JsonConverter();
		return catalog.getRoutinePrivileges().stream()
				.filter(p -> Objects.equals(schema, p.getSchemaName()) && Objects.equals(name, p.getObjectName()))
				.map(p -> json.toJsonString(java.util.Arrays.asList(p.getSchemaName(), p.getObjectName(),
						p.getSpecificName(), p.getGrantorName(), p.getGranteeName(), p.getPrivilege(), p.isGrantable(),
						p.isHierachy(), p.getSpecifics())))
				.sorted().collect(Collectors.joining("\n"));
	}

	private void dropDependentTriggers(List<Command> result, Catalog catalog, java.util.Set<TableIdentity> tables,
			List<HsqlDdlDependencies.ObjectId> views) {
		if (views.isEmpty())
			return;
		var relations = new java.util.HashSet<>(tables);
		views.stream().filter(v -> "RELATION".equals(v.kind()))
				.forEach(v -> relations.add(new TableIdentity(v.schema(), v.name())));
		for (var schema : catalog.getSchemas())
			for (var trigger : schema.getTriggers())
				if (relations.contains(new TableIdentity(trigger.getTableSchemaName(), trigger.getTableName()))) {
					result.add(new Command("DROP TRIGGER " + quote(trigger.getSchemaName()) + "."
							+ quote(trigger.getName()) + " IF EXISTS", SqlType.DROP));
				}
	}

	private void restoreDependents(List<Command> result, Catalog catalog,
			List<HsqlDdlDependencies.ObjectId> dependents) {
		var operations = new ArrayList<SqlOperation>();
		for (var id : dependents)
			operations.addAll(registry.createSql("ROUTINE".equals(id.kind()) ? routine(catalog, id)
					: catalog.getSchemas().get(id.schema()).getViews().get(id.name()), SqlType.CREATE));
		result.addAll(commands(HsqlDdlDependencies.order(operations, catalog.getSchemas(), registry)));
		restoreDependentDetails(result, catalog, dependents);
	}

	private void restoreDependentDetails(List<Command> result, Catalog catalog,
			List<HsqlDdlDependencies.ObjectId> dependents) {
		var granted = new java.util.HashSet<TableIdentity>();
		var routineGrants = new java.util.LinkedHashSet<Command>();
		for (var id : dependents)
			if ("ROUTINE".equals(id.kind())) {
				String name = routine(catalog, id).getName();
				if (!granted.add(new TableIdentity(id.schema(), name)))
					continue;
				for (var p : catalog.getRoutinePrivileges())
					if (Objects.equals(p.getSchemaName(), id.schema()) && Objects.equals(p.getObjectName(), name))
						routineGrants.addAll(commands(registry.createSql(p, SqlType.GRANT)));
			}
		result.addAll(routineGrants);
		var views = dependents.stream().filter(id -> "RELATION".equals(id.kind())).toList();
		for (var id : views) {
			for (var trigger : catalog.getSchemas().get(id.schema()).getTriggers())
				if (Objects.equals(trigger.getTableSchemaName(), id.schema())
						&& Objects.equals(trigger.getTableName(), id.name()))
					result.addAll(commands(registry.createSql(trigger, SqlType.CREATE)));
			for (var p : catalog.getObjectPrivileges())
				if (matches(p, id.schema(), id.name()))
					result.addAll(commands(registry.createSql(p, SqlType.GRANT)));
			for (var p : catalog.getColumnPrivileges())
				if (matches(p, id.schema(), id.name()))
					result.addAll(commands(registry.createSql(p, SqlType.GRANT)));
		}
	}

	private static boolean stored(Column column) {
		return column.getFormula() == null || column.getFormula().isBlank();
	}

	private static TableIdentity identity(Table table) {
		return new TableIdentity(table.getSchemaName(), table.getName());
	}

	private static java.util.Set<TableIdentity> tableIdentities(Catalog catalog) {
		return catalog.getSchemas().stream().flatMap(s -> s.getTables().stream()).map(HsqlTableMigration::identity)
				.collect(Collectors.toSet());
	}

	private static Table table(Catalog catalog, TableIdentity id) {
		var schema = Objects.requireNonNull(catalog.getSchemas().get(id.schema()),
				"Missing batch schema: " + id.schema());
		return Objects.requireNonNull(schema.getTables().get(id.table()), "Missing batch table: " + id);
	}

	private Table backupTable(Plan plan) {
		var names = HsqlExpressionDependencies.typeName(plan.backupTable());
		if (names.size() != 2)
			throw new IllegalArgumentException("Invalid backup identity");
		return new Table(names.get(1)).setSchemaName(names.get(0));
	}

	private List<Command> createWithoutKeys(Table original) {
		var copy = original.clone().setReadonly(false);
		copy.getConstraints().removeIf(c -> c instanceof com.sqlapp.data.schemas.ForeignKeyConstraint);
		return commands(registry.createSql(copy, SqlType.CREATE));
	}

	private List<com.sqlapp.data.schemas.ForeignKeyConstraint> keys(Catalog catalog,
			java.util.Set<TableIdentity> selected) {
		return catalog.getSchemas().stream().flatMap(s -> s.getTables().stream())
				.flatMap(t -> t.getConstraints().getForeignKeyConstraints().stream())
				.filter(f -> selected.contains(identity(f.getTable()))
						|| selected.contains(new TableIdentity(f.getRelatedTableSchemaName(), f.getRelatedTableName())))
				.toList();
	}

	private static String keyIdentity(com.sqlapp.data.schemas.ForeignKeyConstraint key) {
		return new com.sqlapp.util.JsonConverter()
				.toJsonString(List.of(key.getSchemaName(), key.getTableName(), key.getName()));
	}

	private Command dropKey(com.sqlapp.data.schemas.ForeignKeyConstraint key, boolean guarded) {
		return new Command("ALTER TABLE " + name(key.getTable()) + " DROP CONSTRAINT " + quote(key.getName()),
				SqlType.ALTER,
				guarded ? "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA="
						+ literal(key.getSchemaName()) + " AND CONSTRAINT_NAME=" + literal(key.getName())
						+ " AND TABLE_SCHEMA=" + literal(key.getSchemaName()) + " AND TABLE_NAME="
						+ literal(key.getTableName()) + " AND CONSTRAINT_TYPE='FOREIGN KEY'" : null);
	}

	private void addTypeRecovery(List<Command> recovery, TypeReplacement change) {
		recovery.add(dropType(change.original(), "SELECT CASE WHEN (" + change.existenceQuery() + ")>0 AND ("
				+ change.originalQuery() + ")=0 THEN 1 ELSE 0 END FROM (VALUES(0))"));
		recovery.add(new Command(change.original().createSql(), SqlType.CREATE,
				"SELECT CASE WHEN (" + change.existenceQuery() + ")=0 THEN 1 ELSE 0 END FROM (VALUES(0))"));
		recovery.addAll(change.grants());
	}

	public Plan plan(Catalog source, Catalog target, Table table) {
		return plan(source, target, table, ConversionPolicy.LOSSLESS);
	}

	/**
	 * Source/target catalogs must contain complete schema, trigger, FK and
	 * privilege metadata.
	 */
	public Plan plan(Catalog source, Catalog target, Table table, ConversionPolicy policy) {
		return plan(source, target, table, policy, null);
	}

	private Plan plan(Catalog source, Catalog target, Table table, ConversionPolicy policy, Scope scope) {
		Objects.requireNonNull(policy, "conversion policy");
		String schemaName = Objects.requireNonNull(table.getSchemaName(), "table schema");
		Schema originalSchema = Objects.requireNonNull(source.getSchemas().get(schemaName), "source schema");
		Schema targetSchema = Objects.requireNonNull(target.getSchemas().get(schemaName), "target schema");
		Table original = Objects.requireNonNull(originalSchema.getTables().get(table.getName()), "source table");
		Table desired = Objects.requireNonNull(targetSchema.getTables().get(table.getName()), "target table");
		if (!table.equals(desired))
			throw new IllegalArgumentException("Target table must match the target catalog");
		if (HsqlTextTableSources.isText(original) || HsqlTextTableSources.isText(desired)
				|| original.getTableType() == Table.TableType.Temporary
				|| desired.getTableType() == Table.TableType.Temporary) {
			throw new IllegalArgumentException("TEXT and temporary table replacement requires a separate migration");
		}
		if (original.getColumns().size() != desired.getColumns().size()
				|| original.getColumns().stream().anyMatch(c -> desired.getColumns().get(c.getName()) == null)) {
			throw new IllegalArgumentException(
					"Table replacement requires the same column identities; plan column additions/removals/renames separately");
		}
		for (var column : original.getColumns())
			storage(column, source);
		for (var column : desired.getColumns())
			storage(column, target);
		var namedTypes = namedTypes(source, target, original, desired);
		var selected = scope == null ? java.util.Set.of(identity(original)) : scope.tables();
		var typeReplacements = scope == null ? typeReplacements(source, target, namedTypes, selected) : scope.changes();
		if (source.getSchemas().size() != target.getSchemas().size())
			throw new IllegalArgumentException("Plan other schema-object changes separately from table replacement");
		for (var schema : source.getSchemas()) {
			var before = schema.clone();
			var corresponding = target.getSchemas().get(schema.getName());
			if (corresponding == null)
				throw new IllegalArgumentException("Source and target schema identities must match");
			var after = corresponding.clone();
			for (var id : selected)
				if (id.schema().equals(schema.getName())) {
					before.getTables().remove(id.table());
					after.getTables().remove(id.table());
				}
			for (var change : typeReplacements)
				if (schema.getName().equals(change.original().schema())) {
					if ("DOMAIN".equals(change.original().kind())) {
						before.getDomains().remove(change.original().name());
						after.getDomains().remove(change.original().name());
					} else {
						before.getTypes().remove(change.original().name());
						after.getTypes().remove(change.original().name());
					}
				}
			if (!before.equals(after))
				throw new IllegalArgumentException(
						"Plan other schema-object changes separately from table replacement");
		}
		for (var schema : source.getSchemas()) {
			for (var edge : HsqlDdlDependencies.get(schema)) {
				if (edge.prerequisite()
						.equals(new HsqlDdlDependencies.ObjectId("RELATION", schemaName, original.getName()))
						&& !edge.object().equals(edge.prerequisite())
						&& !("RELATION".equals(edge.object().kind())
								&& selected.contains(new TableIdentity(edge.object().schema(), edge.object().name())))
						&& !(scope != null && scope.dependents().contains(edge.object()))) {
					throw new IllegalArgumentException("Migrate dependent view/routine first: " + edge.object());
				}
			}
		}
		String sourceXml = schemaXml(originalSchema);
		String sourceHash = hash(sourceXml);
		String targetHash = hash(schemaXml(targetSchema));
		String backupName = "SQLAPP_BACKUP_"
				+ hash(sourceHash + targetHash + policy + "\n" + schemaName + "\n" + table.getName()).substring(0, 20)
						.toUpperCase(java.util.Locale.ROOT);
		if (originalSchema.getTables().get(backupName) != null)
			throw new IllegalArgumentException("Backup table already exists: " + backupName);
		Table saved = new Table(backupName).setSchemaName(schemaName).setTableType(
				original.getTableType() == Table.TableType.Cache ? Table.TableType.Cache : Table.TableType.Memory);
		for (var column : original.getColumns()) {
			Column copy = column.clone().setIdentity(false).setSequence(null).setFormula(null).setDefaultValue(null);
			Column base = storage(column, source);
			copy.setDataType(base.getDataType()).setDataTypeName(base.getDataTypeName()).setLength(base.getLength())
					.setScale(base.getScale());
			saved.getColumns().add(copy);
		}
		String originalName = name(original), backupNameSql = name(saved);
		String all = names(original.getColumns().stream().toList());
		var backup = commands(registry.createSql(saved, SqlType.CREATE));
		String sourceValues = original.getColumns().stream().map(c -> cast(quote(c.getName()), storage(c, source)))
				.collect(Collectors.joining(", "));
		backup.add(new Command(
				"INSERT INTO " + backupNameSql + " (" + all + ") SELECT " + sourceValues + " FROM " + originalName,
				SqlType.INSERT));
		String backupVerification = difference("SELECT " + sourceValues + " FROM " + originalName,
				"SELECT " + all + " FROM " + backupNameSql);
		var physical = desired.getColumns().stream().filter(c -> c.getFormula() == null || c.getFormula().isBlank())
				.toList();
		if (physical.isEmpty())
			throw new IllegalArgumentException("At least one stored target column is required");
		var checks = new ArrayList<String>();
		if (policy == ConversionPolicy.LOSSLESS) {
			for (var column : physical) {
				var old = original.getColumns().get(column.getName());
				boolean changingType = namedColumn(column) && typeReplacements.stream()
						.anyMatch(c -> typeNameMatches(column, c.target().schema(), c.target().name()));
				// The target named definition is not installed yet; validate its modeled base.
				String converted = changingType ? cast(quote(old.getName()), storage(column, target))
						: converted(quote(old.getName()), column, target);
				checks.add("SELECT COUNT(*) FROM " + backupNameSql + " WHERE " + quote(old.getName())
						+ " IS DISTINCT FROM " + cast(cast(converted, storage(column, target)), storage(old, source)));
			}
		}
		for (var column : physical)
			if (column.isNotNull()) {
				checks.add("SELECT COUNT(*) FROM " + backupNameSql + " WHERE " + quote(column.getName()) + " IS NULL");
			}
		var replace = new ArrayList<Command>();
		var inbound = new ArrayList<com.sqlapp.data.schemas.ForeignKeyConstraint>();
		for (var schema : source.getSchemas())
			for (var other : schema.getTables()) {
				if (Objects.equals(other.getSchemaName(), schemaName)
						&& Objects.equals(other.getName(), original.getName()))
					continue;
				for (var fk : other.getConstraints().getForeignKeyConstraints()) {
					if (Objects.equals(fk.getRelatedTableSchemaName(), schemaName)
							&& Objects.equals(fk.getRelatedTableName(), original.getName()))
						inbound.add(fk);
				}
			}
		for (var fk : inbound)
			replace.add(dropKey(fk, false));
		replace.add(new Command("DROP TABLE " + originalName, SqlType.DROP)); // deliberately no CASCADE
		for (var change : typeReplacements) {
			replace.add(dropType(change.original(), null));
			replace.add(new Command(change.target().createSql(), SqlType.CREATE));
			replace.addAll(change.grants());
		}
		replace.addAll(commands(registry.createSql(desired.clone().setReadonly(false), SqlType.CREATE)));
		var restore = new ArrayList<Command>();
		restore.add(insert(desired, saved, physical, target));
		for (var fk : inbound)
			restore.addAll(commands(registry.createSql(fk, SqlType.CREATE)));
		restore.addAll(details(target, targetSchema, desired));
		var recovery = new ArrayList<Command>();
		for (var fk : inbound)
			recovery.add(dropKey(fk, true));
		recovery.add(new Command("DROP TABLE " + originalName + " IF EXISTS", SqlType.DROP));
		for (var change : typeReplacements)
			addTypeRecovery(recovery, change);
		recovery.addAll(commands(registry.createSql(original.clone().setReadonly(false), SqlType.CREATE)));
		recovery.add(insert(original, saved,
				original.getColumns().stream().filter(c -> c.getFormula() == null || c.getFormula().isBlank()).toList(),
				source));
		for (var fk : inbound)
			recovery.addAll(commands(registry.createSql(fk, SqlType.CREATE)));
		recovery.addAll(details(source, originalSchema, original));
		String projected = physical.stream()
				.map(c -> cast(converted(quote(c.getName()), c, target), storage(c, target)))
				.collect(Collectors.joining(", "));
		String targetValues = physical.stream().map(c -> cast(quote(c.getName()), storage(c, target)))
				.collect(Collectors.joining(", "));
		String verification = difference("SELECT " + targetValues + " FROM " + originalName,
				"SELECT " + projected + " FROM " + backupNameSql);
		String privileges = privileges(source, schemaName, original.getName());
		if (!privileges.equals(privileges(target, schemaName, original.getName())))
			throw new IllegalArgumentException("Plan privilege changes separately from table replacement");
		return new Plan(schemaName, original.getName(), backupNameSql, sourceHash, targetHash, null, policy, sourceXml,
				privileges, foreignKeys(inbound), backup, checks, replace, restore, recovery,
				List.of(new Command("DROP TABLE " + backupNameSql, SqlType.DROP)), backupVerification, verification,
				"SELECT COUNT(*) FROM " + originalName, "SELECT COUNT(*) FROM " + backupNameSql,
				"SELECT COUNT(*) FROM " + originalName, "SELECT " + all + " FROM " + backupNameSql + " ORDER BY " + all,
				namedTypes, typeReplacements);
	}

	/**
	 * Requires an administrator, a dedicated auto-commit connection and
	 * caller-enforced exclusive access.
	 */
	public BatchResult execute(Connection connection, BatchPlan plan, Access access) throws SQLException {
		Phase phase = Phase.PREFLIGHT;
		var backups = new ArrayList<Backup>();
		try {
			requireAccess(connection, access);
			preflight(connection, plan.members(), plan.dependentViews(), plan.dependentRoutines());
			verifyBatchDependencies(connection, plan);
			verifyRoutineTypeUsage(connection, plan.members(), plan.dependentRoutines());
			verifyExpressionOwners(connection, plan);
			var counts = new java.util.LinkedHashMap<TableIdentity, Long>();
			for (var member : plan.members())
				counts.put(new TableIdentity(member.schema(), member.table()),
						scalar(connection, member.sourceCount()));
			phase = Phase.BACKUP;
			for (var member : plan.members()) {
				var id = new TableIdentity(member.schema(), member.table());
				run(connection, member.backup());
				if (scalar(connection, member.backupCount()) != counts.get(id)
						|| scalar(connection, member.backupVerification()) != 0)
					throw new IllegalStateException("Backup verification failed: " + id);
				backups.add(new Backup(id, member.backupTable(), counts.get(id),
						dataFingerprint(connection, member.backupRows())));
			}
			phase = Phase.VALIDATE;
			for (var member : plan.members())
				for (var query : member.validationQueries())
					if (scalar(connection, query) != 0)
						throw new IllegalArgumentException(
								"Conversion would lose data or violate nullability: " + member.table());
			phase = Phase.REPLACE;
			run(connection, plan.replace());
			phase = Phase.RESTORE;
			run(connection, plan.restore());
			phase = Phase.VERIFY;
			for (var member : plan.members())
				if (scalar(connection, member.targetCount()) != counts
						.get(new TableIdentity(member.schema(), member.table()))
						|| scalar(connection, member.targetVerification()) != 0)
					throw new IllegalStateException(
							"Restored batch data differs from the planned conversion: " + member.table());
			verifyAllPrivileges(connection, plan.members(), plan.dependentViews(), plan.dependentRoutines());
			verifyNamedTypes(connection,
					plan.members().stream().flatMap(p -> expectedTypes(p, true, false).stream()).distinct().toList());
			return new BatchResult(plan.fingerprint(), Phase.COMPLETE, backups);
		} catch (SQLException | RuntimeException failure) {
			throw new BatchFailure(plan, phase, failure, backups);
		}
	}

	public BatchResult recover(Connection connection, BatchFailure failure, Access access) throws SQLException {
		var plan = failure.getPlan();
		try {
			requireAccess(connection, access);
			if (failure.getBackups().size() != plan.members().size() || !java.util.Set
					.of(Phase.REPLACE, Phase.RESTORE, Phase.VERIFY, Phase.RECOVER).contains(failure.getPhase()))
				throw new IllegalArgumentException(
						"Batch recovery requires all verified backups and replacement to have begun");
			var backups = failure.getBackups().stream().collect(Collectors.toMap(Backup::table, b -> b));
			var ownerSchemas = new java.util.LinkedHashSet<String>();
			for (var member : plan.members()) {
				ownerSchemas.add(member.schema());
				member.typeReplacements().forEach(t -> ownerSchemas.add(t.original().schema()));
				var backup = Objects.requireNonNull(backups.get(new TableIdentity(member.schema(), member.table())),
						"Missing member backup");
				if (!backup.dataFingerprint().equals(dataFingerprint(connection, member.backupRows())))
					throw new IllegalArgumentException("Batch backup changed: " + member.table());
			}
			for (String schema : ownerSchemas)
				requireSchemaOwner(connection, schema);
			for (String schema : java.util.stream.Stream
					.concat(plan.dependentViews().stream().map(DependentView::schema),
							plan.dependentRoutines().stream().map(DependentRoutine::schema))
					.distinct().toList())
				if (!ownerSchemas.contains(schema))
					requireSchemaOwner(connection, schema);
			verifyNamedTypes(connection,
					plan.members().stream().flatMap(p -> expectedTypes(p, false, true).stream()).distinct().toList());
			run(connection, plan.recovery());
			for (var member : plan.members())
				if (scalar(connection, member.backupVerification()) != 0)
					throw new IllegalStateException(
							"Recovered table differs from its original backup: " + member.table());
			verifyAllPrivileges(connection, plan.members(), plan.dependentViews(), plan.dependentRoutines());
			verifyNamedTypes(connection,
					plan.members().stream().flatMap(p -> expectedTypes(p, false, false).stream()).distinct().toList());
			return new BatchResult(plan.fingerprint(), Phase.RECOVERED, failure.getBackups());
		} catch (SQLException | RuntimeException problem) {
			throw new BatchFailure(plan, Phase.RECOVER, problem, failure.getBackups());
		}
	}

	private void preflight(Connection connection, List<Plan> plans) throws SQLException {
		preflight(connection, plans, List.of(), List.of());
	}

	private void preflight(Connection connection, List<Plan> plans, List<DependentView> views,
			List<DependentRoutine> routines) throws SQLException {
		verifyNamedTypes(connection,
				plans.stream().flatMap(p -> expectedTypes(p, false, false).stream()).distinct().toList());
		var hashes = new java.util.HashMap<String, String>();
		var owners = new java.util.HashMap<String, String>();
		var reader = registry.getDialect().getCatalogReader().getSchemaReader();
		for (String schema : java.util.stream.Stream.of(plans.stream().map(Plan::schema),
				views.stream().map(DependentView::schema), routines.stream().map(DependentRoutine::schema))
				.flatMap(v -> v).distinct().toList()) {
			reader.setSchemaName(schema);
			var found = reader.getAllFull(connection);
			if (found.size() != 1)
				throw new IllegalArgumentException("Missing migration schema: " + schema);
			hashes.put(schema, hash(schemaXml(found.getFirst())));
			owners.put(schema, requireSchemaOwner(connection, schema));
		}
		var actual = readPrivileges(connection);
		for (var routine : routines) {
			if (!Objects.equals(hashes.get(routine.schema()), routine.sourceFingerprint()))
				throw new IllegalArgumentException("Dependent routine schema changed; rebuild the migration plan");
			if (!routinePrivileges(actual, routine.schema(), routine.name()).equals(routine.privileges()))
				throw new IllegalArgumentException("Dependent routine privileges changed or were not read completely");
			for (var p : actual.getRoutinePrivileges())
				if (Objects.equals(p.getSchemaName(), routine.schema())
						&& Objects.equals(p.getObjectName(), routine.name())
						&& !Objects.equals(p.getGrantorName(), owners.get(routine.schema())))
					throw new IllegalArgumentException(
							"Delegated routine grants require a separate privilege migration");
		}
		for (var view : views) {
			if (!Objects.equals(hashes.get(view.schema()), view.sourceFingerprint()))
				throw new IllegalArgumentException("Dependent view schema changed; rebuild the migration plan");
			if (!privileges(actual, view.schema(), view.name()).equals(view.privileges()))
				throw new IllegalArgumentException("Dependent view privileges changed or were not read completely");
			String owner = owners.get(view.schema());
			for (var p : actual.getObjectPrivileges())
				if (matches(p, view.schema(), view.name()) && !Objects.equals(owner, p.getGrantorName()))
					throw new IllegalArgumentException(
							"Delegated view grant provenance requires a separate privilege migration");
			for (var p : actual.getColumnPrivileges())
				if (matches(p, view.schema(), view.name()) && !Objects.equals(owner, p.getGrantorName()))
					throw new IllegalArgumentException(
							"Delegated view grant provenance requires a separate privilege migration");
		}
		var references = registry.getDialect().getCatalogReader().getSchemaReader().getTableReader()
				.getForeignKeyConstraintReader().getAll(connection);
		verifyTypeUsage(connection, plans, views);
		for (var plan : plans) {
			if (!Objects.equals(hashes.get(plan.schema()), plan.sourceFingerprint()))
				throw new IllegalArgumentException(
						"Source schema changed or was not read completely; rebuild the migration plan");
			verifyTypePrivileges(connection, plan, actual, owners);
			String owner = owners.get(plan.schema());
			for (var privilege : actual.getObjectPrivileges())
				if (matches(privilege, plan.schema(), plan.table())
						&& !Objects.equals(owner, privilege.getGrantorName()))
					throw new IllegalArgumentException(
							"Delegated grant provenance requires a separate privilege migration");
			for (var privilege : actual.getColumnPrivileges())
				if (matches(privilege, plan.schema(), plan.table())
						&& !Objects.equals(owner, privilege.getGrantorName()))
					throw new IllegalArgumentException(
							"Delegated grant provenance requires a separate privilege migration");
			if (!privileges(actual, plan.schema(), plan.table()).equals(plan.sourcePrivileges()))
				throw new IllegalArgumentException(
						"Table privileges changed or were not read completely; rebuild the migration plan");
			var incoming = references.stream()
					.filter(fk -> Objects.equals(fk.getRelatedTableSchemaName(), plan.schema())
							&& Objects.equals(fk.getRelatedTableName(), plan.table())
							&& !(Objects.equals(fk.getSchemaName(), plan.schema())
									&& Objects.equals(fk.getTableName(), plan.table())))
					.toList();
			if (!foreignKeys(incoming).equals(plan.inboundFingerprint()))
				throw new IllegalArgumentException(
						"Incoming foreign keys changed or the source catalog was incomplete");
		}
	}

	private void verifyBatchDependencies(Connection connection, BatchPlan plan) {
		var plans = plan.members();
		var views = plan.dependentViews();
		var routines = plan.dependentRoutines();
		var reader = registry.getDialect().getCatalogReader().getSchemaReader();
		var affected = new java.util.HashSet<HsqlDdlDependencies.ObjectId>();
		plans.forEach(p -> affected.add(new HsqlDdlDependencies.ObjectId("RELATION", p.schema(), p.table())));
		views.forEach(v -> affected.add(new HsqlDdlDependencies.ObjectId("RELATION", v.schema(), v.name())));
		routines.forEach(r -> affected.add(new HsqlDdlDependencies.ObjectId("ROUTINE", r.schema(), r.specificName())));
		if (!(reader instanceof com.sqlapp.data.db.dialect.hsql.metadata.HsqlSchemaReader hsql))
			throw new IllegalArgumentException("A complete HSQLDB dependency reader is required");
		for (var edge : hsql.getDependencies(connection))
			if (affected.contains(edge.prerequisite()) && !affected.contains(edge.object()))
				throw new IllegalArgumentException(
						"Dependent object is outside the supplied migration catalog: " + edge.object());
	}

	private void verifyRoutineTypeUsage(Connection connection, List<Plan> plans, List<DependentRoutine> routines)
			throws SQLException {
		var changes = plans.stream().flatMap(p -> p.typeReplacements().stream()).distinct().toList();
		if (changes.isEmpty())
			return;
		var selected = routines.stream()
				.map(r -> new HsqlDdlDependencies.ObjectId("ROUTINE", r.schema(), r.specificName()))
				.collect(Collectors.toSet());
		try (var statement = connection.createStatement();
				var rs = statement.executeQuery(
						"SELECT SPECIFIC_SCHEMA,SPECIFIC_NAME,ROUTINE_DEFINITION FROM INFORMATION_SCHEMA.ROUTINES")) {
			while (rs.next()) {
				var id = new HsqlDdlDependencies.ObjectId("ROUTINE", rs.getString(1), rs.getString(2));
				String ddl = rs.getString(3);
				if (selected.contains(id) || ddl == null)
					continue;
				if (changes.stream().anyMatch(c -> HsqlExpressionDependencies.mentionsIdentifier(ddl,
						c.original().schema(), c.original().name())))
					throw new IllegalArgumentException(
							"Named type routine use is outside the selected migration: " + id);
			}
		}
	}

	/**
	 * Set-based checks also cover owners in schemas omitted from the input catalog.
	 */
	private void verifyExpressionOwners(Connection connection, BatchPlan plan) {
		if (plan.dependentRoutines().isEmpty())
			return;
		var functions = plan.dependentRoutines().stream().map(r -> new RoutineName(r.schema(), r.name()))
				.collect(Collectors.toSet());
		var tables = plan.members().stream().map(p -> new TableIdentity(p.schema(), p.table()))
				.collect(Collectors.toSet());
		var reader = registry.getDialect().getCatalogReader().getSchemaReader();
		for (var column : reader.getTableReader().getColumnReader().getAll(connection))
			if (!tables.contains(new TableIdentity(column.getSchemaName(), column.getTableName()))
					&& (callsRoutineNames(column.getFormula(), column.getSchemaName(), functions)
							|| callsRoutineNames(column.getDefaultValue(), column.getSchemaName(), functions)))
				throw new IllegalArgumentException("An unselected column expression uses an affected routine: "
						+ column.getSchemaName() + "." + column.getTableName());
		for (var check : reader.getTableReader().getCheckConstraintReader().getAll(connection))
			if (!tables.contains(new TableIdentity(check.getSchemaName(), check.getTableName()))
					&& callsRoutineNames(check.getExpression(), check.getSchemaName(), functions))
				throw new IllegalArgumentException("An unselected CHECK uses an affected routine: "
						+ check.getSchemaName() + "." + check.getTableName());
		for (var domain : reader.getDomainReader().getAll(connection)) {
			if (callsRoutineNames(domain.getDefaultValue(), domain.getSchemaName(), functions)
					|| callsRoutineNames(domain.getCheck(), domain.getSchemaName(), functions)
					|| domain.getChecks().stream()
							.anyMatch(c -> callsRoutineNames(c.getExpression(), domain.getSchemaName(), functions)))
				throw new IllegalArgumentException("A domain expression uses an affected routine: "
						+ domain.getSchemaName() + "." + domain.getName());
		}
		plan.dependentViews().forEach(v -> tables.add(new TableIdentity(v.schema(), v.name())));
		for (var trigger : reader.getTriggerReader().getAll(connection))
			if (!tables.contains(new TableIdentity(trigger.getTableSchemaName(), trigger.getTableName()))) {
				var body = com.sqlapp.util.CommonUtils.isEmpty(trigger.getDefinition()) ? trigger.getStatement()
						: trigger.getDefinition();
				if (body != null && callsRoutineNames(String.join("\n", body), trigger.getSchemaName(), functions))
					throw new IllegalArgumentException("An unselected trigger uses an affected routine: "
							+ trigger.getSchemaName() + "." + trigger.getName());
			}
	}

	/**
	 * Requires an administrator, a dedicated auto-commit connection and
	 * caller-enforced exclusive access.
	 */
	public Result execute(Connection connection, Plan plan, Access access) throws SQLException {
		Phase phase = Phase.PREFLIGHT;
		String dataFingerprint = null;
		try {
			requireAccess(connection, access);
			preflight(connection, List.of(plan));
			long rows = scalar(connection, plan.sourceCount());
			phase = Phase.BACKUP;
			run(connection, plan.backup());
			if (scalar(connection, plan.backupCount()) != rows || scalar(connection, plan.backupVerification()) != 0) {
				throw new IllegalStateException("Backup verification failed; original table remains intact");
			}
			dataFingerprint = dataFingerprint(connection, plan.backupRows());
			phase = Phase.VALIDATE;
			for (var query : plan.validationQueries())
				if (scalar(connection, query) != 0) {
					throw new IllegalArgumentException(
							"Conversion would lose data or violate nullability; original table remains intact");
				}
			phase = Phase.REPLACE;
			run(connection, plan.replace());
			phase = Phase.RESTORE;
			run(connection, plan.restore());
			phase = Phase.VERIFY;
			if (scalar(connection, plan.targetCount()) != rows || scalar(connection, plan.targetVerification()) != 0) {
				throw new IllegalStateException(
						"Restored data differs from the planned conversion; retain backup and inspect recovery SQL");
			}
			verifyPrivileges(connection, plan);
			verifyNamedTypes(connection, expectedTypes(plan, true, false));
			return new Result(plan.fingerprint(), plan.backupTable(), rows, dataFingerprint, Phase.COMPLETE);
		} catch (SQLException | RuntimeException failure) {
			throw new Failure(plan, phase, failure, dataFingerprint);
		}
	}

	/**
	 * Explicit recovery only; verifies the retained backup before changing the
	 * failed replacement.
	 */
	public Result recover(Connection connection, Failure failure, Access access) throws SQLException {
		Plan plan = failure.getPlan();
		try {
			requireAccess(connection, access);
			requireSchemaOwner(connection, plan.schema());
			verifyNamedTypes(connection, expectedTypes(plan, false, true));
			if (failure.getDataFingerprint() == null || !java.util.Set
					.of(Phase.REPLACE, Phase.RESTORE, Phase.VERIFY, Phase.RECOVER).contains(failure.getPhase())) {
				throw new IllegalArgumentException(
						"Recovery is only available after verified backup and replacement began");
			}
			if (!failure.getDataFingerprint().equals(dataFingerprint(connection, plan.backupRows())))
				throw new IllegalArgumentException("Backup changed; recovery refused");
			run(connection, plan.recovery());
			if (scalar(connection, plan.backupVerification()) != 0)
				throw new IllegalStateException("Recovered table differs from original backup");
			verifyPrivileges(connection, plan);
			verifyNamedTypes(connection, plan);
			return new Result(plan.fingerprint(), plan.backupTable(), scalar(connection, plan.backupCount()),
					failure.getDataFingerprint(), Phase.RECOVERED);
		} catch (SQLException | RuntimeException problem) {
			throw new Failure(plan, Phase.RECOVER, problem, failure.getDataFingerprint());
		}
	}

	private static void requireAccess(Connection connection, Access access) throws SQLException {
		if (access != Access.EXCLUSIVE || !connection.getAutoCommit() || connection.isReadOnly())
			throw new IllegalArgumentException(
					"Use a dedicated writable auto-commit connection under exclusive database access");
		try (var statement = connection.createStatement();
				var rs = statement.executeQuery(
						"SELECT ADMIN FROM INFORMATION_SCHEMA.SYSTEM_USERS WHERE USER_NAME=CURRENT_USER")) {
			if (!rs.next() || !rs.getBoolean(1))
				throw new IllegalArgumentException("Administrator access is required to validate complete metadata");
		}
	}

	private static String requireSchemaOwner(Connection connection, String schema) throws SQLException {
		try (var statement = connection.createStatement();
				var rs = statement.executeQuery(
						"SELECT SCHEMA_OWNER, CURRENT_USER FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME="
								+ literal(schema))) {
			if (!rs.next())
				throw new IllegalArgumentException("Migration schema is missing");
			String owner = rs.getString(1);
			if (!owner.equals(rs.getString(2)) && scalar(connection,
					"SELECT COUNT(*) FROM INFORMATION_SCHEMA.ENABLED_ROLES WHERE ROLE_NAME=" + literal(owner)) == 0) {
				throw new IllegalArgumentException(
						"Connect as the schema owner or enable its owning role to validate complete privileges");
			}
			return owner;
		}
	}

	private void verifyPrivileges(Connection connection, Plan plan) throws SQLException {
		verifyAllPrivileges(connection, List.of(plan));
	}

	private Catalog readPrivileges(Connection connection) {
		var current = new Catalog();
		current.getObjectPrivileges()
				.addAll(registry.getDialect().getCatalogReader().getObjectPrivilegeReader().getAll(connection));
		current.getColumnPrivileges()
				.addAll(registry.getDialect().getCatalogReader().getColumnPrivilegeReader().getAll(connection));
		current.getRoutinePrivileges()
				.addAll(registry.getDialect().getCatalogReader().getRoutinePrivilegeReader().getAll(connection));
		return current;
	}

	private void verifyAllPrivileges(Connection connection, List<Plan> plans) throws SQLException {
		verifyAllPrivileges(connection, plans, List.of(), List.of());
	}

	private void verifyAllPrivileges(Connection connection, List<Plan> plans, List<DependentView> views,
			List<DependentRoutine> routines) throws SQLException {
		var current = readPrivileges(connection);
		for (var routine : routines)
			if (!routinePrivileges(current, routine.schema(), routine.name()).equals(routine.privileges()))
				throw new IllegalStateException("Restored routine privileges differ: " + routine.name());
		for (var view : views)
			if (!privileges(current, view.schema(), view.name()).equals(view.privileges()))
				throw new IllegalStateException("Restored view privileges differ: " + view.name());
		var owners = new java.util.HashMap<String, String>();
		for (var plan : plans) {
			verifyTypePrivileges(connection, plan, current, owners);
			if (!privileges(current, plan.schema(), plan.table()).equals(plan.sourcePrivileges()))
				throw new IllegalStateException(
						"Restored privileges differ from the verified source privileges: " + plan.table());
		}
	}

	private List<Command> details(Catalog catalog, Schema schema, Table table) {
		var result = new ArrayList<Command>();
		for (var trigger : schema.getTriggers())
			if (Objects.equals(trigger.getTableName(), table.getName())
					&& Objects.equals(trigger.getTableSchemaName(), table.getSchemaName()))
				result.addAll(commands(registry.createSql(trigger, SqlType.CREATE)));
		for (var p : catalog.getObjectPrivileges())
			if (matches(p, table.getSchemaName(), table.getName()))
				result.addAll(commands(registry.createSql(p, SqlType.GRANT)));
		for (var p : catalog.getColumnPrivileges())
			if (matches(p, table.getSchemaName(), table.getName()))
				result.addAll(commands(registry.createSql(p, SqlType.GRANT)));
		if (Boolean.TRUE.equals(table.getReadonly()))
			result.add(new Command("SET TABLE " + name(table) + " READ ONLY", SqlType.ALTER));
		return result;
	}

	private Command insert(Table table, Table backup, List<Column> columns, Catalog catalog) {
		String expressions = columns.stream().map(c -> converted(quote(c.getName()), c, catalog))
				.collect(Collectors.joining(", "));
		boolean identity = columns.stream().anyMatch(Column::isIdentity);
		return new Command("INSERT INTO " + name(table) + " (" + names(columns) + ")"
				+ (identity ? " OVERRIDING SYSTEM VALUE" : "") + " SELECT " + expressions + " FROM " + name(backup),
				SqlType.INSERT);
	}

	private static boolean matches(Object privilege, String schema, String table) {
		if (privilege instanceof com.sqlapp.data.schemas.ObjectPrivilege p
				&& p.getSpecifics().get("OBJECT_TYPE") != null && !"TABLE".equals(p.getSpecifics().get("OBJECT_TYPE")))
			return false;
		return privilege instanceof com.sqlapp.data.schemas.properties.SchemaNameGetter owner
				&& privilege instanceof com.sqlapp.data.schemas.properties.ObjectNameProperty<?> object
				&& Objects.equals(schema, owner.getSchemaName()) && Objects.equals(table, object.getObjectName());
	}

	private static String privileges(Catalog catalog, String schema, String table) {
		var values = new ArrayList<String>();
		var json = new com.sqlapp.util.JsonConverter();
		for (var p : catalog.getObjectPrivileges())
			if (matches(p, schema, table))
				values.add(json.toJsonString(java.util.Arrays.asList("TABLE", p.getSchemaName(), p.getObjectName(),
						p.getGrantorName(), p.getGranteeName(), p.getPrivilege(), p.isGrantable(), p.isHierachy())));
		for (var p : catalog.getColumnPrivileges())
			if (matches(p, schema, table))
				values.add(json.toJsonString(java.util.Arrays.asList("COLUMN", p.getSchemaName(), p.getObjectName(),
						p.getColumnName(), p.getGrantorName(), p.getGranteeName(), p.getPrivilege(), p.isGrantable())));
		values.sort(String::compareTo);
		return String.join("\n", values);
	}

	private static void comparable(Column column) {
		if (column.getArrayDimension() > 0 || column.getDataType() == null
				|| java.util.Set
						.of(com.sqlapp.data.db.datatype.DataType.BLOB, com.sqlapp.data.db.datatype.DataType.CLOB,
								com.sqlapp.data.db.datatype.DataType.OTHER, com.sqlapp.data.db.datatype.DataType.DOMAIN)
						.contains(column.getDataType())) {
			throw new IllegalArgumentException(
					"A scalar comparable built-in type is required for backup verification: " + column.getName());
		}
	}

	private List<NamedTypeSnapshot> namedTypes(Catalog source, Catalog target, Table original, Table desired) {
		var required = new java.util.LinkedHashSet<NamedTypeSnapshot>();
		for (var pair : List.of(java.util.Map.entry(source, original), java.util.Map.entry(target, desired))) {
			for (var column : pair.getValue().getColumns()) {
				if (column.getDataType() != com.sqlapp.data.db.datatype.DataType.DOMAIN
						&& column.getDataType() != com.sqlapp.data.db.datatype.DataType.OTHER)
					continue;
				for (var schema : pair.getKey().getSchemas()) {
					for (var domain : schema.getDomains())
						if (typeNameMatches(column, schema.getName(), domain.getName()))
							required.add(snapshot("DOMAIN", domain));
					for (var type : schema.getTypes())
						if (typeNameMatches(column, schema.getName(), type.getName()))
							required.add(snapshot("TYPE", type));
				}
			}
		}
		var originals = new java.util.LinkedHashSet<NamedTypeSnapshot>();
		for (var type : required) {
			var object = typeObject(source, type);
			if (object == null || typeObject(target, type) == null)
				throw new IllegalArgumentException(
						"Named type definitions must exist in both catalogs: " + type.schema() + "." + type.name());
			originals.add(snapshot(type.kind(), object));
		}
		return List.copyOf(originals);
	}

	private com.sqlapp.data.schemas.AbstractSchemaObject<?> typeObject(Catalog catalog, NamedTypeSnapshot type) {
		var schema = catalog.getSchemas().get(type.schema());
		return schema == null ? null
				: "DOMAIN".equals(type.kind()) ? schema.getDomains().get(type.name())
						: schema.getTypes().get(type.name());
	}

	private List<TypeReplacement> typeReplacements(Catalog source, Catalog target, List<NamedTypeSnapshot> types,
			java.util.Set<TableIdentity> selected) {
		var changes = new ArrayList<TypeReplacement>();
		for (var type : types) {
			var desired = snapshot(type.kind(), typeObject(target, type));
			if (type.equals(desired))
				continue;
			for (var schema : source.getSchemas())
				for (var other : schema.getTables()) {
					if (selected.contains(identity(other)))
						continue;
					if (other.getColumns().stream()
							.anyMatch(c -> namedColumn(c) && typeNameMatches(c, type.schema(), type.name()))) {
						throw new IllegalArgumentException(
								"Named type is used by a table outside the selected migration: " + type.name());
					}
				}
			var grants = new ArrayList<Command>();
			for (var privilege : source.getObjectPrivileges())
				if (typePrivilege(privilege, type))
					grants.addAll(commands(registry.createSql(privilege, SqlType.GRANT)));
			String privileges = typePrivileges(source, type);
			if (!privileges.equals(typePrivileges(target, type)))
				throw new IllegalArgumentException("Plan named type privilege changes separately");
			String view = "DOMAIN".equals(type.kind()) ? "DOMAINS" : "USER_DEFINED_TYPES";
			String prefix = "DOMAIN".equals(type.kind()) ? "DOMAIN" : "USER_DEFINED_TYPE";
			String identity = prefix + "_SCHEMA=" + literal(type.schema()) + " AND " + prefix + "_NAME="
					+ literal(type.name());
			String existence = "SELECT COUNT(*) FROM INFORMATION_SCHEMA." + view + " WHERE " + identity;
			var object = typeObject(source, type);
			String signature;
			if (object instanceof com.sqlapp.data.schemas.Domain domain) {
				String actualChecks = "SELECT DC.CONSTRAINT_NAME,CC.CHECK_CLAUSE FROM INFORMATION_SCHEMA.DOMAIN_CONSTRAINTS DC JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS CC ON DC.CONSTRAINT_SCHEMA=CC.CONSTRAINT_SCHEMA AND DC.CONSTRAINT_NAME=CC.CONSTRAINT_NAME WHERE DC.DOMAIN_SCHEMA="
						+ literal(type.schema()) + " AND DC.DOMAIN_NAME=" + literal(type.name());
				String expectedChecks = domain.getChecks().stream().map(
						c -> "SELECT " + literal(c.getName()) + "," + literal(c.getExpression()) + " FROM (VALUES(0))")
						.collect(Collectors.joining(" UNION ALL "));
				if (expectedChecks.isEmpty())
					expectedChecks = "SELECT CAST(NULL AS VARCHAR(128)),CAST(NULL AS VARCHAR(100000)) FROM (VALUES(0)) WHERE FALSE";
				String sourceType = domain.getSpecifics().get("hsql.domainSourceType.v1");
				if (sourceType == null)
					throw new IllegalArgumentException(
							"Read complete domain source metadata before planning a definition replacement: "
									+ domain.getName());
				signature = existence + " AND DTD_IDENTIFIER=" + literal(sourceType)
						+ " AND DOMAIN_DEFAULT IS NOT DISTINCT FROM "
						+ (domain.getDefaultValue() == null ? "NULL" : literal(domain.getDefaultValue())) + " AND ("
						+ difference(actualChecks, expectedChecks) + ")=0";
			} else {
				var distinct = (com.sqlapp.data.schemas.Type) object;
				if (distinct.getStatement().size() != 1)
					throw new IllegalArgumentException("Distinct type replacement requires a scalar source statement");
				signature = existence + " AND SOURCE_DTD_IDENTIFIER=" + literal(distinct.getStatement().get(0));
			}
			changes.add(new TypeReplacement(type, desired, existence, signature, privileges, grants));
		}
		return List.copyOf(changes);
	}

	private static boolean namedColumn(Column column) {
		return column.getDataType() == com.sqlapp.data.db.datatype.DataType.DOMAIN
				|| column.getDataType() == com.sqlapp.data.db.datatype.DataType.OTHER;
	}

	private Command dropType(NamedTypeSnapshot type, String guard) {
		return new Command("DROP " + type.kind() + " " + quote(type.schema()) + "." + quote(type.name()), SqlType.DROP,
				guard);
	}

	private boolean typePrivilege(com.sqlapp.data.schemas.ObjectPrivilege privilege, NamedTypeSnapshot type) {
		return Objects.equals(type.kind(), privilege.getSpecifics().get("OBJECT_TYPE"))
				&& Objects.equals(type.schema(), privilege.getSchemaName())
				&& Objects.equals(type.name(), privilege.getObjectName());
	}

	private String typePrivileges(Catalog catalog, NamedTypeSnapshot type) {
		var json = new com.sqlapp.util.JsonConverter();
		return catalog.getObjectPrivileges().stream().filter(p -> typePrivilege(p, type))
				.map(p -> json.toJsonString(java.util.Arrays.asList(p.getGrantorName(), p.getGranteeName(),
						p.getPrivilege(), p.isGrantable(), p.isHierachy())))
				.sorted().collect(Collectors.joining("\n"));
	}

	private void verifyTypePrivileges(Connection connection, Plan plan, Catalog actual,
			java.util.Map<String, String> owners) throws SQLException {
		for (var change : plan.typeReplacements()) {
			String schema = change.original().schema();
			if (!owners.containsKey(schema))
				owners.put(schema, requireSchemaOwner(connection, schema));
			String owner = owners.get(schema);
			for (var privilege : actual.getObjectPrivileges())
				if (typePrivilege(privilege, change.original()) && !Objects.equals(owner, privilege.getGrantorName()))
					throw new IllegalArgumentException("Delegated type grants require a separate migration");
			if (!typePrivileges(actual, change.original()).equals(change.privileges()))
				throw new IllegalArgumentException("Named type privileges changed or were not read completely");
		}
	}

	private void verifyTypeUsage(Connection connection, List<Plan> plans, List<DependentView> views) {
		var changes = plans.stream().flatMap(p -> p.typeReplacements().stream()).distinct().toList();
		if (changes.isEmpty())
			return;
		var selected = plans.stream().map(p -> new TableIdentity(p.schema(), p.table())).collect(Collectors.toSet());
		views.forEach(v -> selected.add(new TableIdentity(v.schema(), v.name())));
		var columns = registry.getDialect().getCatalogReader().getSchemaReader().getTableReader().getColumnReader()
				.getAll(connection);
		for (var column : columns)
			for (var change : changes) {
				if (namedColumn(column) && typeNameMatches(column, change.original().schema(), change.original().name())
						&& !selected.contains(new TableIdentity(column.getSchemaName(), column.getTableName())))
					throw new IllegalArgumentException(
							"Named type is used outside the selected tables: " + change.original().name());
			}
	}

	private List<NamedTypeSnapshot> expectedTypes(Plan plan, boolean target, boolean recovering) {
		var result = new ArrayList<>(plan.namedTypes());
		for (var change : plan.typeReplacements()) {
			result.remove(change.original());
			if (!recovering)
				result.add(target ? change.target() : change.original());
		}
		return result;
	}

	private NamedTypeSnapshot snapshot(String kind, com.sqlapp.data.schemas.AbstractSchemaObject<?> object) {
		if (object instanceof com.sqlapp.data.schemas.Domain domain
				&& com.sqlapp.util.CommonUtils.isEmpty(domain.getDefinition())
				&& (!com.sqlapp.util.CommonUtils.isEmpty(domain.getCheck()) || domain.isNotNull())) {
			var modeled = domain.clone();
			if (!com.sqlapp.util.CommonUtils.isEmpty(modeled.getCheck())) {
				addDomainCheck(modeled, modeled.getCheck());
				modeled.setCheck(null);
			}
			if (modeled.isNotNull()) {
				addDomainCheck(modeled, "VALUE IS NOT NULL");
				modeled.setNullable(true);
			}
			object = modeled;
		}
		return new NamedTypeSnapshot(kind, object.getSchemaName(), object.getName(),
				registry.createSql(object, SqlType.CREATE).stream().map(SqlOperation::getSqlText)
						.collect(Collectors.joining("\n")));
	}

	private static void addDomainCheck(com.sqlapp.data.schemas.Domain domain, String expression) {
		String base = "SQLAPP_DOMAIN_" + hash(domain.getSchemaName() + "\n" + domain.getName() + "\n" + expression)
				.substring(0, 20).toUpperCase(java.util.Locale.ROOT);
		String name = base;
		for (int suffix = 1; domain.getChecks().contains(name); suffix++)
			name = base + "_" + suffix;
		domain.getChecks().add(new com.sqlapp.data.schemas.DomainCheckConstraint(name, expression));
	}

	private void verifyNamedTypes(Connection connection, Plan plan) {
		verifyNamedTypes(connection, plan.namedTypes());
	}

	private void verifyNamedTypes(Connection connection, List<NamedTypeSnapshot> expected) {
		if (expected.isEmpty())
			return;
		var reader = registry.getDialect().getCatalogReader().getSchemaReader();
		var actual = new java.util.HashSet<NamedTypeSnapshot>();
		// Two set-based catalog reads, independent of the number of typed columns.
		if (expected.stream().anyMatch(t -> "DOMAIN".equals(t.kind()))) {
			for (var type : reader.getDomainReader().getAllFull(connection))
				actual.add(snapshot("DOMAIN", type));
		}
		if (expected.stream().anyMatch(t -> "TYPE".equals(t.kind()))) {
			for (var type : reader.getTypeReader().getAllFull(connection))
				actual.add(snapshot("TYPE", type));
		}
		for (var type : expected)
			if (actual.stream()
					.noneMatch(a -> a.kind().equals(type.kind()) && a.schema().equals(type.schema())
							&& a.name().equals(type.name())
							&& HsqlDefinitionIdentity.equivalent(a.createSql(), type.createSql()))) {
				throw new IllegalArgumentException("Named type definitions changed; rebuild the migration plan: "
						+ type.schema() + "." + type.name());
			}
	}

	private Column storage(Column column, Catalog catalog) {
		if (column.getArrayDimension() > 0)
			throw new IllegalArgumentException("Array replacement requires a separate migration");
		if (column.getDataType() != com.sqlapp.data.db.datatype.DataType.DOMAIN
				&& column.getDataType() != com.sqlapp.data.db.datatype.DataType.OTHER) {
			comparable(column);
			return column;
		}
		var matches = new ArrayList<Column>();
		for (var schema : catalog.getSchemas()) {
			for (var domain : schema.getDomains())
				if (typeNameMatches(column, schema.getName(), domain.getName())) {
					if (!com.sqlapp.util.CommonUtils.isEmpty(domain.getDefinition()))
						requireMatchingDefinition("DOMAIN", domain, domain.clone().setDefinition((String) null));
					matches.add(new Column().setDataType(domain.getDataType()).setDataTypeName(domain.getDataTypeName())
							.setLength(domain.getLength()).setScale(domain.getScale()));
				}
			if (column.getDataType() == com.sqlapp.data.db.datatype.DataType.OTHER) {
				for (var type : schema.getTypes())
					if (typeNameMatches(column, schema.getName(), type.getName())) {
						if (!com.sqlapp.util.CommonUtils.isEmpty(type.getDefinition()))
							requireMatchingDefinition("TYPE", type, type.clone().setDefinition((String) null));
						if (type.getStatement().size() != 1 || type.getStatement().get(0).isBlank()
								|| !type.getColumns().isEmpty())
							throw new IllegalArgumentException(
									"Distinct type requires a scalar source statement: " + type.getName());
						Column base = new Column();
						registry.getDialect().setDbType(type.getStatement().get(0), null, null, base);
						matches.add(base);
					}
			}
		}
		if (matches.size() != 1)
			throw new IllegalArgumentException("Resolve the named column type uniquely from complete Catalog metadata: "
					+ column.getDataTypeName());
		Column base = matches.get(0);
		comparable(base);
		return base;
	}

	private void requireMatchingDefinition(String kind, com.sqlapp.data.schemas.AbstractSchemaObject<?> object,
			com.sqlapp.data.schemas.AbstractSchemaObject<?> modeled) {
		if (!HsqlDefinitionIdentity.equivalent(snapshot(kind, object).createSql(),
				snapshot(kind, modeled).createSql())) {
			throw new IllegalArgumentException(
					"Named type definition and scalar model disagree; use complete structured metadata: "
							+ object.getName());
		}
	}

	private boolean typeNameMatches(Column column, String schema, String name) {
		String type = column.getDataTypeName();
		if (type == null)
			return false;
		var schemaNames = new ArrayList<String>();
		schemaNames.add(quote(schema));
		var names = new ArrayList<String>();
		names.add(quote(name));
		if (schema.matches("[A-Z_][A-Z_0-9]*"))
			schemaNames.add(schema);
		if (name.matches("[A-Z_][A-Z_0-9]*"))
			names.add(name);
		for (String n : names) {
			if (Objects.equals(schema, column.getSchemaName()) && n.equals(type))
				return true;
			for (String s : schemaNames)
				if ((s + "." + n).equals(type))
					return true;
		}
		return false;
	}

	private String converted(String expression, Column column, Catalog catalog) {
		String value = cast(expression, storage(column, catalog));
		return column.getDataType() == com.sqlapp.data.db.datatype.DataType.DOMAIN
				|| column.getDataType() == com.sqlapp.data.db.datatype.DataType.OTHER ? cast(value, column) : value;
	}

	private String cast(String expression, Column column) {
		var type = registry.getDialect().createSqlBuilder().typeDefinition(column.getDataType(),
				column.getDataTypeName(), column.getLength(), column.getScale());
		return "CAST(" + expression + " AS " + type + ")";
	}

	private String name(Table table) {
		return quote(table.getSchemaName()) + "." + quote(table.getName());
	}

	private String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	private static String literal(String name) {
		return "'" + name.replace("'", "''") + "'";
	}

	private String names(List<Column> columns) {
		return columns.stream().map(c -> quote(c.getName())).collect(Collectors.joining(", "));
	}

	private static ArrayList<Command> commands(List<SqlOperation> operations) {
		return operations.stream().map(op -> new Command(op.getSqlText(), op.getSqlType()))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	private static String difference(String left, String right) {
		return "SELECT COUNT(*) FROM ((" + left + " EXCEPT ALL " + right + ") UNION ALL (" + right + " EXCEPT ALL "
				+ left + ")) D";
	}

	private static void run(Connection connection, List<Command> commands) throws SQLException {
		try (var statement = connection.createStatement()) {
			for (var command : commands)
				if (command.guard() == null || scalar(connection, command.guard()) != 0)
					statement.execute(command.sql());
		}
	}

	private static long scalar(Connection connection, String sql) throws SQLException {
		try (var statement = connection.createStatement(); var rs = statement.executeQuery(sql)) {
			if (!rs.next())
				throw new SQLException("Verification query returned no row");
			return rs.getLong(1);
		}
	}

	private static String hash(String value) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static String xml(com.sqlapp.data.schemas.DbCommonObject<?> object) {
		try {
			return object.asXml();
		} catch (javax.xml.stream.XMLStreamException failure) {
			throw new IllegalArgumentException("Cannot fingerprint schema metadata", failure);
		}
	}

	private static String schemaXml(Schema schema) {
		var canonical = schema.clone().setProductName(null).setProductMajorVersion(null).setProductMinorVersion(null)
				.setProductRevision(null);
		// Derived native spelling is retained in replacement guards, not schema
		// identity.
		for (var domain : canonical.getDomains())
			domain.getSpecifics().remove("hsql.domainSourceType.v1");
		return xml(canonical);
	}

	private static String foreignKeys(List<com.sqlapp.data.schemas.ForeignKeyConstraint> constraints) {
		var json = new com.sqlapp.util.JsonConverter();
		return hash(
				constraints.stream()
						.map(fk -> json.toJsonString(java.util.Arrays.asList(fk.getSchemaName(), fk.getTableName(),
								fk.getName(), fk.getRelatedTableSchemaName(), fk.getRelatedTableName(),
								fk.getColumns().stream().map(Column::getName).toList(),
								fk.getRelatedColumns().stream().map(com.sqlapp.data.schemas.ReferenceColumn::getName)
										.toList(),
								fk.getDeleteRule(), fk.getUpdateRule())))
						.sorted().collect(Collectors.joining("\n")));
	}

	private static String dataFingerprint(Connection connection, String query) throws SQLException {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			try (var statement = connection.createStatement(); var rs = statement.executeQuery(query)) {
				int columns = rs.getMetaData().getColumnCount();
				while (rs.next()) {
					for (int i = 1; i <= columns; i++) {
						String value = rs.getString(i);
						byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
						digest.update(
								java.nio.ByteBuffer.allocate(4).putInt(value == null ? -1 : bytes.length).array());
						digest.update(bytes);
					}
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (java.security.NoSuchAlgorithmException problem) {
			throw new IllegalStateException(problem);
		}
	}
}
