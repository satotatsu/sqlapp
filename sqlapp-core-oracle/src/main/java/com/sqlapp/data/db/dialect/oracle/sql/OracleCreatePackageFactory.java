/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-oracle.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreatePackageFactory;
import com.sqlapp.data.schemas.Package;

public class OracleCreatePackageFactory extends
		AbstractCreatePackageFactory<OracleSqlBuilder> {

	@Override
	protected void addCreateObject(final Package obj, OracleSqlBuilder builder) {
		if (!isEmpty(obj.getDefinition())) {
			builder.create().or().replace();
			builder.space();
			builder._add(obj.getDefinition());
		} else {
			builder.create().or().replace()._package();
			builder.name(obj, this.getOptions().isDecorateSchemaName());
			builder.lineBreak()._add(obj.getStatement());
		}
	}
}
