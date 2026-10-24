/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.db2.Db2RowPermissions;
import com.sqlapp.data.db.dialect.db2.Db2_1010;
import com.sqlapp.data.db.sql.CreateSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaObjectProperties;

public class Db2CreateSchemaFactory extends CreateSchemaFactory {
	@Override
	protected Set<String> getCreateCollectionOrder() {
		Set<String> order = new LinkedHashSet<>();
		for (String collection : super.getCreateCollectionOrder()) {
			if (collection.equals(SchemaObjectProperties.CONSTANTS.getLabel()))
				continue;
			order.add(collection);
			if (collection.equals(SchemaObjectProperties.PROCEDURES.getLabel()))
				order.add(SchemaObjectProperties.CONSTANTS.getLabel());
		}
		order.add(SchemaObjectProperties.MASKS.getLabel());
		return order;
	}

	List<SqlOperation> createBaseSql(Schema schema) {
		// Validate the extension before returning any partial plan.
		validateRowPermissions(schema);
		return super.createSql(schema);
	}

	private void validateRowPermissions(Schema schema) {
		var snapshot = Db2RowPermissions.get(schema);
		if ((!snapshot.permissions().isEmpty()
				|| snapshot.tables().stream().anyMatch(t -> t.rowActive() || t.columnActive()))
				&& !(getDialect() instanceof Db2_1010)) {
			throw new IllegalArgumentException("Db2 row/column access control requires Db2 LUW 10.1 or later");
		}
		Db2RowPermissions.createSql(schema);
	}

	@Override
	public List<SqlOperation> createSql(Schema schema) {
		List<SqlOperation> result = createBaseSql(schema);
		appendSecurity(schema, result, false);
		appendSecurity(schema, result, true);
		return result;
	}

	void appendSecurity(Schema schema, List<SqlOperation> result, boolean activation) {
		for (String sql : activation ? Db2RowPermissions.activationSql(schema)
				: Db2RowPermissions.permissionSql(schema)) {
			var builder = createSqlBuilder();
			builder._add(sql);
			addSql(result, builder, activation ? SqlType.ALTER : SqlType.CREATE, schema);
		}
	}
}
