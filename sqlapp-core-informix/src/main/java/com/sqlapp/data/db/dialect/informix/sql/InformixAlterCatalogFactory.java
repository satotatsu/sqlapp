/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes;
import com.sqlapp.data.db.sql.AlterCatalogFactory;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.schemas.properties.GrantableProperty;

/**
 * Plans column and independent-index changes from complete database snapshots.
 */
public class InformixAlterCatalogFactory extends SimpleSqlFactory<Catalog, InformixSqlBuilder> {
	private record TableId(String schema, String name) {
	}

	private record ConstraintId(TableId table, String name) {
	}

	private record IndexId(TableId table, String name) {
	}

	private record IndexChanges(Map<IndexId, Index> drops, Map<IndexId, Index> creates) {
	}

	private static class CatalogColumnFactory extends InformixAlterTableFactory {
		@Override
		protected void addConstraintDefinitions(Map<String, Difference<?>> changes, Table original, Table target,
				List<DbObjectDifference> constraints, List<SqlOperation> result) {
			// The only constraints in this projection are NOT NULL, which are part of the
			// column declaration.
		}
	}

	private static class EnsureIndexFactory extends InformixCreateIndexFactory {
		@Override
		protected void addExistenceCondition(Index index, com.sqlapp.util.AbstractSqlBuilder<?> builder) {
			builder.ifNotExists(true);
		}
	}

	private static TableId id(Table table) {
		return new TableId(table.getSchemaName(), table.getName());
	}

	private static Map<TableId, Table> tables(Catalog catalog) {
		Map<TableId, Table> result = new LinkedHashMap<>();
		for (Schema schema : catalog.getSchemas())
			for (Table table : schema.getTables()) {
				if (table.getSchemaName() == null || table.getSchemaName().isBlank() || table.getName() == null
						|| table.getName().isBlank() || result.put(id(table), table) != null)
					throw new IllegalArgumentException(
							"Informix dependency planning requires unique owner-qualified table identities");
			}
		return result;
	}

	private static UnsupportedOperationException unsupported(String message) {
		return new UnsupportedOperationException("Informix Catalog MODIFY dependency plan: " + message);
	}

