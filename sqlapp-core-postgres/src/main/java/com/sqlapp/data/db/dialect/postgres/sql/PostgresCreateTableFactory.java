/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

/**
 * Postgresテーブル生成クラス
 * 
 * @author satoh
 * 
 */
public class PostgresCreateTableFactory extends
		AbstractCreateTableFactory<PostgresSqlBuilder> {

	@Override
	protected void addCreateObject(final Table obj, final PostgresSqlBuilder builder) {
		builder.create().unlogged(obj.isUnlogged()).table();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addOtherDefinitions(Table table, List<SqlOperation> result){
		if (table.getRemarks()!=null){
			PostgresSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().table().space().name(table, this.getOptions().isDecorateSchemaName()).is().space().sqlChar(table.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, table);
		}
		table.getColumns().stream().filter(c->c.getRemarks()!=null).forEach(c->{
			PostgresSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().column().space().columnName(c, true, this.getOptions().isDecorateSchemaName()).is().space().sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
		table.getIndexes().stream().filter(c->c.getRemarks()!=null).forEach(c->{
			PostgresSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().index().space().name(c, this.getOptions().isDecorateSchemaName()).is().space().sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
		table.getConstraints().stream().filter(c->c.getRemarks()!=null).forEach(c->{
			PostgresSqlBuilder builder=this.createSqlBuilder();
			builder.comment().on().constraint().space().name(c, this.getOptions().isDecorateSchemaName()).on().name(table, this.getOptions().isDecorateSchemaName()).is().space().sqlChar(c.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, c);
		});
	}
	
}
