/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;

/**
 * Firebird create table Operation factory
 * 
 * @author tatsuo satoh
 * 
 */
public class FirebirdCreateTableFactory extends AbstractCreateTableFactory<FirebirdSqlBuilder> {
	@Override
	protected void addCreateIndexDefinition(com.sqlapp.data.schemas.Table table, com.sqlapp.data.schemas.Index index,
			java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		result.addAll(getSqlFactoryRegistry().createSql(index, com.sqlapp.data.db.sql.SqlType.CREATE));
	}

	@Override
	protected void addOtherDefinitions(com.sqlapp.data.schemas.Table table,
			java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		if (table.getRemarks() != null)
			result.addAll(getSqlFactoryRegistry().createSql(table, com.sqlapp.data.db.sql.SqlType.SET_COMMENT));
		for (var column : table.getColumns())
			if (column.getRemarks() != null)
				result.addAll(getSqlFactoryRegistry().createSql(column, com.sqlapp.data.db.sql.SqlType.SET_COMMENT));
		for (var key : table.getConstraints().getForeignKeyConstraints()) {
			var builder = createSqlBuilder();
			FirebirdConstraintSql.indexComment(key, builder);
			if (key.getSpecifics().get("firebird.indexComment") != null)
				addSql(result, builder, com.sqlapp.data.db.sql.SqlType.SET_COMMENT, key);
		}
	}

	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(com.sqlapp.data.schemas.Table table) {
		FirebirdConstraintSql.validateTable(table);
		var result = super.createSql(table);
		if (table.getTableType() == com.sqlapp.data.schemas.Table.TableType.Temporary
				&& table.getSpecifics().containsKey("firebird.sqlSecurity")) {
			var builder = createSqlBuilder()._add("ALTER TABLE ").name(table, getOptions().isDecorateSchemaName())
					._add(" ALTER");
			FirebirdRoutineSql.addSecurity(table.getSpecifics(), builder);
			addSql(result, builder, com.sqlapp.data.db.sql.SqlType.ALTER, table);
		}
		return result;
	}

	@Override
	protected void addCreateObject(com.sqlapp.data.schemas.Table table, FirebirdSqlBuilder builder) {
		String externalFile = table.getSpecifics().get("firebird.external.file");
		if (externalFile != null || table.getTableType() == com.sqlapp.data.schemas.Table.TableType.File) {
			if (externalFile == null || externalFile.isBlank())
				throw new IllegalArgumentException("Firebird external table requires firebird.external.file");
			if (table.getTableType() == com.sqlapp.data.schemas.Table.TableType.Temporary
					|| table.getSpecifics().containsKey("firebird.onCommit"))
				throw new IllegalArgumentException("Firebird external table cannot be temporary");
			if (!table.getConstraints().isEmpty() || !table.getIndexes().isEmpty())
				throw new IllegalArgumentException("Firebird external tables cannot have constraints or indexes");
			for (var column : table.getColumns())
				if (column.getArrayDimension() > 0 || column.isIdentity() || column.getFormula() != null
						|| column.getDataType() == com.sqlapp.data.db.datatype.DataType.BLOB
						|| column.getDataType() == com.sqlapp.data.db.datatype.DataType.CLOB)
					throw new IllegalArgumentException(
							"Firebird external table cannot contain arrays, blobs, computed or identity columns");
			builder._add("CREATE TABLE ").name(table.getName())._add(" EXTERNAL FILE ").sqlChar(externalFile);
			return;
		}
		if (table.getTableType() == com.sqlapp.data.schemas.Table.TableType.Temporary) {
			FirebirdTemporaryTableSql.requireSupportedDialect(builder);
			builder.create().global().temporary().table().name(table, getOptions().isDecorateSchemaName());
		} else {
			if (table.getSpecifics().containsKey("firebird.onCommit"))
				throw new IllegalArgumentException("firebird.onCommit requires a temporary table");
			super.addCreateObject(table, builder);
		}
	}

	@Override
	protected void addOption(com.sqlapp.data.schemas.Table table, FirebirdSqlBuilder builder) {
		if (table.getTableType() == com.sqlapp.data.schemas.Table.TableType.Temporary)
			FirebirdTemporaryTableSql.addOnCommit(table,
					getTableOptions().getTempTableOnCommitPreserveRows().test(table), builder);
		else
			FirebirdRoutineSql.addSecurity(table.getSpecifics(), builder);
	}

}
