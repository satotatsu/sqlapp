/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-firebird.
 *
 * sqlapp-core-firebird is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-firebird is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-firebird.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.firebird.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreatePackageBodyFactory;
import com.sqlapp.data.schemas.PackageBody;

public class Firebird30CreatePackageBodyFactory extends AbstractCreatePackageBodyFactory<FirebirdSqlBuilder> {

	@Override
	protected void addCreateObject(final PackageBody obj, FirebirdSqlBuilder builder) {
		if (!isEmpty(obj.getDefinition())) {
			builder.create().or().alter();
			builder.space()._add(obj.getDefinition());
		} else {
			builder.create().or().alter()._package().body();
			builder.name(obj, this.getOptions().isDecorateSchemaName());
			builder.lineBreak().as();
			builder.lineBreak()._add(obj.getStatement());
		}
	}
}