	@Override
	public List<SqlOperation> createSql(Catalog catalog) {
		throw new IllegalArgumentException(
				"Informix Catalog ALTER requires an original/target difference of complete Catalog snapshots");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (!(difference.getOriginal() instanceof Catalog before) || !(difference.getTarget() instanceof Catalog after))
			throw new IllegalArgumentException("Informix Catalog ALTER requires original and target Catalog snapshots");
		// Cloned/hand-authored Catalogs may resolve a generic dialect. SQL here is
		// explicitly requested from the Informix registry, including nested diffs.
		setDialect(getSqlFactoryRegistry().getDialect());
		var catalogChanges = difference.toDifference().getChangedProperties(getDialect());
		var privilegeCollections = List.of(SchemaObjectProperties.OBJECT_PRIVILEGES.getLabel(),
				SchemaObjectProperties.COLUMN_PRIVILEGES.getLabel(),
				SchemaObjectProperties.ROUTINE_PRIVILEGES.getLabel());
		if (privilegeCollections.stream().anyMatch(catalogChanges::containsKey)) {
			if (!privilegeCollections.containsAll(catalogChanges.keySet()))
				throw unsupported("apply object, column and routine privileges separately from other Catalog changes");
			Map<String, SqlOperation> revokes = new LinkedHashMap<>();
			Map<String, SqlOperation> grants = new LinkedHashMap<>();
			for (String property : privilegeCollections) {
				if (catalogChanges.get(property) instanceof DbObjectDifferenceCollection changes) {
					for (var deletion : changes.getList(State.Deleted)) {
						for (var operation : revokePrivilege(deletion, before, after, grants))
							revokes.putIfAbsent(operation.getSqlText(), operation);
					}
					for (var change : changes.getList(State.Added, State.Modified)) {
						var oldGrant = change.getOriginal();
						var newGrant = change.getTarget();
						if ((oldGrant instanceof RoutinePrivilege || oldGrant instanceof ObjectPrivilege
								|| oldGrant instanceof ColumnPrivilege)
								&& oldGrant instanceof GrantableProperty<?> oldOption && oldOption.isGrantable()
								&& newGrant instanceof GrantableProperty<?> newOption && !newOption.isGrantable()
								&& oldGrant.equals(newGrant,
										new ExcludeFilterEqualsHandler(SchemaProperties.GRANTABLE.getLabel()))) {
							if (oldGrant instanceof ColumnPrivilege column) {
								var operation = revokeColumnScope(column, before, after, grants).getFirst();
								revokes.putIfAbsent(operation.getSqlText(), operation);
								continue;
							}
							if (oldGrant instanceof RoutinePrivilege routine)
								validateRoutineGrantRemoval(routine, before, after);
							if (oldGrant instanceof ObjectPrivilege table)
								validateTableGrantRemoval(table, before, after);
							for (var operation : getSqlFactoryRegistry().createSql(oldGrant, SqlType.REVOKE))
								revokes.putIfAbsent(operation.getSqlText(), operation);
							for (var operation : getSqlFactoryRegistry().createSql(newGrant, SqlType.GRANT))
								grants.putIfAbsent(operation.getSqlText(), operation);
							continue;
						}
						for (var operation : getSqlFactoryRegistry().createSql(change))
							grants.putIfAbsent(operation.getSqlText(), operation);
					}
				}
			}
			List<SqlOperation> operations = new ArrayList<>(revokes.values());
			operations.addAll(InformixGrantOrder.order(grants.values()));
			return operations;
		}
		Map<TableId, Table> originals = tables(before), targets = tables(after);
		Set<TableId> modified = new LinkedHashSet<>();
		Set<TableId> constraintsChanged = new LinkedHashSet<>();
		for (var entry : originals.entrySet()) {
			Table target = targets.get(entry.getKey());
			if (target != null && (!entry.getValue().getColumns().equals(target.getColumns())
					|| !entry.getValue().getIndexes().equals(target.getIndexes())
					|| !entry.getValue().getConstraints().getUniqueConstraints()
							.equals(target.getConstraints().getUniqueConstraints())
					|| !entry.getValue().getConstraints().getNotNullConstraints()
							.equals(target.getConstraints().getNotNullConstraints())
					|| hasBackingReferenceChange(entry.getValue(), target)))
				modified.add(entry.getKey());
			if (target != null && (!entry.getValue().getConstraints().getCheckConstraints()
					.equals(target.getConstraints().getCheckConstraints())
					|| !entry.getValue().getConstraints().getForeignKeyConstraints()
							.equals(target.getConstraints().getForeignKeyConstraints())))
				constraintsChanged.add(entry.getKey());
		}
		if (modified.isEmpty() && constraintsChanged.isEmpty()) {
			var factory = new AlterCatalogFactory();
			factory.setSqlFactoryRegistry(getSqlFactoryRegistry());
			factory.setOptions(getOptions());
			factory.setDialect(getDialect());
			return factory.createDiffSql(difference);
		}
		if (!Objects.equals(before.getName(), after.getName()) || !originals.keySet().equals(targets.keySet()))
			throw unsupported(
					"combine neither table/database renames nor table additions/deletions with column MODIFY");
		if (!before.getSchemas().stream().map(Schema::getName).toList()
				.equals(after.getSchemas().stream().map(Schema::getName).toList()))
			throw unsupported("schema identities and order must remain unchanged");
		if (!getOptions().isDecorateSchemaName())
			throw unsupported("owner qualification must be enabled for dependency planning");
		// Keep this path restricted to column changes. Other collections have
		// independent
		// dependency and ordering requirements and must not be silently skipped.
		if (!catalogChanges.keySet().equals(Set.of("schemas")))
			throw unsupported("apply non-schema changes separately");
		for (Schema schema : before.getSchemas()) {
			Schema target = after.getSchemas().get(schema.getName());
			if (target == null || !schema.diff(target).toDifference().getChangedProperties(getDialect()).keySet()
					.stream().allMatch("tables"::equals))
				throw unsupported("apply schema or non-table object changes separately");
		}
		Map<TableId, Map<String, String>> renames = new LinkedHashMap<>();
		for (TableId table : originals.keySet()) {
			Table original = originals.get(table), target = targets.get(table);
			var originalNames = original.getColumns().stream().map(Column::getName).toList();
			var targetNames = target.getColumns().stream().map(Column::getName).toList();
			Set<String> existingNames = new LinkedHashSet<>(originalNames);
			Set<String> retainedNames = new LinkedHashSet<>(targetNames);
			if (!originalNames.stream().filter(retainedNames::contains).toList()
					.equals(targetNames.stream().filter(existingNames::contains).toList()))
				throw unsupported("column reorderings require a separate plan");
			if (!retainedNames.containsAll(existingNames)) {
				if (!existingNames.containsAll(retainedNames))
					renames.put(table, columnRenames(original, target, existingNames, retainedNames));
				validateColumnRemoval(before, original, target);
			}
			if (!original.diff(target).toDifference().getChangedProperties(getDialect()).keySet().stream()
					.allMatch(p -> Set.of("columns", "indexes", "constraints").contains(p))
					|| !original.getConstraints().stream()
							.filter(c -> !supportsConstraintChange(c) && !(c instanceof NotNullConstraint)).toList()
							.equals(target.getConstraints().stream()
									.filter(c -> !supportsConstraintChange(c) && !(c instanceof NotNullConstraint))
									.toList()))
				throw unsupported("apply constraint and table-property changes separately");
		}
		Map<ConstraintId, Constraint> drops = managed(originals, targets, modified, constraintsChanged);
		Map<ConstraintId, Constraint> creates = managed(targets, originals, modified, constraintsChanged);
		for (var entry : creates.entrySet()) {
			if (!drops.containsKey(entry.getKey()) && !supportsConstraintChange(entry.getValue()))
				throw unsupported("unsupported constraint addition");
			if (entry.getValue() instanceof ForeignKeyConstraint foreign && (!foreign.equals(drops.get(entry.getKey()))
					|| !originals.get(id(foreign.getRelatedTable())).getConstraints().getUniqueConstraints().equals(
							targets.get(id(foreign.getRelatedTable())).getConstraints().getUniqueConstraints()))) {
				Table referenced = targets.get(id(foreign.getRelatedTable()));
				var referencedNames = foreign.getRelatedColumns().stream().map(ReferenceColumn::getName).toList();
				if (referenced.getConstraints().stream()
						.noneMatch(constraint -> constraint instanceof UniqueConstraint key && key.isEnable() && key
								.getColumns().stream().map(ReferenceColumn::getName).toList().equals(referencedNames)))
					throw unsupported(
							"new foreign key target must have an enabled primary or unique constraint on the referenced columns");
			}
		}
		for (ConstraintId key : drops.keySet()) {
			Constraint original = drops.get(key), target = creates.get(key);
			if (target == null) {
				if (supportsConstraintChange(original))
					continue;
				throw unsupported("unsupported constraint removal");
			}
			if (!original.equals(target)
					&& !(original instanceof UniqueConstraint && target instanceof UniqueConstraint)
					&& !(original instanceof CheckConstraint oldCheck && target instanceof CheckConstraint newCheck
							&& checkDefinitionWithoutExpressionOrMode(oldCheck)
									.equals(checkDefinitionWithoutExpressionOrMode(newCheck)))
					&& !(original instanceof ForeignKeyConstraint && target instanceof ForeignKeyConstraint
							&& original.equals(target,
									new ExcludeFilterEqualsHandler(SchemaProperties.DELETE_RULE.getLabel(),
											SchemaProperties.RELATED_TABLE_SCHEMA_NAME.getLabel(),
											SchemaProperties.RELATED_TABLE_NAME.getLabel(),
											SchemaObjectProperties.RELATED_COLUMNS.getLabel(),
											SchemaObjectProperties.COLUMN_ARRAY.getLabel(),
											SchemaProperties.ENABLE.getLabel(), SchemaProperties.SPECIFICS.getLabel()))
							&& specificsWithoutMode(original).equals(specificsWithoutMode(target))))
				throw unsupported("unsupported constraint definition change");
		}
		Set<IndexId> backing = backingIndexes(creates);
		IndexChanges indexes = indexChanges(originals, targets);
		Set<IndexId> oldBacking = backingIndexes(drops);
		validateBackingRenames(originals, targets, oldBacking, backing, indexes);
		if (!backing.isEmpty() || indexes.drops().keySet().stream().anyMatch(oldBacking::contains)) {
			Integer major = before.getProductMajorVersion(), minor = before.getProductMinorVersion();
			if (major == null || minor == null || major < 11 || major == 11 && minor < 70)
				throw unsupported("named backing-index preservation requires known Informix version 11.70 or later");
		}
		Catalog strippedBefore = before.clone().setDialect(getDialect()),
				strippedAfter = after.clone().setDialect(getDialect());
		strip(strippedBefore, drops.keySet());
		strip(strippedAfter, creates.keySet());
		List<SqlOperation> result = new ArrayList<>();
		for (boolean foreign : new boolean[] { true, false })
			for (Constraint constraint : drops.values())
				if ((constraint instanceof ForeignKeyConstraint) == foreign) {
					Table table = constraint.getAncestor(Table.class);
					InformixAlterTableFactory factory = getSqlFactoryRegistry().getSqlFactory(table, SqlType.ALTER);
					factory.addDropConstraintDefinition(table, table, constraint, null, null, result);
				}
		Map<TableId, Table> strippedOriginals = tables(strippedBefore), strippedTargets = tables(strippedAfter);
		for (var entry : indexes.drops().entrySet()) {
			Index index = entry.getValue();
			InformixAlterTableFactory factory = getSqlFactoryRegistry().getSqlFactory(index.getTable(), SqlType.ALTER);
			var builder = createSqlBuilder();
			// DROP CONSTRAINT can already remove an index that existed solely for the
			// constraint.
			factory.addDropIndexDefinition(index, builder, oldBacking.contains(entry.getKey()));
			result.add(new SqlOperation(builder.toString(), SqlType.DROP, index));
		}
		for (TableId id : modified) {
			var factory = new CatalogColumnFactory();
			factory.setSqlFactoryRegistry(getSqlFactoryRegistry());
			factory.setDialect(getDialect());
			factory.setOptions(getOptions());
			Table columnsBefore = strippedOriginals.get(id).clone(), columnsAfter = strippedTargets.get(id).clone();
			columnsBefore.getIndexes().clear();
			columnsAfter.getIndexes().clear();
			for (var rename : renames.getOrDefault(id, Map.of()).entrySet()) {
				Column originalColumn = columnsBefore.getColumns().get(rename.getKey());
				Column targetColumn = columnsAfter.getColumns().get(rename.getValue());
				factory.addRenameColumn(columnsBefore, columnsAfter, originalColumn.clone(), targetColumn, null,
						result);
				originalColumn.setName(rename.getValue());
			}
			result.addAll(factory.createDiffSql(columnsBefore.diff(columnsAfter)));
			for (String name : changedNotNullColumns(columnsBefore, columnsAfter)) {
				Column oldColumn = columnsBefore.getColumns().get(name);
				Column newColumn = columnsAfter.getColumns().get(name);
				if (oldColumn != null && newColumn != null && oldColumn.equals(newColumn))
					factory.addAlterColumn(columnsBefore, columnsAfter, oldColumn, newColumn, null, result);
			}
		}
		for (IndexId id : indexes.creates().keySet()) {
			result.addAll(createIndex(strippedTargets.get(id.table()).getIndexes().get(id.name()), false));
		}
		// Explicit indexes normally survive constraint removal and MODIFY rebuilds
		// indexes in place. Ensure named backing indexes also exist if the server
		// removed an internal implementation index. Build from the stripped model,
		// so an index sharing its name with a constraint is not suppressed.
		for (IndexId id : backing) {
			if (indexes.creates().containsKey(id))
				continue;
			Index index = strippedTargets.get(id.table()).getIndexes().get(id.name());
			result.addAll(createIndex(index, true));
		}
		for (boolean foreign : new boolean[] { false, true })
			for (Constraint constraint : creates.values())
				if ((constraint instanceof ForeignKeyConstraint) == foreign)
					result.addAll(getSqlFactoryRegistry().createSql(constraint, SqlType.CREATE));
		return result;
	}

