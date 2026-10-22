/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.ArgumentRoutine;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.util.JsonConverter;

/** HSQLDB catalog dependency identities retained by Schema XML and cloning. */
public final class HsqlDdlDependencies {
	public static final String SPECIFICS_KEY = "hsql.ddlDependencies.v1";

	private HsqlDdlDependencies() {
	}

	public record ObjectId(String kind, String schema, String name) {
		public ObjectId {
			if (kind == null || schema == null || name == null || kind.isBlank() || schema.isBlank()
					|| name.isBlank()) {
				throw new IllegalArgumentException("HSQLDB dependency requires kind, schema and name");
			}
		}
	}

	public record Dependency(ObjectId object, ObjectId prerequisite) {
	}

	public record Manifest(int format, String schema, List<Dependency> dependencies) {
	}

	public static void set(Schema schema, List<Dependency> dependencies) {
		validate(schema, new Manifest(1, schema.getName(), dependencies));
		if (dependencies.isEmpty()) {
			schema.getSpecifics().remove(SPECIFICS_KEY);
			return;
		}
		schema.getSpecifics().put(SPECIFICS_KEY, new JsonConverter()
				.toJsonString(new Manifest(1, schema.getName(), List.copyOf(new LinkedHashSet<>(dependencies)))));
	}

	public static List<Dependency> get(Schema schema) {
		String json = schema.getSpecifics().get(SPECIFICS_KEY);
		if (json == null)
			return List.of();
		var manifest = new JsonConverter().getObjectMapper().readerFor(Manifest.class)
				.with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.<Manifest>readValue(json);
		validate(schema, manifest);
		return List.copyOf(manifest.dependencies());
	}

	private static void validate(Schema schema, Manifest manifest) {
		if (manifest == null || manifest.format() != 1 || !schema.getName().equals(manifest.schema())
				|| manifest.dependencies() == null) {
			throw new IllegalArgumentException("Invalid or stale HSQLDB dependency manifest: " + schema.getName());
		}
		for (var edge : manifest.dependencies()) {
			if (edge == null || edge.object() == null || edge.prerequisite() == null
					|| !schema.getName().equals(edge.object().schema())
					|| !Set.of("RELATION", "ROUTINE", "Sequence").contains(edge.object().kind())
					|| !Set.of("RELATION", "ROUTINE", "Sequence").contains(edge.prerequisite().kind())) {
				throw new IllegalArgumentException("Invalid HSQLDB dependency owner: " + schema.getName());
			}
		}
	}

	/**
	 * Stable ordering of existing shared-factory operations; never parses SQL text.
	 */
	public static List<SqlOperation> order(List<SqlOperation> operations, Collection<Schema> schemas) {
		return order(operations, schemas, null);
	}

