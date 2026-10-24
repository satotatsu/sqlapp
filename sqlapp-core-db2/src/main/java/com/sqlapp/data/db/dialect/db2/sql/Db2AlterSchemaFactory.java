/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.db2.Db2RowPermissions;
import com.sqlapp.data.db.dialect.db2.Db2_1010;
import com.sqlapp.data.db.sql.AlterSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaObjectProperties;

public class Db2AlterSchemaFactory extends AlterSchemaFactory {
	@Override
	protected Set<String> getAlterCollectionOrder() {
		Set<String> order = new LinkedHashSet<>(super.getAlterCollectionOrder());
		order.add(SchemaObjectProperties.MASKS.getLabel());
		return order;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Schema original = (Schema) difference.getOriginal(), target = (Schema) difference.getTarget();
		if (original == null || target == null)
			return super.createDiffSql(difference);
		var plan = Db2RowPermissions.diff(original, target);
		if ((!plan.beforeSql().isEmpty() || !plan.afterSql().isEmpty()) && !(getDialect() instanceof Db2_1010)) {
			throw new IllegalArgumentException("Db2 row/column access control requires Db2 LUW 10.1 or later");
		}
		List<SqlOperation> result = new ArrayList<>();
		for (String sql : plan.beforeSql())
			addSql(result, createSqlBuilder(getDialect())._add(sql), SqlType.DROP, original);
		Schema beforeNative = original.clone(), afterNative = target.clone();
		beforeNative.setDialect(getDialect());
		afterNative.setDialect(getDialect());
		Db2RowPermissions.set(beforeNative, List.of(), List.of());
		Db2RowPermissions.set(afterNative, List.of(), List.of());
		result.addAll(super.createDiffSql(beforeNative.diff(afterNative)));
		for (String sql : plan.afterSql())
			addSql(result, createSqlBuilder(getDialect())._add(sql), SqlType.ALTER, target);
		return result;
	}
}
