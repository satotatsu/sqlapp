/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.Db2RowPermissions;
import com.sqlapp.data.db.dialect.db2.Db2_1010;
import java.util.LinkedHashSet;
import java.util.Set;
import com.sqlapp.data.db.sql.CreateCatalogFactory;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.AbstractDbObjectCollection;

public class Db2CreateCatalogFactory extends CreateCatalogFactory {
	@Override
	public List<SqlOperation> createSql(Catalog catalog) {
		for (Schema schema : catalog.getSchemas()) {
			var snapshot = Db2RowPermissions.get(schema);
			if ((!snapshot.permissions().isEmpty()
					|| snapshot.tables().stream().anyMatch(t -> t.rowActive() || t.columnActive()))
					&& !(getDialect() instanceof Db2_1010)) {
				throw new IllegalArgumentException("Db2 row/column access control requires Db2 LUW 10.1 or later");
			}
			Db2RowPermissions.createSql(schema);
		}
		List<SqlOperation> result = super.createSql(catalog);
		// All schemas' permissions precede every table activation, including
		// cross-schema targets.
		for (boolean activation : new boolean[] { false, true }) {
			for (Schema schema : catalog.getSchemas()) {
				for (String sql : activation ? Db2RowPermissions.activationSql(schema)
						: Db2RowPermissions.permissionSql(schema)) {
					addSql(result, createSqlBuilder()._add(sql), activation ? SqlType.ALTER : SqlType.CREATE, schema);
				}
			}
		}
		return result;
	}

	@Override
	protected void addCreateDbObjectCollection(String propertyName, AbstractDbObjectCollection<?> collection,
			List<SqlOperation> result) {
		if (SchemaObjectProperties.SCHEMAS.getLabel().equals(propertyName)) {
			for (var object : collection) {
				Schema schema = (Schema) object;
				SqlFactory<Schema> factory = getSqlFactoryRegistry().getSqlFactory(schema, SqlType.CREATE);
				if (factory instanceof Db2CreateSchemaFactory db2)
					result.addAll(db2.createBaseSql(schema));
				else
					result.addAll(factory.createSql(schema));
			}
		} else
			super.addCreateDbObjectCollection(propertyName, collection, result);
	}

	@Override
	protected Set<String> getCreateCollectionOrder() {
		Set<String> order = new LinkedHashSet<>(super.getCreateCollectionOrder());
		order.add(SchemaObjectProperties.ROUTINE_PRIVILEGES.getLabel());
		return order;
	}
}
