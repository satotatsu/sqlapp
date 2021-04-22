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

import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * SELECT TABLE
 * 
 * @author satoh
 * 
 */
public abstract class AbstractSelectTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableFactory<S> {

	@Override
	public List<SqlOperation> createSql(final Table obj) {
		final S builder = createSqlBuilder();
		addSelectFromTable(obj, builder);
		builder.lineBreak();
		builder.where()._true();
		super.addConditionColumns(obj, builder);
		addOrderBy(obj, builder);
		addOffsetRowsOnly(obj, builder);
		final List<SqlOperation> sqlList = list();
		addSql(sqlList, builder, SqlType.SELECT, obj);
		return sqlList;
	}

	/**
	 * SELECT * FROM tableまでを追加します。
	 * @param obj
	 * @param builder
	 */
	protected void addSelectFromTable(final Table obj, final S builder) {
		builder.select().space()._add(toIfExpression("!"+ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME));
		addSelectAllColumns(obj, builder);
		builder._add("--else count(*)");
		builder.lineBreak();
		builder._add(this.getEndIfExpression());
		builder.lineBreak();
		builder.from().space().name(obj, this.getOptions().isDecorateSchemaName());
	}

	protected void addOrderBy(final Table obj, final S builder) {
		builder.lineBreak();
		builder._add(toIfExpression("!"+ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME+" && "+toIsNotEmptyExpression(ParameterDefinition.ORDER_BY_KEY_PARANETER_NAME)));
		builder.lineBreak();
		final List<Column> columns = obj.getUniqueColumns();
		builder.orderBy().space();
		builder.setAppendAutoSpace(false);
		builder._add("/*$"+ParameterDefinition.ORDER_BY_KEY_PARANETER_NAME+";sqlKeywordCheck=true*/");
		if (!CommonUtils.isEmpty(columns)) {
			builder.names(columns.toArray(new Column[0]));
		} else{
			builder.names(obj.getColumns().get(0));
		}
		builder.setAppendAutoSpace(true);
		builder.lineBreak();
		builder._add(this.getEndIfExpression());
	}

	protected void addOffsetRowsOnly(final Table obj, final S builder) {
		if(this.getDialect().supportsStandardOffsetFetchRows()){
			builder.lineBreak()._add(toIfIsNotEmptyExpression(""+ParameterDefinition.OFFSET_KEY_PARANETER_NAME)).lineBreak();
			builder.offset().space()._add("/*"+ParameterDefinition.OFFSET_KEY_PARANETER_NAME+"*/1").space().rows();
			builder.lineBreak();
			builder._add(this.getEndIfExpression());
			//
			builder.lineBreak()._add(toIfIsNotEmptyExpression(ParameterDefinition.ROW_KEY_PARANETER_NAME)).lineBreak();
			builder.fetch().first().space()._add("/*"+ParameterDefinition.ROW_KEY_PARANETER_NAME+"*/1").space().rows().only();
			builder.lineBreak();
			builder._add(this.getEndIfExpression());
		}
	}

}
