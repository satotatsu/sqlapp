/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.db.sql.AddObjectDetail;
import com.sqlapp.data.db.sql.AddTableObjectDetailFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

public class Db2CreateTableFactory extends
		AbstractCreateTableFactory<Db2SqlBuilder> {

	@Override
	protected void addCreateObject(final Table table, final Db2SqlBuilder builder) {
		builder.create().table().ifNotExists(this.getOptions().isCreateIfNotExists());
		builder.name(table, this.getOptions().isDecorateSchemaName());
	}

	/**
	 * オプションを追加します
	 * 
	 * @param table
	 * @param builder
	 */
	@Override
	protected void addOption(final Table table, final Db2SqlBuilder builder) {
		addTableOption(table, builder);
		addPartitionByDefinition(table, builder);
	}

	protected void addTableOption(final Table table, final Db2SqlBuilder builder) {
		addCollateDefinition(table, builder);
		addAutoIncrementDefinition(table, builder);
		addRemarkDefinition(table, builder);
	}

	@Override
	protected void addIndexDefinitions(final Table table,final List<SqlOperation> result) {
	}
	
	/**
	 * Collate定義を追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addCollateDefinition(final Table table,
			final Db2SqlBuilder builder) {
		if (!CommonUtils.isEmpty(table.getCollation())) {
			builder.collate().eq()._add(table.getCollation());
		}
	}

	/**
	 * COMMENT定義を追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addRemarkDefinition(final Table table,
			final Db2SqlBuilder builder) {
		if (!CommonUtils.isEmpty(table.getRemarks())) {
			builder.comment()
					.eq().space()
					.sqlChar(table.getRemarks());
		}
	}

	/**
	 * AUTO_INCREMENTの初期値を設定DDLを取得します
	 * 
	 * @param colDiff
	 * @param builder
	 */
	protected void addAutoIncrementDefinition(final Table table,
			final Db2SqlBuilder builder) {
		for (final Column column : table.getColumns()) {
			if (!column.isIdentity()) {
				continue;
			} else {
				Long current = column.getIdentityLastValue();
				if (current == null) {
					current = column.getIdentityStartValue();
				}
				if (current != null
						&& !CommonUtils.eq(current, Long.valueOf(1))) {
					builder.space()
							.property("AUTO_INCREMENT", current);
				}
				return;
			}
		}
	}

	/**
	 * Partition定義を追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addPartitionByDefinition(final Table table,
			final Db2SqlBuilder builder) {
		if (table.getPartitioning() != null) {
			final AddObjectDetail<Partitioning, AbstractSqlBuilder<?>> addObjectDetail=getAddObjectDetail(table.getPartitioning(), SqlType.CREATE);
			addObjectDetail.addObjectDetail(table.getPartitioning(), builder);
		}
	}

	@Override
	protected void addConstraintDefinitions(final Table table, final Db2SqlBuilder builder){
		super.addConstraintDefinitions(table, builder);
		addIndexDefinitions(table, builder);
	}
	
	
	/**
	 * インデックスを追加します
	 * 
	 * @param index
	 * @param builder
	 */
	@Override
	protected void addIndexDefinitions(final Table table, final Db2SqlBuilder builder) {
		for(final Index index:table.getIndexes()){
			if (!table.getConstraints().contains(index.getName())) {
				addIndexDefinition(index, builder);
			}
		}
	}

	/**
	 * インデックスを追加します
	 * 
	 * @param index
	 * @param builder
	 */
	protected void addIndexDefinition(final Index index, final Db2SqlBuilder builder) {
		final AddTableObjectDetailFactory<Index, Db2SqlBuilder> indexOperation=this.getAddTableObjectDetailOperationFactory(index);
		if (indexOperation!=null) {
			builder.lineBreak().comma();
			indexOperation.addObjectDetail(index, null, builder);
		}
	}

}
