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

import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Table;

/**
 * Postgresテーブル生成クラス
 * 
 * @author satoh
 * 
 */
public class Postgres82AlterTableFactory extends PostgresAlterTableFactory {
	@Override
	protected void addOtherDefinitions(Map<String, Difference<?>> allDiff
			, Table originalTable, Table table, List<SqlOperation> result){
		super.addOtherDefinitions(allDiff, originalTable, table, result);
	}

}
