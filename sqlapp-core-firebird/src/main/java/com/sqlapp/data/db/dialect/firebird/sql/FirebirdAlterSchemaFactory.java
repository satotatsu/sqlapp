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
		var before = FirebirdSchemaObjects.get(original);
		var after = FirebirdSchemaObjects.get(target);
		for (var object : before) {
			if (!after.contains(object))
				throw new UnsupportedOperationException(
						"Changing/removing Firebird native schema objects requires an explicit dependency-aware migration: "
								+ object.name());
		}
		var result = new ArrayList<SqlOperation>();
		if (!publicationSql.isEmpty())
			result.add(new SqlOperation(publicationSql.getFirst(), SqlType.ALTER, (Schema) difference.getTarget()));
		for (var object : after)
			if (!before.contains(object))
				for (String sql : object.definition())
					result.add(new SqlOperation(sql, SqlType.CREATE, target));
		original.getSpecifics().remove(FirebirdSchemaObjects.SPECIFICS_KEY);
		target.getSpecifics().remove(FirebirdSchemaObjects.SPECIFICS_KEY);
		original.setDialect(getDialect());
		target.setDialect(getDialect());
		result.addAll(FirebirdSchemaAlterOrder.order((Schema) difference.getOriginal(), (Schema) difference.getTarget(),
				super.createDiffSql(original.diff(target))));
		for (String sql : publicationSql.stream().skip(1).toList())
			result.add(new SqlOperation(sql, SqlType.ALTER, (Schema) difference.getTarget()));
		return result;
	}
}
