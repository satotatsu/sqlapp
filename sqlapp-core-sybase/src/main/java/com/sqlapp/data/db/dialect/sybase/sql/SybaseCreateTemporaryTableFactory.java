/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sybase.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.sybase.util.SybaseSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTemporaryTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

/**
 * Sybase用のTEMPテーブル作成
 * 
 * @author tatsuo satoh
 * 
 */
public class SybaseCreateTemporaryTableFactory extends AbstractCreateTemporaryTableFactory<SybaseSqlBuilder> {

	@Override
	protected void addCreateObject(final Table obj, final SybaseSqlBuilder builder) {
		String prefix = this.getTableOptions().getTemporaryTableNamePrefix().apply(obj);
		String suffix = this.getTableOptions().getTemporaryTableNameSuffix().apply(obj);
		builder.create().table();
		builder.space()._add(
				this.getDialect().getTemporaryTableName(obj, prefix, suffix, this.getOptions().isDecorateSchemaName()));
	}

	@Override
	protected void addCreateIndexDefinition(final Table table, final Index index, final List<SqlOperation> result) {
		final SybaseSqlBuilder builder = createSqlBuilder();
		addCreateIndexDefinition(index, builder);
		add(result, createOperation(builder.toString(), SqlType.CREATE, index));
	}

	/**
	 * オプションを追加します
	 * 
	 * @param table
	 * @param builder
	 */
	@Override
	protected void addOption(final Table table, final SybaseSqlBuilder builder) {
	}
}
