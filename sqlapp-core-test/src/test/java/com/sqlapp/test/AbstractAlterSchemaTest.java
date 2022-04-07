/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.test;

import java.util.List;

import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.util.FileUtils;

public abstract class AbstractAlterSchemaTest extends AbstractSqlFactoryTest {
	protected SqlFactory<Schema> createOperationFactory;
	protected SqlFactory<Schema> dropOperationFactory;

	protected void before() {
		createOperationFactory = sqlFactoryRegistry.getSqlFactory(
				new Schema("scm"), SqlType.ALTER);
	}

	protected String getAlterSqlText(final String filename1, final String filename2) throws Exception {
		final Catalog catalog1=new Catalog();
		catalog1.getSchemas().loadXml(FileUtils.getInputStream(this.getClass(), filename1));
		final Catalog catalog2=new Catalog();
		catalog2.getSchemas().loadXml(FileUtils.getInputStream(this.getClass(), filename2));
		final DbObjectDifference dbDiff=catalog1.getSchemas().get(0).diff(catalog2.getSchemas().get(0));
		final List<SqlOperation> operations=createOperationFactory.createDiffSql(dbDiff);
		final StringBuilder builder=new StringBuilder();
		for(int i=0;i<operations.size();i++){
			final SqlOperation operation=operations.get(i);
			builder.append(operation.getSqlText());
			builder.append(";\n");
		}
		return builder.substring(0, builder.length()-1).toString();
	}

}
