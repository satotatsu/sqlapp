/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sqlserver.sql;


import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.DbObjectDifference;

/**
 * SQL Server 2016+ temporal table alteration
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2016AlterTableFactory extends SqlServer2014AlterTableFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Table original = difference.getOriginal(Table.class);
		Table target = difference.getTarget(Table.class);
		if (original != null && target != null && (!original.getTemporalPeriods().isEmpty()
				|| !target.getTemporalPeriods().isEmpty() || SqlServerTemporalSupport.enabled(original)
				|| SqlServerTemporalSupport.enabled(target))) {
			if (original.equals(target)) return java.util.Collections.emptyList();
			throw new IllegalArgumentException("Automatic ALTER of temporal tables is unsupported; disable SYSTEM_VERSIONING, "
					+ "migrate both current and history tables explicitly, then re-enable it: " + target.getName());
		}
		return super.createDiffSql(difference);
	}


}
