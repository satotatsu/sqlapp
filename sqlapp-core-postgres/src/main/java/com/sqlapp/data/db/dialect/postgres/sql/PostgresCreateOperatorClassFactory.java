/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateOperatorClassFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.FunctionFamily;
import com.sqlapp.data.schemas.OperatorClass;
import com.sqlapp.data.schemas.OperatorFamily;

/**
 * CREATE OPERATOR CLASS
 * 
 * 
 */
public class PostgresCreateOperatorClassFactory extends
		AbstractCreateOperatorClassFactory<PostgresSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(OperatorClass obj) {
		List<SqlOperation> sqlList = list();
		PostgresSqlBuilder builder = createSqlBuilder();
		addCreateObject(obj, builder);
		addSql(sqlList, builder, SqlType.CREATE, obj);
		return sqlList;
	}

	protected void addCreateObject(final OperatorClass obj,
			PostgresSqlBuilder builder) {
		builder.create().operator()._class();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
		if (obj.isDefault()) {
			builder._default();
		}
		builder._for().type().space()._add(obj.getDataTypeName()).using()
				._add(obj.getIndexType()).as();
		builder.appendIndent(+1);
		for (OperatorFamily operatorFamily : obj.getOperatorFamilies()) {
			builder.lineBreak();
			builder.operator().space()
					._add(operatorFamily.getStrategyNumber()).space()
					._add(operatorFamily.getOperatorName());
		}
		for (FunctionFamily functionFamily : obj.getFunctionFamilies()) {
			builder.lineBreak();
			builder.operator().space()
					._add(functionFamily.getSupportNumber()).space()
					._add(functionFamily.getFunctionName());
		}
		builder.appendIndent(-1);
	}

}
