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

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * INSERT生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractInsertTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableFactory<S> {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		final List<SqlOperation> sqlList = list();
		final S builder = createSqlBuilder();
		final List<Column> list=addInsertIntoTable(table, builder);
		builder.lineBreak();
		builder.brackets(()->{
			builder.indent(()->{
				int i=0;
				for (final Column column:list) {
					final String def=this.getValueDefinitionForInsert(column);
					builder.lineBreak();
					builder.comma(i > 0).space(2, i == 0);
					builder._add(def);
					i++;
				}
			});
			builder.lineBreak();
		});
		addSql(sqlList, builder, SqlType.INSERT, table);
		return sqlList;
	}

	protected List<Column> addInsertIntoTable(final Table obj, final S builder) {
		final List<Column> list=CommonUtils.list();
		builder.insert().into().space();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		builder.space().lineBreak();
		builder.brackets(()->{
			builder.indent(()->{
				int i=0;
				for(final Column column:obj.getColumns()){
					if (!isInsertable(column)) {
						continue;
					}
					if (this.isFormulaColumn(column)) {
						continue;
					}
					if (this.isAutoIncrementColumn(column)){
						final Dialect dialect = builder.getDialect();
						if (!CommonUtils.isEmpty(dialect.getIdentityInsertString())) {
							builder.lineBreak();
							builder.comma(i > 0).space(2, i == 0);
							builder.name(column);
							final String comment=this.getOptions().getTableOptions().getInsertColumnComment().apply(column);
							if (!CommonUtils.isEmpty(comment)&&!CommonUtils.eqIgnoreCase(comment, column.getName())) {
								builder.space().addComment(comment);
							}
							list.add(column);
							i++;
						}
					} else {
						builder.lineBreak();
						builder.comma(i > 0).space(2, i == 0);
						builder.name(column);
						final String comment=this.getOptions().getTableOptions().getInsertColumnComment().apply(column);
						if (!CommonUtils.isEmpty(comment)&&!CommonUtils.eqIgnoreCase(comment, column.getName())) {
							builder.addComment(comment);
						}
						list.add(column);
						i++;
					}
				}
			});
			builder.lineBreak();
		});
		builder.lineBreak().values();
		return list;
	}
}