	public static List<SqlOperation> order(List<SqlOperation> operations, Collection<Schema> schemas,
			com.sqlapp.data.db.sql.SqlFactoryRegistry registry) {
		Map<ObjectId, List<SqlOperation>> groups = new LinkedHashMap<>();
		Map<ObjectId, Set<ObjectId>> needs = new LinkedHashMap<>();
		List<Function> functions = schemas.stream().flatMap(s -> s.getFunctions().stream()).toList();
		List<SqlOperation> prefix = new ArrayList<>();
		List<SqlOperation> grants = new ArrayList<>();
		Set<ObjectId> declarations = new LinkedHashSet<>();
		for (var operation : operations) {
			if (operation.getSqlType() == SqlType.GRANT) {
				grants.add(operation);
				continue;
			}
			if (!(operation.getOriginal() instanceof AbstractSchemaObject<?> object)) {
				prefix.add(operation);
				continue;
			}
			var id = operation instanceof RoutineCompletion ? completionId(id(object)) : id(object);
			if (operation instanceof RoutineDeclaration)
				declarations.add(id);
			groups.computeIfAbsent(id, k -> new ArrayList<>()).add(operation);
			var dependencies = needs.computeIfAbsent(id, k -> new LinkedHashSet<>());
			if (operation instanceof RoutineCompletion completion)
				dependencies.addAll(completion.prerequisites);
			if (object instanceof TableNameProperty<?> table && table.getTableName() != null) {
				dependencies.add(new ObjectId("RELATION", id.schema(), table.getTableName()));
			}
			if (object instanceof com.sqlapp.data.schemas.ForeignKeyConstraint fk) {
				dependencies.add(new ObjectId("RELATION",
						fk.getRelatedTableSchemaName() == null ? id.schema() : fk.getRelatedTableSchemaName(),
						fk.getRelatedTableName()));
			}
			if (object instanceof Trigger trigger && trigger.getTableName() != null) {
				dependencies.add(new ObjectId("RELATION",
						trigger.getTableSchemaName() == null ? id.schema() : trigger.getTableSchemaName(),
						trigger.getTableName()));
			}
			if (object instanceof Synonym synonym && synonym.getObjectName() != null) {
				String kind = synonym.getSpecifics().get("OBJECT_TYPE");
				if ("TABLE".equals(kind) || "VIEW".equals(kind) || "SEQUENCE".equals(kind)) {
					dependencies.add(new ObjectId("SEQUENCE".equals(kind) ? "Sequence" : "RELATION",
							synonym.getObjectSchemaName() == null ? id.schema() : synonym.getObjectSchemaName(),
							synonym.getObjectName()));
				}
			}
			if (object instanceof Table table) {
				for (var column : table.getColumns()) {
					expressionNeeds(dependencies, column.getFormula(), id.schema(), functions);
					expressionNeeds(dependencies, column.getDefaultValue(), id.schema(), functions);
					var type = HsqlExpressionDependencies.typeName(column.getDataTypeName());
					if (!type.isEmpty() && (column.getDataType() == com.sqlapp.data.db.datatype.DataType.DOMAIN
							|| column.getDataType() == com.sqlapp.data.db.datatype.DataType.OTHER)) {
						String typeSchema = type.size() > 1 ? type.get(type.size() - 2) : id.schema();
						String typeName = type.get(type.size() - 1);
						dependencies.add(new ObjectId("Domain", typeSchema, typeName));
						dependencies.add(new ObjectId("Type", typeSchema, typeName));
					}
					if (column.getSequenceName() != null && !column.getSequenceName().isBlank()) {
						var sequence = column.getSequence();
						dependencies.add(new ObjectId("Sequence",
								sequence.getSchemaName() == null ? id.schema() : sequence.getSchemaName(),
								sequence.getName()));
					}
				}
				for (var check : table.getConstraints().getCheckConstraints())
					expressionNeeds(dependencies, check.getExpression(), id.schema(), functions);
				for (var fk : table.getConstraints().getForeignKeyConstraints()) {
					dependencies.add(new ObjectId("RELATION",
							fk.getRelatedTableSchemaName() == null ? id.schema() : fk.getRelatedTableSchemaName(),
							fk.getRelatedTableName()));
				}
			}
			if (object instanceof Domain domain) {
				expressionNeeds(dependencies, domain.getDefaultValue(), id.schema(), functions);
				expressionNeeds(dependencies, domain.getCheck(), id.schema(), functions);
				for (var check : domain.getChecks())
					expressionNeeds(dependencies, check.getExpression(), id.schema(), functions);
			}
			dependencies.remove(id);
		}
		for (var schema : schemas) {
			for (var edge : get(schema)) {
				if (!edge.object().equals(edge.prerequisite()) && needs.containsKey(edge.object())) {
					needs.get(declarations.contains(edge.object()) ? completionId(edge.object()) : edge.object())
							.add(edge.prerequisite());
				}
			}
		}
		var pending = new LinkedHashMap<>(groups);
		while (!pending.isEmpty()) {
			boolean progress = false;
			var iterator = pending.entrySet().iterator();
			while (iterator.hasNext()) {
				var entry = iterator.next();
				if (needs.get(entry.getKey()).stream().noneMatch(pending::containsKey)) {
					prefix.addAll(entry.getValue());
					iterator.remove();
					progress = true;
				}
			}
			if (!progress && !bootstrap(pending, needs, schemas, registry)) {
				throw new IllegalArgumentException(
						"Cyclic HSQLDB DDL dependencies require modeled SQL routine signatures and bodies or an explicit migration: "
								+ pending.keySet());
			}
		}
		prefix.addAll(grants);
		return prefix;
	}

	private static ObjectId completionId(ObjectId id) {
		return new ObjectId("ROUTINE_BODY", id.schema(), id.name());
	}

	private static final class RoutineDeclaration extends SqlOperation {
		RoutineDeclaration(SqlOperation operation) {
			super(operation.getSqlText(), operation.getSqlType(),
					(com.sqlapp.data.schemas.DbCommonObject<?>) operation.getOriginal());
			setTerminator(operation.getTerminator());
			setStartStatementTerminator(operation.getStartStatementTerminator());
			setEndStatementTerminator(operation.getEndStatementTerminator());
		}
	}

	private static final class RoutineCompletion extends SqlOperation {
		private final Set<ObjectId> prerequisites;

		RoutineCompletion(String sql, ArgumentRoutine<?> routine, Set<ObjectId> prerequisites) {
			super(sql, SqlType.ALTER, routine);
			this.prerequisites = Set.copyOf(prerequisites);
		}
	}

