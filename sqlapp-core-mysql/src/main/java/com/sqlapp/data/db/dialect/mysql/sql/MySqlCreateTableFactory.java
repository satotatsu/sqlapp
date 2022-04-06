/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
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

public class MySqlCreateTableFactory extends
		AbstractCreateTableFactory<MySqlSqlBuilder> {

	@Override
	protected void addCreateObject(final Table table, MySqlSqlBuilder builder) {
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
	protected void addOption(final Table table, MySqlSqlBuilder builder) {
		addTableOption(table, builder);
		addPartitionByDefinition(table, builder);
	}

	protected void addTableOption(final Table table, MySqlSqlBuilder builder) {
		addEnginDefinition(table, builder);
		addCollateDefinition(table, builder);
		addRowformatDefinition(table, builder);
		addAutoIncrementDefinition(table, builder);
		addRemarkDefinition(table, builder);
	}

	@Override
	protected void addIndexDefinitions(Table table,List<SqlOperation> result) {
	}
	
	/**
	 * Engine定義を追加します
	 * 
	 * @param table
	 * @param sqlBuilder
	 */
	protected void addEnginDefinition(final Table table,
			MySqlSqlBuilder builder) {
		if (!CommonUtils.isEmpty(table.getSpecifics().get("ENGINE"))) {
			builder.engine().eq()._add(table.getSpecifics().get("ENGINE"));
		}
	}

	/**
	 * RowFormat定義を追加します
	 * 
	 * @param table
	 * @param sqlBuilder
	 */
	protected void addRowformatDefinition(final Table table,
			MySqlSqlBuilder builder) {
		String rowFormat = (String) table.getSpecifics().get("ROW_FORMAT");
		if (!CommonUtils.isEmpty(rowFormat)) {
			builder.rowFormat().eq()._add(rowFormat);
		} else {
			if (table.isCompression()) {
				builder.rowFormat().eq().compressed();
			}
		}
	}

	/**
	 * Collate定義を追加します
	 * 
	 * @param table
	 * @param sqlBuilder
	 */
	protected void addCollateDefinition(final Table table,
			MySqlSqlBuilder builder) {
		if (!CommonUtils.isEmpty(table.getCollation())) {
			builder.collate().eq()._add(table.getCollation());
		}
	}

	/**
	 * COMMENT定義を追加します
	 * 
	 * @param table
	 * @param sqlBuilder
	 */
	protected void addRemarkDefinition(final Table table,
			MySqlSqlBuilder builder) {
		if (!CommonUtils.isEmpty(table.getRemarks())) {
			builder.comment()
					.eq()
					.sqlChar(table.getRemarks());
		}
	}

	/**
	 * AUTO_INCREMENTの初期値を設定DDLを取得します
	 * 
	 * @param colDiff
	 * @param sqlBuilder
	 */
	protected void addAutoIncrementDefinition(final Table table,
			MySqlSqlBuilder builder) {
		for (Column column : table.getColumns()) {
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
	 * @param sqlBuilder
	 */
	protected void addPartitionByDefinition(final Table table,
			MySqlSqlBuilder builder) {
		if (table.getPartitioning() != null) {
			AddObjectDetail<Partitioning, AbstractSqlBuilder<?>> addObjectDetail=getAddObjectDetail(table.getPartitioning(), SqlType.CREATE);
			addObjectDetail.addObjectDetail(table.getPartitioning(), builder);
		}
	}

	@Override
	protected void addConstraintDefinitions(Table table, MySqlSqlBuilder builder){
		super.addConstraintDefinitions(table, builder);
	}
	
	
	/**
	 * インデックスを追加します
	 * 
	 * @param index
	 * @param builder
	 */
	protected void addIndexDefinitions(final Table table, MySqlSqlBuilder builder) {
		for(Index index:table.getIndexes()){
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
	protected void addIndexDefinition(final Index index, MySqlSqlBuilder builder) {
		AddTableObjectDetailFactory<Index, MySqlSqlBuilder> indexOperation=this.getAddTableObjectDetailOperationFactory(index);
		if (indexOperation!=null) {
			builder.lineBreak().comma();
			indexOperation.addObjectDetail(index, null, builder);
		}
	}

}
