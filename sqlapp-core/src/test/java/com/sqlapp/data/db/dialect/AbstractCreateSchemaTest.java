/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect;

import java.util.List;

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaCollection;
import com.sqlapp.data.schemas.SchemaUtils;

public abstract class AbstractCreateSchemaTest extends AbstractSqlFactoryTest {
	SqlFactory<Schema> createOperationFactory;
	SqlFactory<Schema> dropOperationFactory;

	protected void before() {
		createOperationFactory = sqlFactoryRegistry.getSqlFactory(
				new Schema("scm"), SqlType.CREATE);
	}

	protected String getSqlText(final String filename) throws Exception {
		final SchemaCollection schemas=SchemaUtils.readXml(this.getClass(), filename);
		final List<SqlOperation> operations=createOperationFactory.createSql(schemas);
		final StringBuilder builder=new StringBuilder();
		for(int i=0;i<operations.size();i++){
			final SqlOperation operation=operations.get(i);
			builder.append(operation.getSqlText());
			builder.append(";\n\n");
		}
		return builder.substring(0, builder.length()-1).toString();
	}
	

	@Override
	protected abstract String productName();

	@Override
	protected abstract int getMajorVersion();

	@Override
	protected abstract int getMinorVersion();

}