	private List<SqlOperation> revokePrivilege(DbObjectDifference deletion, Catalog before, Catalog target,
			Map<String, SqlOperation> grants) {
		var original = deletion.getOriginal();
		if (original instanceof ColumnPrivilege column)
			return revokeColumnScope(column, before, target, grants);
		if (original instanceof GrantableProperty<?> grant && grant.isGrantable()) {
			if (original instanceof RoutinePrivilege routine)
				validateRoutineGrantRemoval(routine, before, target);
			else if (original instanceof ObjectPrivilege table)
				validateTableGrantRemoval(table, before, target);
			else
				throw unsupported("removing a grantable privilege requires a grant-chain plan");
		}
		if (original instanceof ObjectPrivilege table)
			validateNoRetainedColumns(table, target);
		return getSqlFactoryRegistry().createSql(deletion);
	}

	private List<SqlOperation> revokeColumnScope(ColumnPrivilege column, Catalog before, Catalog target,
			Map<String, SqlOperation> grants) {
		if (column.getGrantorName() == null || column.getSpecificName() != null
				|| column.getState() != null && column.getState() != PrivilegeState.Grant
				|| !Set.of("SELECT", "UPDATE", "REFERENCES").stream()
						.anyMatch(p -> p.equalsIgnoreCase(column.getPrivilege())))
			throw unsupported("column privilege removal requires an explicit grantor and a supported column privilege");
		var table = new ObjectPrivilege().setSchemaName(column.getSchemaName()).setObjectName(column.getTableName())
				.setPrivilege(column.getPrivilege()).setGranteeName(column.getGranteeName())
				.setGrantorName(column.getGrantorName()).setHierachy(false).setGrantable(false);
		if (target.getObjectPrivileges().stream().anyMatch(p -> sameRevokeScope(table, p.getSchemaName(),
				p.getObjectName(), p.getPrivilege(), p.getGranteeName(), p.getGrantorName())))
			throw unsupported("column revocation would also revoke a retained table privilege");
		if (before.getColumnPrivileges().stream()
				.anyMatch(p -> matchesColumnScope(table, p) && p.getGrantorName() == null))
			throw unsupported("column revocation requires known grantors in the original scope");
		if (before.getColumnPrivileges().stream().anyMatch(p -> matchesColumnScope(table, p) && p.isGrantable())) {
			if (!"SELECT".equalsIgnoreCase(column.getPrivilege()) && !"UPDATE".equalsIgnoreCase(column.getPrivilege()))
				throw unsupported("column grant-option removal supports SELECT and UPDATE");
			validateNoDelegatedTableGrants(table, before, target);
		}
		for (ColumnPrivilege retained : target.getColumnPrivileges()) {
			if (!matchesColumnScope(table, retained))
				continue;
			if (retained.isGrantable() || retained.getGrantorName() == null)
				throw unsupported("cannot restore a retained column privilege with grant option or an unknown grantor");
			for (var operation : getSqlFactoryRegistry().createSql(retained, SqlType.GRANT))
				grants.putIfAbsent(operation.getSqlText(), operation);
		}
		return getSqlFactoryRegistry().createSql(table, SqlType.REVOKE);
	}

