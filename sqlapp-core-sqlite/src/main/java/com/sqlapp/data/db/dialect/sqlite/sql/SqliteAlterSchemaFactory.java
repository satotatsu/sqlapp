/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.View;

/** Coordinates the implicit removal of triggers by SQLite DROP VIEW. */
public class SqliteAlterSchemaFactory extends AbstractAlterSchemaFactory<SqliteSqlBuilder> {

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		var original = difference.getOriginal(Schema.class);
		var target = difference.getTarget(Schema.class);
		if (original == null || target == null)
			throw new IllegalArgumentException("SQLite Schema ALTER requires original and target Schemas");
		var changes = difference.toDifference().getChangedProperties(getDialect());
		var views = (DbObjectDifferenceCollection) changes.get("views");
		if (views == null)
			return super.createDiffSql(difference);
		var replacements = views.getList(State.Modified, State.Deleted).stream().filter(diff -> {
			var view = diff.getOriginal(View.class);
			return original.getTriggers().stream().anyMatch(trigger -> owns(view, trigger));
		}).toList();
		if (replacements.isEmpty())
			return super.createDiffSql(difference);
		Set<String> removedTriggers = new LinkedHashSet<>();
		for (var replacement : replacements) {
			var oldView = replacement.getOriginal(View.class);
			var newView = replacement.getTarget(View.class);
			if (newView != null && !java.util.Objects.equals(oldView.getSchemaName(), newView.getSchemaName()))
				throw new IllegalArgumentException(
						"SQLite view move with owned triggers requires an explicit recreation plan");
			for (var trigger : original.getTriggers()) {
				if (!owns(oldView, trigger))
					continue;
				removedTriggers.add(trigger.getName());
				var next = target.getTriggers().get(trigger.getName());
				// Retain the trigger factory's definition/supplemental-field validation.
				if (newView == null && next != null && owns(oldView, next))
					throw new IllegalArgumentException(
							"SQLite deleted view still has a target trigger: " + next.getName());
				if (next != null && newView != null && !oldView.getName().equals(newView.getName()))
					SqliteAlterSchemaObjectFactory.validateRenamedViewTrigger(newView, trigger, next);
				if (next != null && (!java.util.Objects.equals(trigger.getTableName(), next.getTableName())
						|| !java.util.Objects.equals(trigger.getTableSchemaName(), next.getTableSchemaName())))
					SqliteAlterSchemaObjectFactory.validateTriggerTarget(next);
				if (next != null)
					getSqlFactoryRegistry().createSql(trigger.diff(next), SqlType.ALTER);
			}
		}
		// A fresh planner keeps coordination local to this invocation, including
		// failures.
		var planner = new AbstractAlterSchemaFactory<SqliteSqlBuilder>() {
			@Override
			protected void addAlterSchemaObjects(String property, DbObjectDifferenceCollection collection,
					List<SqlOperation> operations) {
				if ("views".equals(property)) {
					operations.addAll(createDiffSql(
							collection.getList().stream().filter(diff -> !replacements.contains(diff)).toList()));
					for (var replacement : replacements) {
						if (replacement.getState() == State.Deleted) {
							operations.addAll(createDiffSql(List.of(replacement)));
							continue;
						}
						var factory = (SqliteAlterSchemaObjectFactory) getSqlFactoryRegistry()
								.getSqlFactory(replacement.getTarget(View.class), SqlType.ALTER);
						operations.addAll(factory.createViewReplacement(replacement));
					}
				} else if ("triggers".equals(property)) {
					operations
							.addAll(createDiffSql(collection.getList().stream()
									.filter(diff -> diff.getOriginal() == null
											|| !removedTriggers.contains(diff.getOriginal(Trigger.class).getName()))
									.toList()));
				} else {
					super.addAlterSchemaObjects(property, collection, operations);
				}
			}
		};
		planner.setSqlFactoryRegistry(getSqlFactoryRegistry());
		planner.setOptions(getOptions());
		var operations = planner.createDiffSql(difference);
		for (String name : removedTriggers) {
			var trigger = target.getTriggers().get(name);
			if (trigger != null)
				operations.addAll(getSqlFactoryRegistry().createSql(trigger, SqlType.CREATE));
		}
		return operations;
	}

	private static boolean owns(View view, Trigger trigger) {
		return view.getName().equalsIgnoreCase(trigger.getTableName()) && view.getSchemaName() != null
				&& view.getSchemaName().equalsIgnoreCase(trigger.getTableSchemaName());
	}
}
