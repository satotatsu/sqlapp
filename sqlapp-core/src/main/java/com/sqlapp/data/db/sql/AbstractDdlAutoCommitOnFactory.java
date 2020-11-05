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

import java.util.List;

import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * DDL AUTO COMMIT ON
 * 
 * @author tatsuo satoh
 * 
 */
public abstract class AbstractDdlAutoCommitOnFactory<S extends AbstractSqlBuilder<?>> extends AbstractSqlFactory<DbCommonObject<?>,S> {

	@Override
	public List<SqlOperation> createSql(DbCommonObject<?> obj) {
		return createSql();
	}

	@Override
	public List<SqlOperation> createSql() {
		List<SqlOperation> sqlList=CommonUtils.list();
		S builder=this.createSqlBuilder(this.getDialect());
		createSqlInternal(builder);
		addSql(sqlList, builder, SqlType.DDL_AUTOCOMMIT_ON, (DbCommonObject<?>)null);
		return sqlList;
	}
	
	protected void createSqlInternal(S builder){
		builder.set().transaction().autocommit().on();
	}

}
