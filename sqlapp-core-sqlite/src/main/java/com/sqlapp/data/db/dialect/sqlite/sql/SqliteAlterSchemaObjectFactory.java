/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterNamedObjectFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.View;

/**
 * SQLite view/trigger replacement composed from the owning CREATE and DROP
 * factories.
 */
public class SqliteAlterSchemaObjectFactory extends AbstractAlterNamedObjectFactory<SqliteSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(AbstractNamedObject<?> object) {
		throw new IllegalArgumentException(
				"SQLite view/trigger ALTER requires original and target objects; use their difference");
	}

	@Override
	protected List<SqlOperation> doCreateDiffSql(DbObjectDifference difference, Map<String, Difference<?>> changes) {
		return createReplacement(difference, changes, true);
	}

	List<SqlOperation> createViewReplacement(DbObjectDifference difference) {
		return createReplacement(difference, difference.toDifference().getChangedProperties(getDialect()), false);
	}

	private List<SqlOperation> createReplacement(DbObjectDifference difference, Map<String, Difference<?>> changes,
			boolean restoreTriggers) {
		if (!(difference.getOriginal() instanceof AbstractSchemaObject<?> original)
				|| !(difference.getTarget() instanceof AbstractSchemaObject<?> target)
				|| original.getClass() != target.getClass() || !(target instanceof View || target instanceof Trigger))
			throw new IllegalArgumentException(
					"SQLite view/trigger ALTER requires matching original and target object kinds");
		if (changes.isEmpty())
			return List.of();
		if (original.getSchemaName() == null || original.getSchemaName().isBlank() || target.getSchemaName() == null
				|| target.getSchemaName().isBlank())
			throw new IllegalArgumentException(
					"SQLite view/trigger replacement requires explicit original and target database names");
		if (original.getName() == null || original.getName().isBlank() || target.getName() == null
				|| target.getName().isBlank() || original.getName().indexOf('\0') >= 0
				|| target.getName().indexOf('\0') >= 0 || original.getSchemaName().indexOf('\0') >= 0
				|| target.getSchemaName().indexOf('\0') >= 0)
			throw new IllegalArgumentException("SQLite view/trigger replacement requires nonempty names without NUL");
		if (changes.containsKey("catalogName") || changes.containsKey("enable"))
			throw new IllegalArgumentException(
					"SQLite view/trigger replacement cannot change catalog or enabled status");
		if (target instanceof View view && !view.getDefinition().isEmpty() && !changes.containsKey("definition")
				&& (changes.containsKey("statement") || changes.containsKey("columns")))
			throw new IllegalArgumentException(
					"SQLite view definition takes precedence; edit the complete definition or clear it to use statement and columns");
		if (target instanceof Trigger && !changes.containsKey("definition") && changes.keySet().stream()
				.anyMatch(property -> !java.util.Set.of("name", "schemaName", "specificName").contains(property)))
			throw new IllegalArgumentException(
					"SQLite trigger changes require editing the complete CREATE TRIGGER definition; other trigger fields are supplemental metadata");
		// Generate/validate CREATE before exposing any destructive operation.
		var create = new ArrayList<>(getSqlFactoryRegistry().createSql(target, SqlType.CREATE));
		if (restoreTriggers)
			create.addAll(recreateOwnedTriggers(original, target));
		var result = new ArrayList<SqlOperation>(getSqlFactoryRegistry().createSql(original, SqlType.DROP));
		result.addAll(create);
		return result;
	}

	private List<SqlOperation> recreateOwnedTriggers(AbstractSchemaObject<?> original, AbstractSchemaObject<?> target) {
		if (!(original instanceof View) || original.getSchema() == null)
			return List.of();
		var triggers = original.getSchema().getTriggers().stream()
				.filter(trigger -> original.getName().equalsIgnoreCase(trigger.getTableName())
						&& original.getSchemaName().equalsIgnoreCase(trigger.getTableSchemaName()))
				.toList();
		if (triggers.isEmpty())
			return List.of();
		if (!original.getSchemaName().equals(target.getSchemaName()) || target.getSchema() == null)
			throw new IllegalArgumentException(
					"SQLite view with modeled triggers requires the same database and both owning Schemas; explicitly plan recreation");
		boolean rename = !original.getName().equals(target.getName());
		var operations = new ArrayList<SqlOperation>();
		for (var trigger : triggers) {
			var replacement = target.getSchema().getTriggers().get(trigger.getName());
			if (rename) {
				validateRenamedViewTrigger((View) target, trigger, replacement);
				getSqlFactoryRegistry().createSql(trigger.diff(replacement), SqlType.ALTER);
			} else if (replacement == null
					|| !trigger.diff(replacement).toDifference().getChangedProperties(getDialect()).isEmpty())
				throw new IllegalArgumentException(
						"SQLite view and its owned triggers cannot be changed together; explicitly plan recreation: "
								+ trigger.getName());
			operations.addAll(getSqlFactoryRegistry().createSql(replacement, SqlType.CREATE));
		}
		return operations;
	}

	static void validateRenamedViewTrigger(View view, Trigger original, Trigger target) {
		if (target == null || !view.getName().equalsIgnoreCase(target.getTableName())
				|| !view.getSchemaName().equalsIgnoreCase(target.getTableSchemaName())
				|| target.getDefinition().isEmpty() || original.getDefinition().equals(target.getDefinition()))
			throw new IllegalArgumentException(
					"SQLite renamed view requires an explicitly rebound complete trigger definition: "
							+ original.getName());
		validateTriggerTarget(target);
	}

	static void validateTriggerTarget(Trigger trigger) {
		String definition = String.join("\n", trigger.getDefinition());
		String schema = com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl.triggerTargetSchema(definition);
		String name = com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl.triggerTargetName(definition);
		if (!name.equalsIgnoreCase(trigger.getTableName())
				|| schema != null && !schema.equalsIgnoreCase(trigger.getTableSchemaName()))
			throw new IllegalArgumentException(
					"SQLite trigger ON target disagrees with its modeled table/view identity: " + trigger.getName());
	}
}
