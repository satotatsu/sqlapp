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
		final Table source=obj.clone();
		source.setName(this.getOptions().getTableOptions().getTempTableName().apply(source));
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
		builder.on()._add("(");
		final Set<String> pkCols=CommonUtils.linkedSet();
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
		builder._add(")");
		builder.lineBreak();
		builder.when().matched();
		builder.indent(()->{
			builder.lineBreak();
			builder.then().update();
			builder.indent(()->{
				int i=0;
				for(final Column column:obj.getColumns()){
					if (!pkCols.contains(column.getName())) {
						builder.lineBreak().set(i==0).comma(i>0);
						builder.name(targetTableAlias+".", column).eq().name(sourceTableAlias+".", column);
						i++;
					}
				}
			});
		});
		builder.lineBreak();
		builder.when().not().matched().by().space().target();
		final List<Column> insertColumns=CommonUtils.list();
		builder.indent(()->{
			builder.lineBreak();
			builder.then().insert();
			builder.lineBreak();
			builder._add("(");
			builder.indent(()->{
				int i=0;
				for(final Column column:obj.getColumns()){
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
			builder._add(")");
			builder.lineBreak();
			builder.values();
			builder.lineBreak();
			builder._add("(");
			builder.indent(()->{
				int i=0;
				for(final Column column:insertColumns){
					builder.lineBreak().comma(i>0);
					builder.name(sourceTableAlias+".", column);
					i++;
				}
			});
			builder.lineBreak();
			builder._add(")");
		});
		builder.lineBreak();
		builder.when().not().matched().by().source();
		builder.indent(()->{
			builder.lineBreak();
			builder.then().delete();
		});
	}

}