	private static boolean bootstrap(Map<ObjectId, List<SqlOperation>> pending, Map<ObjectId, Set<ObjectId>> needs,
			Collection<Schema> schemas, com.sqlapp.data.db.sql.SqlFactoryRegistry registry) {
		if (registry == null)
			return false;
		boolean changed = false;
		List<ArgumentRoutine<?>> routines = new ArrayList<>();
		for (var schema : schemas) {
			routines.addAll(schema.getFunctions());
			routines.addAll(schema.getProcedures());
		}
		for (var routine : routines) {
			ObjectId id = id(routine);
			if (!pending.containsKey(id) || needs.containsKey(completionId(id)))
				continue;
			var stub = initializationStub(routine);
			if (stub == null)
				continue;
			var prerequisites = new LinkedHashSet<>(needs.get(id));
			prerequisites.add(id);
			var sql = registry.getDialect().createSqlBuilder().alter().specific().space()._add("ROUTINE").space()
					.name(id.schema())._add(".").name(id.name()).space()._add("BODY ")._add(routine.getStatement());
			pending.put(id, registry.createSql(stub, SqlType.CREATE).stream().<SqlOperation>map(RoutineDeclaration::new)
					.toList());
			var completion = new RoutineCompletion(sql.toString(), routine, prerequisites);
			registry.getDialect().setChangeAndResetSqlDelimiter(completion.getSqlText(), completion);
			pending.put(completionId(id), List.of(completion));
			needs.put(completionId(id), prerequisites);
			needs.get(id).removeIf(dependency -> Set.of("RELATION", "ROUTINE").contains(dependency.kind()));
			changed = true;
		}
		return changed;
	}

	/**
	 * Shared fail-closed declaration used by CREATE cycles and migration
	 * detachment.
	 */
	static ArgumentRoutine<?> initializationStub(ArgumentRoutine<?> routine) {
		if (routine.getLanguage() != null && !"SQL".equalsIgnoreCase(routine.getLanguage())
				|| routine.getStatement() == null || routine.getStatement().isEmpty())
			return null;
		if (routine instanceof Function function) {
			if (function.getReturning().getTable() != null || function.getReturning().getDataType() == null
					|| function.getFunctionType() != null
							&& function.getFunctionType() != com.sqlapp.data.schemas.FunctionType.Scalar)
				return null;
			return initializationBody(function, function.clone(),
					"BEGIN ATOMIC SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Routine initialization incomplete'; RETURN NULL; END");
		}
		if (routine instanceof com.sqlapp.data.schemas.Procedure procedure)
			return initializationBody(procedure, procedure.clone(),
					"BEGIN ATOMIC SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Routine initialization incomplete'; END");
		return null;
	}

	private static ArgumentRoutine<?> initializationBody(ArgumentRoutine<?> original, ArgumentRoutine<?> stub,
			String body) {
		stub.setDefinition((String) null).setStatement(body);
		if (original.getDefinition() == null || original.getDefinition().isEmpty())
			return stub;
		// Keep the native signature: HSQLDB may report only a UDT's base in routine
		// columns.
		String ddl = String.join("\n", original.getDefinition()).stripTrailing();
		String statement = String.join("\n", original.getStatement()).stripTrailing();
		if (statement.isBlank() || !ddl.endsWith(statement))
			return null;
		stub.setDefinition(ddl.substring(0, ddl.length() - statement.length()) + body);
		return stub;
	}

	private static void expressionNeeds(Set<ObjectId> needs, String expression, String ownerSchema,
			List<Function> functions) {
		for (var reference : HsqlExpressionDependencies.references(expression)) {
			var parts = reference.name();
			if (parts.size() > 3)
				throw new IllegalArgumentException("Invalid HSQLDB expression identifier: " + parts);
			String schema = parts.size() > 1 ? parts.get(parts.size() - 2) : ownerSchema;
			String name = parts.get(parts.size() - 1);
			if (reference.sequence()) {
				needs.add(new ObjectId("Sequence", schema, name));
			} else {
				// Dependency ordering needs every matching overload, not a guessed signature.
				for (var function : functions) {
					if (schema.equals(function.getSchemaName()) && name.equals(function.getName()))
						needs.add(id(function));
				}
			}
		}
	}

	private static ObjectId id(AbstractSchemaObject<?> object) {
		if (object instanceof Table)
			return new ObjectId("RELATION", object.getSchemaName(), object.getName());
		if (object instanceof ArgumentRoutine<?> routine) {
			return new ObjectId("ROUTINE", object.getSchemaName(),
					routine.getSpecificName() == null ? object.getName() : routine.getSpecificName());
		}
		return new ObjectId(object.getClass().getSimpleName(), object.getSchemaName(), object.getName());
	}
}
