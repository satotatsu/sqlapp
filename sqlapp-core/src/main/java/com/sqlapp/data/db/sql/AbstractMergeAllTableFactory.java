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

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * MERGE TABLE 生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractMergeAllTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableFactory<S> {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		final List<SqlOperation> sqlList = list();
		final S builder = createSqlBuilder();
		addMergeTable(table, builder);
		addSql(sqlList, builder, SqlType.MERGE_ALL, table);
		return sqlList;
	}

	protected void addMergeTable(final Table obj, final S builder) {
		final String sourceTableName=this.getOptions().getTableOptions().getTempTableName().apply(obj);
		Table source=obj.getParent()!=null?obj.getParent().get(sourceTableName):null;
		if (source==null) {
			source=obj.clone();
			source.setName(sourceTableName);
		}
		final String targetTableAlias="_target_";
		final String sourceTableAlias="_source_";
		builder.merge().space();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		builder.as().space()._add(targetTableAlias);
		builder.lineBreak();
		builder.using().name(source, this.getOptions().isDecorateSchemaName());
		builder.as().space()._add(sourceTableAlias);
		builder.lineBreak();
		final UniqueConstraint uk=obj.getPrimaryKeyConstraint();
		final Set<String> pkCols=CommonUtils.linkedSet();
		builder.on().brackets(()->{
			builder.indent(()->{
				int i=0;
				for(final ReferenceColumn rc:uk.getColumns()) {
					builder.lineBreak();
					builder.and(i>0).name(targetTableAlias+".",rc);
					builder.eq();
					builder.name(sourceTableAlias+".",rc);
					pkCols.add(rc.getName());
					i++;
				}
			});
			builder.lineBreak();
		});
		addMergeTableWhenMatched(obj, targetTableAlias,
				sourceTableAlias, pkCols, builder);
		addMergeTableWhenNotMatched(obj, targetTableAlias,
				sourceTableAlias, pkCols, builder);
		addMergeTableWhenNotMatchedBySource(obj, targetTableAlias,
				sourceTableAlias, pkCols, builder);
		addMergeTableAfter(obj, builder);
	}

	protected void addMergeTableAfter(final Table obj, final S builder) {
		
	}

	protected void addMergeTableWhenMatched(final Table obj, final String targetTableAlias,
			final String sourceTableAlias, final Set<String> pkCols, final S builder) {
		builder.lineBreak();
		addWhenMatched(obj, targetTableAlias,sourceTableAlias, builder);
		builder.indent(()->{
			builder.lineBreak();
			builder.then().update();
			builder.indent(()->{
				int i=0;
				for(final Column column:obj.getColumns()){
					if (!isUpdateable(column)) {
						continue;
					}
					if (!pkCols.contains(column.getName())) {
						builder.lineBreak().set(i==0).comma(i>0);
						builder.name(targetTableAlias+".", column).eq();
						final String value=this.getOptions().getTableOptions().getUpdateTableColumnValue().apply(column);
						if (value!=null && !Objects.equals(value, column.getName())) {
							builder._add(value);
						} else {
							builder.name(sourceTableAlias+".", column);
						}
						i++;
					}
				}
				addMergeTableWhenMatchedWhere(obj, targetTableAlias,
						sourceTableAlias, pkCols, builder);
			});
		});
		builder.lineBreak();
	}

	protected void addWhenMatched(final Table obj, final String targetTableAlias,
			final String sourceTableAlias, final S builder) {
		builder.when().matched();
	}

	protected void addMergeTableWhenMatchedWhere(final Table obj, final String targetTableAlias,
			final String sourceTableAlias, final Set<String> pkCols, final S builder) {
	}

	protected void addMergeTableWhenNotMatched(final Table obj, final String targetTableAlias,
			final String sourceTableAlias, final Set<String> pkCols, final S builder) {
		addWhenNotMatched(obj, targetTableAlias,sourceTableAlias, builder);
		final List<Column> insertColumns=CommonUtils.list();
		builder.indent(()->{
			builder.lineBreak();
			builder.then().insert();
			builder.lineBreak();
			builder.brackets(()->{
				builder.indent(()->{
					int i=0;
					for(final Column column:obj.getColumns()){
						if (!isInsertable(column)) {
							continue;
						}
						if (column.isIdentity()) {
							if (!CommonUtils.isEmpty(getDialect().getIdentityInsertString())) {
								insertColumns.add(column);
								builder.lineBreak().comma(i>0).name(column);
								i++;
							}
						} else {
							if (!this.isFormulaColumn(column)) {
								insertColumns.add(column);
								builder.lineBreak().comma(i>0).name(column);
								i++;
							}
						}
					}
				});
				builder.lineBreak();
			});
			builder.lineBreak();
			builder.values();
			builder.lineBreak();
			builder.brackets(()->{
				builder.indent(()->{
					int i=0;
					for(final Column column:insertColumns){
						builder.lineBreak().comma(i>0);
						final String value=this.getOptions().getTableOptions().getInsertTableColumnValue().apply(column);
						if (column.getDefaultValue()!=null) {
							builder.coalesce(()->{
								if (value!=null && !Objects.equals(value, column.getName())) {
									builder._add(value);
								} else {
									builder.name(sourceTableAlias+".", column);
								}
								builder.comma()._add(column.getDefaultValue());
							});
						} else {
							if (value!=null && !Objects.equals(value, column.getName())) {
								builder._add(value);
							} else {
								builder.name(sourceTableAlias+".", column);
							}
						}
						i++;
					}
				});
				builder.lineBreak();
			});
			addMergeTableWhenNotMatchedWhere(obj, targetTableAlias,
					sourceTableAlias, pkCols, builder);
		});
	}

	protected void addWhenNotMatched(final Table obj, final String targetTableAlias,
			final String sourceTableAlias, final S builder) {
		builder.when().not().matched();
	}
	
	protected void addMergeTableWhenNotMatchedWhere(final Table obj, final String targetTableAlias,
			final String sourceTableAlias, final Set<String> pkCols, final S builder) {
	}

	
	protected void addMergeTableWhenNotMatchedBySource(final Table obj, final String targetTableAlias,
			final String sourceTableAlias, final Set<String> pkCols, final S builder) {
	}

}
