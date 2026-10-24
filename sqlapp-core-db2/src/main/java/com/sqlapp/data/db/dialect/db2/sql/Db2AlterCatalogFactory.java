/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import com.sqlapp.data.db.dialect.db2.Db2RowPermissions;
import com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges;
import com.sqlapp.data.db.dialect.db2.Db2Federation;
import com.sqlapp.data.db.dialect.db2.Db2_1010;
import com.sqlapp.data.db.sql.AlterCatalogFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Schema;

public class Db2AlterCatalogFactory extends AlterCatalogFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Catalog original = (Catalog) difference.getOriginal(), target = (Catalog) difference.getTarget();
		if (original == null || target == null)
			return super.createDiffSql(difference);
		var objectGrants = Db2ObjectPrivileges.diff(original, target);
		var databaseGrants = Db2DatabasePrivilegePlan.diff(original, target, getSqlFactoryRegistry());
		var federation = Db2FederationSql.diff(original, target, getDialect());
		var mappings = Db2UserMappingSql.diff(original, target, getDialect());
		var names = new LinkedHashSet<String>();
		original.getSchemas().forEach(s -> names.add(s.getName()));
		target.getSchemas().forEach(s -> names.add(s.getName()));
		var plans = new ArrayList<Db2RowPermissions.ChangePlan>();
		for (String name : names) {
			Schema before = original.getSchemas().get(name), after = target.getSchemas().get(name);
			plans.add(Db2RowPermissions.diff(before == null ? new Schema(name) : before,
					after == null ? new Schema(name) : after));
		}
		if (!(getDialect() instanceof Db2_1010)
				&& plans.stream().anyMatch(p -> !p.beforeSql().isEmpty() || !p.afterSql().isEmpty())) {
			throw new IllegalArgumentException("Db2 row/column access control requires Db2 LUW 10.1 or later");
		}
		List<SqlOperation> result = new ArrayList<>();
		for (String sql : mappings.before())
			addSql(result, createSqlBuilder(getDialect())._add(sql), SqlType.DROP, original);
		for (String sql : federation)
			addSql(result, createSqlBuilder(getDialect())._add(sql), SqlType.ALTER, target);
		for (String sql : mappings.after())
			addSql(result, createSqlBuilder(getDialect())._add(sql),
					sql.startsWith("CREATE ") ? SqlType.CREATE : SqlType.ALTER, target);
		result.addAll(databaseGrants.before());
		for (String sql : objectGrants.beforeSql())
			addSql(result, createSqlBuilder(getDialect())._add(sql), SqlType.REVOKE, original);
		for (var plan : plans)
			for (String sql : plan.beforeSql())
				addSql(result, createSqlBuilder(getDialect())._add(sql), SqlType.DROP, original);
		Catalog beforeNative = original.clone(), afterNative = target.clone();
		beforeNative.setDialect(getDialect());
		afterNative.setDialect(getDialect());
		Db2ObjectPrivileges.set(beforeNative, List.of());
		Db2ObjectPrivileges.set(afterNative, List.of());
		beforeNative.getUserPrivileges().clear();
		afterNative.getUserPrivileges().clear();
		Db2Federation.set(beforeNative, List.of(), List.of(), List.of());
		Db2Federation.set(afterNative, List.of(), List.of(), List.of());
		com.sqlapp.data.db.dialect.db2.Db2UserMappings.set(beforeNative, List.of());
		com.sqlapp.data.db.dialect.db2.Db2UserMappings.set(afterNative, List.of());
		for (Schema schema : beforeNative.getSchemas())
			Db2RowPermissions.set(schema, List.of(), List.of());
		for (Schema schema : afterNative.getSchemas())
			Db2RowPermissions.set(schema, List.of(), List.of());
		result.addAll(super.createDiffSql(beforeNative.diff(afterNative)));
		result.addAll(databaseGrants.after());
		for (String sql : objectGrants.afterSql())
			addSql(result, createSqlBuilder(getDialect())._add(sql), SqlType.GRANT, target);
		// Schema transitions can target tables owned by other schemas.
		for (boolean activation : new boolean[] { false, true }) {
			for (var plan : plans)
				for (String sql : plan.afterSql()) {
					if (sql.startsWith("ALTER TABLE ") == activation)
						addSql(result, createSqlBuilder(getDialect())._add(sql), SqlType.ALTER, target);
				}
		}
		return result;
	}
}