	private static void validateRoutineGrantRemoval(RoutinePrivilege original, Catalog before, Catalog after) {
		if (original.getGrantorName() == null || original.getGranteeName() == null)
			throw unsupported("routine grant-option removal requires explicit grantor and grantee");
		// Check all overloads with this qualified routine name to cover different
		// identity representations.
		for (Catalog catalog : List.of(before, after)) {
			if (catalog.getRoutinePrivileges().stream()
					.anyMatch(candidate -> Objects.equals(original.getSchemaName(), candidate.getSchemaName())
							&& Objects.equals(original.getObjectName(), candidate.getObjectName())
							&& (candidate.getGrantorName() == null
									|| original.getGranteeName().equals(candidate.getGrantorName()))))
				throw unsupported(
						"routine grant-option removal requires a grant-chain plan for downstream or unknown grantors");
		}
	}

	private static void validateTableGrantRemoval(ObjectPrivilege table, Catalog before, Catalog after) {
		if (table.getGrantorName() == null || table.getGranteeName() == null || table.getPrivilege() == null
				|| !Set.of("SELECT", "INSERT", "UPDATE", "DELETE")
						.contains(table.getPrivilege().toUpperCase(java.util.Locale.ROOT)))
			throw unsupported(
					"table grant-option removal requires explicit principals and SELECT, INSERT, UPDATE or DELETE");
		validateNoRetainedColumns(table, after);
		validateNoDelegatedTableGrants(table, before, after);
	}

