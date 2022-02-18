/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.sqlserver.sql;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateAssemblyFactory;
import com.sqlapp.data.schemas.Assembly;
import com.sqlapp.data.schemas.AssemblyFile;
import com.sqlapp.util.BinaryUtils;
import com.sqlapp.util.CommonUtils;

public class SqlServer2005CreateAssemblyFactory extends
		AbstractCreateAssemblyFactory<SqlServerSqlBuilder> {

	@Override
	protected void addCreateObject(final Assembly obj,
			SqlServerSqlBuilder builder) {
		builder.create().space();
		builder._add(obj.getClass().getSimpleName().toUpperCase());
		builder.name(obj);
		builder.lineBreak();
		builder.from().space();
		for (int i = 0; i < obj.getAssemblyFiles().size(); i++) {
			AssemblyFile asf = obj.getAssemblyFiles().get(i);
			builder.lineBreak(i > 0)._add(",", i > 0);
			if (CommonUtils.isEmpty(asf.getContent())) {
				builder._add(asf.getName());
			} else {
				builder._add("0x")._add(
						BinaryUtils.toHexString(asf.getContent()));
			}
		}
		if (obj.getPermissionSet() != null) {
			builder.lineBreak();
			builder.with().permissionSet().space().eq().space()
					._add(obj.getPermissionSet());
		}
	}
}
