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

import java.util.Collection;
import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * Rowクラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractRowFactory<S extends AbstractSqlBuilder<?>>
		extends SimpleSqlFactory<DbCommonObject<?>, S> {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public List<SqlOperation> createSql(final Collection<DbCommonObject<?>> c) {
		return getOperationsInternal((Collection) c);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public List<SqlOperation> createSql(final DbCommonObject<?> obj) {
		if (obj instanceof Table) {
			return createSql(((Table)obj).getRows());
		} else if (obj instanceof RowCollection) {
			return getOperationsInternal((RowCollection) obj);
		} else if (obj instanceof Collection) {
			return getOperationsInternal((Collection<Row>) obj);
		} else if (obj instanceof Row) {
			final List<Row> batchRows=CommonUtils.list(1);
			final Row row=Row.class.cast(obj);
			batchRows.add(row);
			return getOperations(row.getTable(), batchRows);
		}
		throw new IllegalArgumentException("arg=" + obj);
	}

	protected List<SqlOperation> getOperationsInternal(final Collection<Row> rows) {
		final List<SqlOperation> result = CommonUtils.list();
		if (CommonUtils.isEmpty(rows)){
			return result;
		}
		final List<Row> batchRows=CommonUtils.list();
		Table table=null;
		int batchSize=1;
		for(final Row row:rows){
			if (table==null){
				table=row.getTable();
				batchSize=this.getOptions().getTableOptions().getDmlBatchSize().apply(table);
			}
			batchRows.add(row);
			if (batchRows.size()==batchSize){
				final List<SqlOperation> ops = getOperations(table, batchRows);
				result.addAll(ops);
				batchRows.clear();
			}
		}
		if (batchRows.size()>0){
			final List<SqlOperation> ops = getOperations(table, batchRows);
			result.addAll(ops);
			batchRows.clear();
		}
		return result;
	}
	


	protected List<SqlOperation> getOperations(final Table table, final Collection<Row> rows){
		final List<SqlOperation> result = CommonUtils.list();
		for(final Row row:rows){
			final List<SqlOperation> ops = getOperations(row);
			result.addAll(ops);
		}
		return result;
	}
	
	protected abstract List<SqlOperation> getOperations(final Row row);

	protected Table toTable(final DbCommonObject<?> obj) {
		if (obj instanceof Table) {
			return (Table) obj;
		} else if (obj instanceof RowCollection) {
			return ((RowCollection) obj).getParent();
		} else if (obj instanceof Row) {
			return ((Row) obj).getTable();
		}
		return null;
	}

	protected void addInsertIntoTable(final Table table, final Row row, final S builder) {
		builder.insert().into().table();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		builder.space()._add("(");
		final ColumnCollection columns=table.getColumns();
		final boolean[] first=new boolean[]{false};
		for (int i = 0; i < columns.size(); i++) {
			final Column column = columns.get(i);
			final String def=this.getValueDefinitionForInsert(row, column);
			builder.$if(def!=null, ()->{
				if (!isFormulaColumn(column)) {
					builder.lineBreak();
					builder.comma(!first[0]).space(2, !first[0]);
					builder.name(column);
					first[0]=false;
				}
			});
		}
		builder.lineBreak();
		builder._add(")");
		builder.lineBreak();
		builder.values();
	}

	/**
	 * ユニークカラムの検索条件を追加します
	 * 
	 * @param table
	 * @param row
	 * @param builder
	 */
	protected void addUniqueColumnsCondition(final Table table, final Row row,
			final AbstractSqlBuilder<?> builder) {
		builder.setQuateObjectName(this.getOptions().isQuateColumnName());
		List<Column> columns = table.getUniqueColumns();
		if (columns==null){
			columns=CommonUtils.list();
			columns.addAll(table.getColumns());
		}
		builder.appendIndent(1);
		final boolean[] first=new boolean[]{true};
		for (final Column column : columns) {
			final String def=this.getValueDefinitionForCondition(row, column);
			builder.$if(def!=null, ()->{
				builder.lineBreak();
				builder.and(!first[0]).name(column);
				if ("IS NULL".equals(def)){
					builder.space().is()._null();
				} else{
					builder.space().eq().space()._add(def);
				}
				first[0]=false;
			});
		}
		builder.appendIndent(-1);
		builder.setQuateObjectName(false);
	}
}