	private static void validateNoDelegatedTableGrants(ObjectPrivilege table, Catalog before, Catalog after) {
		for (Catalog catalog : List.of(before, after)) {
			if (catalog.getObjectPrivileges().stream()
					.anyMatch(p -> possibleDelegatedTableGrant(table, p.getSchemaName(), p.getObjectName(),
							p.getPrivilege(), p.getGrantorName()))
					|| catalog.getColumnPrivileges().stream().anyMatch(p -> possibleDelegatedTableGrant(table,
							p.getSchemaName(), p.getTableName(), p.getPrivilege(), p.getGrantorName())))
				throw unsupported(
						"grant-option removal requires a grant-chain plan for downstream or unknown grantors");
		}
	}

	private static boolean possibleDelegatedTableGrant(ObjectPrivilege table, String schema, String object,
			String privilege, String grantor) {
		return Objects.equals(table.getSchemaName(), schema) && Objects.equals(table.getObjectName(), object)
				&& table.getPrivilege().equalsIgnoreCase(privilege)
				&& (grantor == null || table.getGranteeName().equals(grantor));
	}

	private static void validateNoRetainedColumns(ObjectPrivilege table, Catalog target) {
		if (target.getColumnPrivileges().stream().anyMatch(column -> matchesColumnScope(table, column)))
			throw unsupported("revocation would also revoke a retained column privilege");
	}

	private static boolean matchesColumnScope(ObjectPrivilege table, ColumnPrivilege column) {
		return sameRevokeScope(table, column.getSchemaName(), column.getTableName(), column.getPrivilege(),
				column.getGranteeName(), column.getGrantorName());
	}

