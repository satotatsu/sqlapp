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

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractTableCommandFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

/**
 * PostgresテーブルVacuum生成
 * 
 * @author satoh
 * 
 */
public class PostgresDefragFullTableFactory extends AbstractTableCommandFactory<PostgresSqlBuilder> {

	@Override
	protected void addTableCommand(Table obj, PostgresSqlBuilder builder) {
		builder.vacuum().full().name(obj, this.getOptions().isDecorateSchemaName());
		this.addTableComment(obj, builder);
	}

	@Override
	protected SqlType getSqlType() {
		return SqlType.DEFRAG_FULL;
	}
	
}
