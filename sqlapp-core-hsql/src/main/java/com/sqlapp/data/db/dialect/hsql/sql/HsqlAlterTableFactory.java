/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.sql;

import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.hsql.HsqlTextTableSources;
import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.ExcludeFilterEqualsHandler;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class HsqlAlterTableFactory extends AbstractAlterTableFactory<HsqlSqlBuilder> {
	@Override
	protected void addOtherDefinitions(Map<String, Difference<?>> differences, Table original, Table target,
			List<SqlOperation> result) {
		boolean oldText = HsqlTextTableSources.isText(original);
		boolean newText = HsqlTextTableSources.isText(target);
		if (oldText != newText || !CommonUtils.eq(original.getSpecifics().get(HsqlTextTableSources.SPECIFICS_KEY),
				target.getSpecifics().get(HsqlTextTableSources.SPECIFICS_KEY))) {
			throw new IllegalArgumentException("HSQLDB text source changes require an explicit file migration");
		}
		if (target.getTableType() != null && !CommonUtils.eq(original.getTableType(), target.getTableType())) {
			if (oldText || target.getTableType() != Table.TableType.Memory
					&& target.getTableType() != Table.TableType.Cache) {
				throw new IllegalArgumentException("HSQLDB table storage change requires an explicit migration");
			}
			var builder = createSqlBuilder();
			builder.set().table().name(target, getOptions().isDecorateSchemaName()).space();
			builder._add(target.getTableType() == Table.TableType.Cache ? "TYPE CACHED" : "TYPE MEMORY");
			add(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		if (target.getReadonly() != null && !CommonUtils.eq(original.getReadonly(), target.getReadonly())) {
			var builder = createSqlBuilder();
			builder.set().table().name(target, getOptions().isDecorateSchemaName()).space();
			builder._add(Boolean.TRUE.equals(target.getReadonly()) ? "READ ONLY" : "READ WRITE");
			add(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
	}

	@Override
	protected void addAlterColumn(Table originalTable, Table table, Column oldColumn, Column column,
			DbObjectDifference diff, List<SqlOperation> result) {
		if (!CommonUtils.eq(oldColumn.getFormula(), column.getFormula())) {
			throw new IllegalArgumentException(
					"Use HsqlTableMigration.plan for a verified HSQLDB generated expression migration: "
							+ table.getName() + "." + column.getName());
		}
		HsqlSqlBuilder builder = createSqlBuilder();
		ExcludeFilterEqualsHandler handler = new ExcludeFilterEqualsHandler("default",
				SchemaProperties.NOT_NULL.getLabel());
		if (!oldColumn.equals(column, handler)) {
			builder.alter().table();
			builder.name(table, this.getOptions().isDecorateSchemaName());
			builder.alterColumn();
			builder.name(column);
			builder.space().definitionForAlterColumn(column);
			add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
		}
		//
		if (!CommonUtils.eq(oldColumn.getDefaultValue(), column.getDefaultValue())) {
			builder = createSqlBuilder();
			builder.alter().table();
			builder.name(table, this.getOptions().isDecorateSchemaName());
			builder.alterColumn();
			builder.name(column);
			builder.space().appendAlterColumnDefaultDefinition(column);
			add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
		}
		//
		if (!CommonUtils.eq(oldColumn.isNotNull(), column.isNotNull())) {
			builder = createSqlBuilder();
			builder.alter().table();
			builder.name(table, this.getOptions().isDecorateSchemaName());
			builder.alterColumn();
			builder.name(column);
			builder.space();
			if (column.isNotNull()) {
				builder.setNotNull();
			} else {
				builder.setNull();
			}
			add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
		}
	}

	@Override
	protected void addRenameColumn(Table originalTable, Table table, Column oldColumn, Column column,
			DbObjectDifference diff, List<SqlOperation> result) {
		HsqlSqlBuilder builder = createSqlBuilder();
		builder.alter().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.alterColumn();
		builder.name(oldColumn);
		builder.space();
		builder.rename().to();
		builder.name(column);
		add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
	}

	@Override
	protected void addDropIndexDefinition(Index obj, HsqlSqlBuilder builder) {
		builder.drop().index();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addCreateIndexDefinition(Table originalTable, Table table, Index originalIndex, Index index,
			DbObjectDifference diff, List<SqlOperation> result) {
		if (index.getName().startsWith("SYS_IDX_")) {
			return;
		}
		super.addCreateIndexDefinition(originalTable, table, originalIndex, index, diff, result);
	}

	@Override
	protected void addDropIndexDefinition(Table originalTable, Table table, Index originalIndex, Index index,
			DbObjectDifference diff, List<SqlOperation> result) {
		if (originalIndex.getName().startsWith("SYS_IDX_")) {
			return;
		}
		super.addDropIndexDefinition(originalTable, table, originalIndex, index, diff, result);
	}
}