	private static boolean sameRevokeScope(ObjectPrivilege table, String schema, String object, String privilege,
			String grantee, String grantor) {
		return Objects.equals(table.getSchemaName(), schema) && Objects.equals(table.getObjectName(), object)
				&& (Objects.equals(table.getGranteeName(), grantee)
						|| "PUBLIC".equalsIgnoreCase(table.getGranteeName()) && "PUBLIC".equalsIgnoreCase(grantee))
				&& table.getPrivilege() != null && table.getPrivilege().equalsIgnoreCase(privilege)
				&& (table.getGrantorName() == null || grantor == null || table.getGrantorName().equals(grantor));
	}

	private static Set<String> changedNotNullColumns(Table before, Table after) {
		var originals = namedNotNullByColumn(before);
		var targets = namedNotNullByColumn(after);
		Set<String> names = new LinkedHashSet<>(originals.keySet());
		names.addAll(targets.keySet());
		names.removeIf(name -> Objects.equals(originals.get(name), targets.get(name)));
		return names;
	}

	private static Map<String, NotNullConstraint> namedNotNullByColumn(Table table) {
		Map<String, NotNullConstraint> result = new LinkedHashMap<>();
		for (var constraint : table.getConstraints().getNotNullConstraints()) {
			var name = constraint.getColumnName();
			Column column = name == null ? null : table.getColumns().get(name);
			if (column == null || result.putIfAbsent(name, constraint) != null
					|| constraint.isEnable() && !column.isNotNull())
				throw unsupported("NOT NULL constraints require a resolved column and a consistent, unique definition");
		}
		return result;
	}

	private static boolean supportsConstraintChange(Constraint constraint) {
		return constraint instanceof CheckConstraint || constraint instanceof ForeignKeyConstraint
				|| constraint instanceof UniqueConstraint;
	}

	private static Map<String, String> specificsWithoutMode(Constraint constraint) {
		var specifics = new LinkedHashMap<>(constraint.getSpecifics());
		specifics.remove(InformixConstraintModes.MODE);
		return specifics;
	}

	private static CheckConstraint checkDefinitionWithoutExpressionOrMode(CheckConstraint check) {
		var definition = (CheckConstraint) check.clone();
		definition.setExpression(null);
		definition.setEnable(true);
		definition.getSpecifics().remove(InformixConstraintModes.MODE);
		return definition;
	}

	private static Map<String, String> columnRenames(Table original, Table target, Set<String> existingNames,
			Set<String> targetNames) {
		Map<String, String> result = new LinkedHashMap<>();
		if (original.getColumns().size() != target.getColumns().size())
			throw unsupported("column renames require matching column counts and stable, unique column IDs");
		for (int i = 0; i < original.getColumns().size(); i++) {
			Column before = original.getColumns().get(i), after = target.getColumns().get(i);
			if (Objects.equals(before.getName(), after.getName()))
				continue;
			String id = before.getId();
			if (id == null || id.isBlank() || !id.equals(after.getId()) || existingNames.contains(after.getName())
					|| targetNames.contains(before.getName())
					|| original.getColumns().stream().filter(c -> id.equals(c.getId())).count() != 1
					|| target.getColumns().stream().filter(c -> id.equals(c.getId())).count() != 1)
				throw unsupported("each renamed column must retain a unique ID and its position");
			result.put(before.getName(), after.getName());
		}
		return result;
	}

	private List<SqlOperation> createIndex(Index index, boolean ensure) {
		InformixCreateIndexFactory factory = ensure ? new EnsureIndexFactory() : new InformixCreateIndexFactory();
		factory.setSqlFactoryRegistry(getSqlFactoryRegistry());
		factory.setDialect(getDialect());
		factory.setOptions(getOptions().clone());
		List<SqlOperation> operations = factory.createSql(index);
		if (operations.isEmpty())
			throw unsupported("index creation was suppressed by a remaining same-named constraint: " + index.getName());
		return operations;
	}

