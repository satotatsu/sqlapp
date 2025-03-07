/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.ExcludeConstraint;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FlexList;

/**
 * テーブル生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractCreateTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableFactory<S> {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		final List<SqlOperation> sqlList = list();
		final S builder = createSqlBuilder();
		addCreateObject(table, builder);
		this.addTableComment(table, builder);
		builder.lineBreak().brackets(()->{
			builder.indent(()->{
				for (int i = 0; i < table.getColumns().size(); i++) {
					final Column column = table.getColumns().get(i);
					builder.lineBreak();
					builder.comma(i > 0).space(2, i == 0);
					builder.name(column).space().definition(column, this.getOptions().getTableOptions().getWithColumnRemarks().test(column));
				}
				addIndexDefinitions(table, builder);
				addConstraintDefinitions(table, builder);
			});
			builder.lineBreak();
		});
		addOption(table, builder);
		addSql(sqlList, builder, SqlType.CREATE, table);
		addIndexDefinitions(table, sqlList);
		addOtherDefinitions(table, sqlList);
		return sqlList;
	}

	@Override
	protected List<Table> sort(final List<Table> c){
		return SchemaUtils.getNewSortedTableList(c, Table.TableOrder.CREATE.getComparator());
	}
	
	@Override
	protected List<DbObjectDifference> sortDbObjectDifference(
			final List<DbObjectDifference> list) {
		return sort(list, Table.TableOrder.CREATE.getComparator());
	}

	private List<DbObjectDifference> sort(
			final List<DbObjectDifference> list, final Comparator<Table> comparator) {
		final List<Table> tables = CommonUtils.list(list.size());
		for (final DbObjectDifference dbObjectDifference : list) {
			tables.add((Table) dbObjectDifference.getTarget());
		}
		Collections.sort(tables, comparator);
		final List<DbObjectDifference> result = new FlexList<DbObjectDifference>();
		for (int i = 0; i < tables.size(); i++) {
			final Table table = tables.get(i);
			for (final DbObjectDifference dbObjectDifference : list) {
				if (table == dbObjectDifference.getTarget()) {
					result.add(dbObjectDifference);
				}
			}
		}
		return result;
	}

	protected void addCreateObject(final Table obj, final S builder) {
		builder.create().table();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}

	protected void addIndexDefinitions(final Table table, final S builder){
	}
	
	protected void addConstraintDefinitions(final Table table, final S builder){
		addUniqueConstraintDefinitions(table, builder);
		addCheckConstraintDefinitions(table, builder);
		addForeignKeyConstraintDefinitions(table, builder);
		addExcludeConstraintDefinitions(table, builder);
	}

	protected void addOtherDefinitions(final Table table, final List<SqlOperation> result){
		
	}

	/**
	 * インデックス定義を追加します
	 * 
	 * @param table
	 * @param result
	 */
	protected void addIndexDefinitions(final Table table,final List<SqlOperation> result) {
		for (final Index index : table.getIndexes()) {
			addCreateIndexDefinition(table, index, result);
		}
	}

	protected void addCreateIndexDefinition(final Table table, final Index index, final List<SqlOperation> result) {
		final S builder = createSqlBuilder();
		addCreateIndexDefinition(index, builder);
		add(result, createOperation(builder.toString(), SqlType.CREATE, index));
	}

	/**
	 * 全Unique制約を追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addUniqueConstraintDefinitions(final Table table, final S builder) {
		for (final UniqueConstraint uniqueConstraint : table.getConstraints()
				.getUniqueConstraints()) {
			addConstraintDefinition(uniqueConstraint, builder);
		}
	}

	/**
	 * 制約を追加します
	 * 
	 * @param constraint
	 * @param builder
	 */
	protected void addConstraintDefinition(final Constraint obj, final S builder) {
		final AddTableObjectDetailFactory<Constraint, AbstractSqlBuilder<?>> sqlFactory=getAddTableObjectDetailOperationFactory(obj);
		if (sqlFactory!=null){
			builder.lineBreak().comma();
			if (obj.getParent()!=null){
				sqlFactory.addObjectDetail(obj, obj.getParent().getParent(), builder);
			} else{
				sqlFactory.addObjectDetail(obj, null, builder);
			}
		}
	}

	/**
	 * 全Exclude制約を追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addExcludeConstraintDefinitions(final Table table, final S builder) {
		for (final ExcludeConstraint excludeConstraint : table.getConstraints()
				.getExcludeConstraints()) {
			addConstraintDefinition(excludeConstraint, builder);
		}
	}

	/**
	 * 全Check制約を追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addCheckConstraintDefinitions(final Table table, final S builder) {
		for (final CheckConstraint checkConstraint : table.getConstraints()
				.getCheckConstraints()) {
			addConstraintDefinition(checkConstraint, builder);
		}
	}

	/**
	 * 全Foreign Key制約を追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addForeignKeyConstraintDefinitions(final Table table, final S builder) {
		for (final ForeignKeyConstraint constraint : table.getConstraints()
				.getForeignKeyConstraints()) {
			addConstraintDefinition(constraint, builder);
		}
	}

	/**
	 * オプションを追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addOption(final Table table, final S builder) {
	}

}
