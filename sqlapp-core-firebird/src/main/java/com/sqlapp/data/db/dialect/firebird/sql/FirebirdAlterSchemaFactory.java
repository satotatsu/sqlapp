/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects;
import com.sqlapp.data.db.sql.AlterSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Schema;

/**
 * Adds prerequisites without guessing destructive native-object replacements.
 */
public class FirebirdAlterSchemaFactory extends AlterSchemaFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		return generate(difference, null);
	}

	/**
	 * Disables publication before shared DDL, then restores membership and optional
	 * activation last.
	 */
	public List<SqlOperation> createDiffSql(DbObjectDifference difference,
			com.sqlapp.data.db.dialect.firebird.FirebirdPublications.Activation activation) {
		return generate(difference, java.util.Objects.requireNonNull(activation, "activation"));
	}

	private List<SqlOperation> generate(DbObjectDifference difference,
			com.sqlapp.data.db.dialect.firebird.FirebirdPublications.Activation activation) {
		var original = ((Schema) difference.getOriginal()).clone();
		var target = ((Schema) difference.getTarget()).clone();
		com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.get(original);
		com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.get(target);
		original.getSpecifics().remove(com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.SPECIFICS_KEY);
		target.getSpecifics().remove(com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.SPECIFICS_KEY);
		if (activation == null && !com.sqlapp.data.db.dialect.firebird.FirebirdPublications.get(original)
				.equals(com.sqlapp.data.db.dialect.firebird.FirebirdPublications.get(target)))
			throw new UnsupportedOperationException(
					"Publication policy changes require the explicit FirebirdAlterSchemaFactory.createDiffSql(difference, Activation) overload");
		List<String> publicationSql = List.of();
		if (activation != null) {
			if (com.sqlapp.data.db.dialect.firebird.FirebirdPublications.get(target).isEmpty())
				throw new IllegalArgumentException("Explicit publication restore requires a target publication policy");
			publicationSql = com.sqlapp.data.db.dialect.firebird.FirebirdPublications.restoreSql(target, getDialect(),
					activation);
		}
		original.getSpecifics().remove(com.sqlapp.data.db.dialect.firebird.FirebirdPublications.SPECIFICS_KEY);
		target.getSpecifics().remove(com.sqlapp.data.db.dialect.firebird.FirebirdPublications.SPECIFICS_KEY);
		var result = new ArrayList<SqlOperation>();
		if (!publicationSql.isEmpty())
			result.add(new SqlOperation(publicationSql.getFirst(), SqlType.ALTER, (Schema) difference.getTarget()));
		original.getSpecifics().remove(FirebirdSchemaObjects.SPECIFICS_KEY);
		target.getSpecifics().remove(FirebirdSchemaObjects.SPECIFICS_KEY);
		var removedKeys = original.getTables().stream()
				.filter(table -> target.getTables().stream().noneMatch(next -> next.getName().equals(table.getName())))
				.flatMap(table -> table.getConstraints().getForeignKeyConstraints().stream()).toList();
		var addedKeys = target.getTables().stream()
				.filter(table -> original.getTables().stream()
						.noneMatch(previous -> previous.getName().equals(table.getName())))
				.flatMap(table -> table.getConstraints().getForeignKeyConstraints().stream()).toList();
		for (var table : target.getTables())
			if (addedKeys.stream().anyMatch(key -> key.getTable() == table))
				getSqlFactoryRegistry().createSql(table, SqlType.CREATE);
		for (var key : addedKeys) {
			String provider = key.getRelatedTable().getName();
			if (original.getTables().stream().anyMatch(table -> table.getName().equals(provider))
					&& target.getTables().stream().noneMatch(table -> table.getName().equals(provider)))
				throw new UnsupportedOperationException(
						"Firebird target foreign key references a removed table: " + provider);
		}
		var keyAdditions = FirebirdSchemaForeignKeys.create(getSqlFactoryRegistry(), addedKeys);
		var orderedOriginal = FirebirdSchemaForeignKeys.without((Schema) difference.getOriginal(), removedKeys);
		var orderedTarget = FirebirdSchemaForeignKeys.without((Schema) difference.getTarget(), addedKeys);
		var shared = new ArrayList<SqlOperation>();
		for (var key : removedKeys)
			shared.addAll(getSqlFactoryRegistry().createSql(key, SqlType.DROP));
		var ddlOriginal = FirebirdSchemaForeignKeys.without(original, removedKeys);
		var ddlTarget = FirebirdSchemaForeignKeys.without(target, addedKeys);
		ddlOriginal.setDialect(getDialect());
		ddlTarget.setDialect(getDialect());
		shared.addAll(FirebirdSchemaAlterOrder.order(orderedOriginal, orderedTarget,
				super.createDiffSql(ddlOriginal.diff(ddlTarget))));
		shared.addAll(keyAdditions);
		var nativePlan = FirebirdNativeSchemaAlter.plan((Schema) difference.getOriginal(),
				(Schema) difference.getTarget(), shared);
		result.addAll(nativePlan.before());
		result.addAll(shared);
		result.addAll(nativePlan.after());
		for (String sql : publicationSql.stream().skip(1).toList())
			result.add(new SqlOperation(sql, SqlType.ALTER, (Schema) difference.getTarget()));
		return result;
	}
}
