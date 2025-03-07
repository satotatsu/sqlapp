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

import java.util.List;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * UPDATE生成クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractInsertSelectTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableFactory<S> {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		final List<SqlOperation> sqlList = list();
		final S builder = createSqlBuilder();
		addInsertSelectTable(table, builder);
		addSql(sqlList, builder, SqlType.INSERT_SELECT_BY_PK, table);
		return sqlList;
	}

	protected void addInsertSelectTable(final Table obj,
			final S builder) {
		builder.insert().into().table();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		this.addTableComment(obj, builder);
		final int[] i=new int[1];
		builder.lineBreak();
		builder.brackets(()->{
			builder.indent(()->{
				for(final Column column:obj.getColumns()){
					if (!this.isFormulaColumn(column)) {
						builder.lineBreak().comma(i[0]>0).space(2, i[0] == 0);
						builder.name(column);
						i[0]++;
					}
				}
			});
			builder.lineBreak();
		});
		builder.lineBreak();
		builder.values();
		builder.lineBreak();
		builder.select();
		builder.space();
		builder.names(c->!isFormulaColumn(c), obj.getColumns());
		builder.lineBreak();
		builder.from();
		builder.name(obj);
		builder.where().lineBreak();
		builder.not().exists().space()._add("(");
		builder.appendIndent(+1);
		builder.lineBreak();
		builder.select().space()._add("1");
		builder.lineBreak();
		builder.from().name(obj, this.getOptions().isDecorateSchemaName());
		builder.lineBreak().where()._true();
		builder.appendIndent(-1);
		this.addUniqueColumnsCondition(obj, builder);
		builder.lineBreak()._add(")");
	}

}
