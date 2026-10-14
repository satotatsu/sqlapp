/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;
import java.util.Map;
import com.sqlapp.data.db.sql.AlterCatalogFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects;

/** Composes database-wide and schema-scoped PostgreSQL object differences. */
public class PostgresAlterCatalogFactory extends AlterCatalogFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		return createDiffSql(difference, Map.of());
	}

	/**
	 * External values are resolved before a plan is returned, without changing the
	 * model.
	 */
	public List<SqlOperation> createDiffSql(DbObjectDifference difference, Map<String, String> externalValues) {
		var original = (Catalog) difference.getOriginal();
		var target = (Catalog) difference.getTarget();
		var plan = new PostgresSchemaObjectDiff(original, target, externalValues, getDialect());
		var localDrops = new java.util.ArrayList<SqlOperation>();
		var removedSchemaObjects = new java.util.ArrayList<SqlOperation>();
		for (var schema : original.getSchemas()) {
			var next = target.getSchemas().get(schema.getName());
			if (next == null) {
				next = new com.sqlapp.data.schemas.Schema(schema.getName());
				var nativeSchema = schema.clone();
				nativeSchema.setDialect(getDialect());
				next.setDialect(getDialect());
				PostgresAlterSchemaFactory.stripExtensionMembers(nativeSchema);
				var factory = getSqlFactoryRegistry().getSqlFactory(nativeSchema, com.sqlapp.data.db.sql.SqlType.ALTER);
				factory.setOptions(getOptions().clone());
				removedSchemaObjects.addAll(factory.createDiffSql(nativeSchema.diff(next)));
			}
			var local = new PostgresSchemaObjectDiff(schema, next, externalValues, getDialect());
			localDrops.addAll(local.drops);
			plan.before.addAll(local.before);
			plan.foreignTables.addAll(local.foreignTables);
			plan.after.addAll(local.after);
		}
		for (var schema : target.getSchemas())
			if (original.getSchemas().get(schema.getName()) == null) {
				var local = new PostgresSchemaObjectDiff(new com.sqlapp.data.schemas.Schema(schema.getName()), schema,
						externalValues, getDialect());
				plan.before.addAll(local.before);
				plan.foreignTables.addAll(local.foreignTables);
				plan.after.addAll(local.after);
			}
		// Global dependents (publications/subscriptions) precede local drops;
		// prerequisites follow them.
		int split = 0;
		while (split < plan.drops.size() && (plan.drops.get(split).getSqlText().startsWith("DROP PUBLICATION")
				|| plan.drops.get(split).getSqlText().startsWith("ALTER SUBSCRIPTION")))
			split++;
		plan.drops.addAll(split, localDrops);
		// Only the shared fields go through the shared factories; otherwise schema
		// payloads run twice.
		var nativeOriginal = original.clone();
		var nativeTarget = target.clone();
		for (var catalog : List.of(nativeOriginal, nativeTarget)) {
			catalog.setDialect(getDialect());
			catalog.getSpecifics().remove(PostgresSchemaObjects.SPECIFICS_KEY);
			for (var schema : catalog.getSchemas()) {
				schema.setDialect(getDialect());
				PostgresAlterSchemaFactory.stripExtensionMembers(schema);
			}
		}
		var nativeSql = super.createDiffSql(nativeOriginal.diff(nativeTarget));
		nativeSql.addAll(0, removedSchemaObjects);
		return PostgresAlterSchemaFactory.compose(plan, nativeSql);
	}
}
