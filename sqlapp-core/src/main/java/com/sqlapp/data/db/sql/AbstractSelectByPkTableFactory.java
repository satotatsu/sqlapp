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

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * SELECT BY PK
 * 
 * @author satoh
 * 
 */
public abstract class AbstractSelectByPkTableFactory<S extends AbstractSqlBuilder<?>>
		extends AbstractTableFactory<S> {

	@Override
	public List<SqlOperation> createSql(Table obj) {
		S builder = createSqlBuilder();
		addSelectFtomTable(obj, builder);
		builder.where()._true();
		addUniqueColumnsCondition(obj, builder);
		List<SqlOperation> sqlList = list();
		addSql(sqlList, builder, SqlType.SELECT_BY_PK, obj);
		return sqlList;
	}

	protected void addSelectFtomTable(Table obj, S builder) {
		builder.select()._add(" *").from();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}

	protected void addColumnIfComment(Column column, S builder) {
		builder.lineBreak();
		builder._add("/*if isNotEmty(+" + column.getName() + ") */");
		builder.appendIndent(1);
		builder.lineBreak();
		builder.name(column).eq();
		builder._add("/*" + column.getName() + "*/");
		builder._add(getDefaultValueLiteral(column));
		builder.appendIndent(-1);
		builder._add("/*end*/");
	}

	protected String getDefaultValueLiteral(Column column) {
		DbDataType<?> dbDataType = this.getDialect().getDbDataType(column);
		return dbDataType.getDefaultValueLiteral();
	}
}