	private static void validateColumnRemoval(Catalog catalog, Table original, Table target) {
		if (target.getColumns().isEmpty() || original.getPartitioning() != null || !original.getInherits().isEmpty()
				|| target.getConstraints().stream().anyMatch(c -> c instanceof CheckConstraint))
			throw unsupported(
					"column removal requires a nonempty table without partitioning, inheritance or CHECK expressions");
		for (Schema schema : catalog.getSchemas()) {
			if (!schema.getViews().isEmpty() || !schema.getTriggers().isEmpty() || !schema.getMviews().isEmpty()
					|| !schema.getFunctions().isEmpty() || !schema.getProcedures().isEmpty())
				throw unsupported("column removal requires a separate plan for view, trigger and routine dependencies");
		}
		for (Constraint constraint : target.getConstraints()) {
			if (constraint instanceof NotNullConstraint notNull
					&& target.getColumns().get(notNull.getColumnName()) == null)
				throw unsupported("a retained NOT NULL constraint references a removed column");
			if (constraint instanceof UniqueConstraint key
					&& key.getColumns().stream().anyMatch(column -> target.getColumns().get(column.getName()) == null))
				throw unsupported("a retained key references a removed column");
		}
		for (Index index : target.getIndexes()) {
			if (index.getPartitioning() != null || index.getColumns().stream().anyMatch(column -> target.getColumns()
					.get(column.getName()) == null
					|| "true".equals(column.getSpecifics().get(
							com.sqlapp.data.db.dialect.informix.metadata.InformixIndexReader.INFORMIX_INDEX_EXPRESSION))))
				throw unsupported("column removal cannot retain an index with unresolved keys or expressions");
		}
		for (ColumnPrivilege privilege : catalog.getColumnPrivileges()) {
			if (Objects.equals(original.getName(), privilege.getTableName())
					&& Objects.equals(original.getSchemaName(), privilege.getSchemaName())
					&& target.getColumns().get(privilege.getColumnName()) == null)
				throw unsupported("column removal would also remove a modeled column privilege");
		}
	}

	private static void validateBackingRenames(Map<TableId, Table> originals, Map<TableId, Table> targets,
			Set<IndexId> oldBacking, Set<IndexId> backing, IndexChanges indexes) {
		// UniqueConstraint.equals compares the key definition, but omits the backing
		// index name.
		for (var entry : originals.entrySet()) {
			Table target = targets.get(entry.getKey());
			for (Constraint constraint : entry.getValue().getConstraints()) {
				if (!(constraint instanceof UniqueConstraint before)
						|| !(target.getConstraints().get(before.getName()) instanceof UniqueConstraint after)
						|| Objects.equals(before.getIndexName(), after.getIndexName()))
					continue;
				IndexId oldId = new IndexId(entry.getKey(), before.getIndexName());
				IndexId newId = new IndexId(entry.getKey(), after.getIndexName());
				if (!oldBacking.contains(oldId) || !backing.contains(newId) || !indexes.drops().containsKey(oldId)
						|| !indexes.creates().containsKey(newId) || target.getIndexes().get(oldId.name()) != null
						|| entry.getValue().getIndexes().get(newId.name()) != null)
					throw unsupported(
							"backing-index rename requires removing the old named index and adding a new named index");
				var keyColumns = after.getColumns().stream().map(ReferenceColumn::getName).toList();
				if (target.getIndexes().stream()
						.anyMatch(index -> !newId.name().equals(index.getName())
								&& index.getColumns().size() == keyColumns.size()
								&& index.getColumns().stream().map(ReferenceColumn::getName)
										.collect(java.util.stream.Collectors.toSet())
										.equals(new java.util.HashSet<>(keyColumns))))
					throw unsupported("another index on the same key columns makes backing-index selection ambiguous");
			}
		}
	}

	private static boolean hasBackingReferenceChange(Table original, Table target) {
		return original.getConstraints().stream()
				.anyMatch(constraint -> constraint instanceof UniqueConstraint before
						&& target.getConstraints().get(before.getName()) instanceof UniqueConstraint after
						&& !Objects.equals(before.getIndexName(), after.getIndexName()));
	}

	private static IndexChanges indexChanges(Map<TableId, Table> originals, Map<TableId, Table> targets) {
		Map<IndexId, Index> drops = new LinkedHashMap<>(), creates = new LinkedHashMap<>();
		for (var entry : originals.entrySet()) {
			Table target = targets.get(entry.getKey());
			for (Index index : entry.getValue().getIndexes()) {
				Index next = target.getIndexes().get(index.getName());
				if (next == null || !index.equals(next))
					drops.put(new IndexId(entry.getKey(), index.getName()), index);
			}
			for (Index index : target.getIndexes()) {
				Index previous = entry.getValue().getIndexes().get(index.getName());
				if (previous == null || !previous.equals(index))
					creates.put(new IndexId(entry.getKey(), index.getName()), index);
			}
		}
		for (Index index : java.util.stream.Stream.concat(drops.values().stream(), creates.values().stream()).toList())
			if (index.getName() == null || index.getName().isBlank() || index.getColumns().isEmpty()
					|| !index.getIncludes().isEmpty())
				throw unsupported(
						"index changes require named indexes with key columns and no included payload columns");
		return new IndexChanges(drops, creates);
	}

