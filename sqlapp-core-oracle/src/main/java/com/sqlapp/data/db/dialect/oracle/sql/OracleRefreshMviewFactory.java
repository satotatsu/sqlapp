/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractRefreshMviewFactory;
import com.sqlapp.data.schemas.Mview;

public class OracleRefreshMviewFactory extends AbstractRefreshMviewFactory<OracleSqlBuilder> {
	@Override
	protected void addRefreshObject(final Mview obj, final OracleSqlBuilder builder) {
		if (obj.getName() == null || obj.getName().isBlank()) {
			throw new IllegalArgumentException("Materialized view name is required for refresh");
		}
		if ("NEVER".equalsIgnoreCase(obj.getSpecifics().get("REFRESH_METHOD"))
				|| "NEVER".equalsIgnoreCase(obj.getSpecifics().get("REFRESH_MODE"))) {
			throw new IllegalArgumentException("Cannot refresh a NEVER REFRESH materialized view: " + obj.getName());
		}
		OracleSqlBuilder name = createSqlBuilder();
		name.name(obj, getOptions().isDecorateSchemaName());
		builder._add("BEGIN DBMS_MVIEW.REFRESH(list => ").sqlChar(name.toString())._add("); END;");
	}

}
