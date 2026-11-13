/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.dialect.db2.Db2RowPermissions;
import com.sqlapp.data.db.sql.CreateSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaObjectProperties;

/**
 * Host schema CREATE: column masks follow the other schema objects, then row
 * permissions, then explicit ACTIVATE ROW/COLUMN ACCESS CONTROL. Activation
 * comes last so tables are never activated before their permissions exist. ADD
 * VERSIONING statements follow all tables so history tables exist first.
 */
public class Db2HostCreateSchemaFactory extends CreateSchemaFactory {
	@Override
	protected Set<String> getCreateCollectionOrder() {
		Set<String> order = new LinkedHashSet<>(super.getCreateCollectionOrder());
		order.remove(SchemaObjectProperties.MASKS.getLabel());
		order.add(SchemaObjectProperties.MASKS.getLabel());
		return order;
	}

	@Override
	public List<SqlOperation> createSql(Schema schema) {
		// Validate the snapshot before returning any partial plan.
		Db2RowPermissions.createSql(schema);
		List<SqlOperation> created = super.createSql(schema);
		// History tables must exist before ADD VERSIONING, whatever the table order.
		List<SqlOperation> result = new java.util.ArrayList<>();
		List<SqlOperation> versioning = new java.util.ArrayList<>();
		for (SqlOperation operation : created)
			(Db2HostCreateTableFactory.isVersioning(operation) ? versioning : result).add(operation);
		result.addAll(versioning);
		for (String sql : Db2RowPermissions.permissionSql(schema))
			addSql(result, createSqlBuilder()._add(sql), SqlType.CREATE, schema);
		for (String sql : Db2RowPermissions.activationSql(schema))
			addSql(result, createSqlBuilder()._add(sql), SqlType.ALTER, schema);
		return result;
	}
}