	private static Map<ConstraintId, Constraint> managed(Map<TableId, Table> tables, Map<TableId, Table> otherTables,
			Set<TableId> modified, Set<TableId> constraintsChanged) {
		Map<ConstraintId, Constraint> result = new LinkedHashMap<>();
		for (var entry : tables.entrySet())
			for (Constraint constraint : entry.getValue().getConstraints()) {
				boolean affected = modified.contains(entry.getKey());
				if (!affected && supportsConstraintChange(constraint) && constraintsChanged.contains(entry.getKey())) {
					Constraint other = otherTables.get(entry.getKey()).getConstraints().get(constraint.getName());
					affected = !constraint.equals(other);
				}
				if (constraint instanceof ForeignKeyConstraint foreign) {
					if (modified.isEmpty() && !affected)
						continue;
					Table related = foreign.getRelatedTable();
					if (related == null || !tables.containsKey(id(related)))
						throw unsupported("all foreign-key targets must resolve inside the complete Catalog snapshot");
					if (!Objects.equals(related.getCatalogName(), entry.getValue().getCatalogName()))
						throw unsupported("cross-database foreign keys require a separate migration plan");
					if (foreign.getColumns().isEmpty()
							|| foreign.getColumns().size() != foreign.getRelatedColumns().size()
							|| foreign.getColumns().stream()
									.anyMatch(c -> entry.getValue().getColumns().get(c.getName()) == null)
							|| foreign.getRelatedColumns().stream()
									.anyMatch(c -> tables.get(id(related)).getColumns().get(c.getName()) == null))
						throw unsupported("foreign-key column identities must resolve inside the Catalog snapshot");
					affected |= modified.contains(id(related));
					if (affected && foreign.isVirtual())
						throw unsupported("virtual relationships cannot be dropped as database constraints");
				}
				if (!affected || constraint instanceof NotNullConstraint)
					continue;
				if (!(constraint instanceof ForeignKeyConstraint || constraint instanceof UniqueConstraint
						|| constraint instanceof CheckConstraint))
					throw unsupported("unsupported constraint kind: " + constraint.getClass().getSimpleName());
				if (constraint.getName() == null || constraint.getName().isBlank())
					throw unsupported("constraints must have names for DROP");
				if (constraint instanceof UniqueConstraint unique) {
					if (unique.getColumns().isEmpty() || !unique.getIncludes().isEmpty() || unique.getColumns().stream()
							.anyMatch(c -> entry.getValue().getColumns().get(c.getName()) == null))
						throw unsupported(
								"key constraints require resolved key columns without included payload columns");
					if (unique.isPrimaryKey() && unique.isEnable() && unique.getColumns().stream()
							.anyMatch(c -> !entry.getValue().getColumns().get(c.getName()).isNotNull()))
						throw unsupported("enabled primary-key columns must remain NOT NULL");
				}
				result.put(new ConstraintId(entry.getKey(), constraint.getName()), constraint);
			}
		return result;
	}

	private static Set<IndexId> backingIndexes(Map<ConstraintId, Constraint> constraints) {
		Set<IndexId> result = new LinkedHashSet<>();
		for (var entry : constraints.entrySet())
			if (entry.getValue() instanceof UniqueConstraint unique && unique.getIndexName() != null) {
				Table table = unique.getAncestor(Table.class);
				Index index = table.getIndexes().get(unique.getIndexName());
				if (index == null || index.getName().isBlank() || index.getColumns().isEmpty() || !index.isUnique()
						|| index.getColumns().size() != unique.getColumns().size() || !index.isEnable()
						|| !index.getIncludes().isEmpty()
						|| com.sqlapp.data.db.dialect.informix.util.InformixIndexModes.sql(index) != null)
					throw unsupported(
							"named backing index must be explicitly modeled, unique, enabled and match its key");
				for (int i = 0; i < index.getColumns().size(); i++) {
					ReferenceColumn actual = index.getColumns().get(i), expected = unique.getColumns().get(i);
					if (!Objects.equals(actual.getName(), expected.getName())
							|| (actual.getOrder() == Order.Desc) != (expected.getOrder() == Order.Desc)
							|| "true".equals(actual.getSpecifics().get(
									com.sqlapp.data.db.dialect.informix.metadata.InformixIndexReader.INFORMIX_INDEX_EXPRESSION)))
						throw unsupported("named backing index columns and ordering must match the constraint");
				}
				result.add(new IndexId(entry.getKey().table(), index.getName()));
			}
		return result;
	}

	private static void strip(Catalog catalog, Set<ConstraintId> constraints) {
		Map<TableId, Table> tables = tables(catalog);
		for (ConstraintId constraint : constraints)
			tables.get(constraint.table()).getConstraints().remove(constraint.name());
	}
}
