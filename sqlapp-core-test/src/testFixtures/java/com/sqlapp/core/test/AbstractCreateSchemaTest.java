/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-test.
 *
 * sqlapp-core-test is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-test is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-test.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.core.test;

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
		createOperationFactory = createSqlFactoryRegistry().getSqlFactory(new Schema("scm"), SqlType.CREATE);
	}

	protected String getSqlText(String filename) throws Exception {
		SchemaCollection schemas = SchemaUtils.readXml(getResourceAsInputStream(filename));
		List<SqlOperation> operations = createOperationFactory.createSql(schemas);
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < operations.size(); i++) {
			SqlOperation operation = operations.get(i);
			builder.append(operation.getSqlText());
			builder.append(";\n\n");
		}
		return builder.substring(0, builder.length() - 1).toString();
	}

}
