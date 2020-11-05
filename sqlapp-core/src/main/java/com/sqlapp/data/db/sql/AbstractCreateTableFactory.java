/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
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
		List<SqlOperation> sqlList = list();
		S builder = createSqlBuilder();
		addCreateObject(table, builder);
		builder.lineBreak()._add("(");
		builder.appendIndent(1);
		for (int i = 0; i < table.getColumns().size(); i++) {
			Column column = table.getColumns().get(i);
			builder.lineBreak();
			builder.comma(i > 0).space(2, i == 0);
			builder.name(column).space().definition(column);
		}
		addConstraintDefinitions(table, builder);
		builder.appendIndent(-1);
		builder.lineBreak()._add(")");
		addOption(table, builder);
		addSql(sqlList, builder, SqlType.CREATE, table);
		addIndexDefinitions(table, sqlList);
		addOtherDefinitions(table, sqlList);
		return sqlList;
	}

	@Override
	protected List<Table> sort(List<Table> c){
		return SchemaUtils.getNewSortedTableList(c, Table.TableOrder.CREATE.getComparator());
	}
	
	@Override
	protected List<DbObjectDifference> sortDbObjectDifference(
			List<DbObjectDifference> list) {
		return sort(list, Table.TableOrder.CREATE.getComparator());
	}

	private List<DbObjectDifference> sort(
			List<DbObjectDifference> list, Comparator<Table> comparator) {
		List<Table> tables = CommonUtils.list(list.size());
		for (DbObjectDifference dbObjectDifference : list) {
			tables.add((Table) dbObjectDifference.getTarget());
		}
		Collections.sort(tables, comparator);
		List<DbObjectDifference> result = new FlexList<DbObjectDifference>();
		for (int i = 0; i < tables.size(); i++) {
			Table table = tables.get(i);
			for (DbObjectDifference dbObjectDifference : list) {
				if (table == dbObjectDifference.getTarget()) {
					result.add(dbObjectDifference);
				}
			}
		}
		return result;
	}

	protected void addCreateObject(final Table obj, S builder) {
		builder.create().table();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}

	protected void addConstraintDefinitions(Table table, S builder){
		addUniqueConstraintDefinitions(table, builder);
		addCheckConstraintDefinitions(table, builder);
		addForeignKeyConstraintDefinitions(table, builder);
		addExcludeConstraintDefinitions(table, builder);
	}
	
	protected void addOtherDefinitions(Table table, List<SqlOperation> result){
		
	}

	/**
	 * インデックス定義を追加します
	 * 
	 * @param table
	 * @param result
	 */
	protected void addIndexDefinitions(Table table,List<SqlOperation> result) {
		for (Index index : table.getIndexes()) {
			addCreateIndexDefinition(table, index, result);
		}
	}

	protected void addCreateIndexDefinition(Table table, Index index, List<SqlOperation> result) {
		S builder = createSqlBuilder();
		addCreateIndexDefinition(index, builder);
		add(result, createOperation(builder.toString(), SqlType.CREATE, index));
	}

	/**
	 * 全Unique制約を追加します
	 * 
	 * @param table
	 * @param builder
	 */
	protected void addUniqueConstraintDefinitions(final Table table, S builder) {
		for (UniqueConstraint uniqueConstraint : table.getConstraints()
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
	protected void addConstraintDefinition(Constraint obj, S builder) {
		AddTableObjectDetailFactory<Constraint, AbstractSqlBuilder<?>> sqlFactory=getAddTableObjectDetailOperationFactory(obj);
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
	protected void addExcludeConstraintDefinitions(final Table table, S builder) {
		for (ExcludeConstraint excludeConstraint : table.getConstraints()
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
	protected void addCheckConstraintDefinitions(final Table table, S builder) {
		for (CheckConstraint checkConstraint : table.getConstraints()
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
	protected void addForeignKeyConstraintDefinitions(final Table table, S builder) {
		for (ForeignKeyConstraint constraint : table.getConstraints()
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
	protected void addOption(final Table table, S builder) {
	}

}
