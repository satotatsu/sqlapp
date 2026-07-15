/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractTruncateTemporaryTableFactory;
import com.sqlapp.data.schemas.Table;

/**
 * CREATE TEMPORARY TABLE
 * 
 * @author satoh
 * 
 */
public class OracleTruncateTemporaryTableFactory extends AbstractTruncateTemporaryTableFactory<OracleSqlBuilder> {

	@Override
	protected void addTruncateTable(final Table obj, final OracleSqlBuilder builder) {
		// 通常のテーブルだとTRUNCATEするとCOMMITされるが、一時テーブルの時だけCOMMITされないらしいのでそのまま
		super.addTruncateTable(obj, builder);
	}
}
