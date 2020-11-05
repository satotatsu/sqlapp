/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-postgres.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.postgres.metadata;

import static com.sqlapp.util.CommonUtils.eq;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.ProductVersionInfo;

public class Postgres83ColumnReader extends PostgresColumnReader {

	protected Postgres83ColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Column> result = super.doGetAll(connection, context, productVersionInfo);
		List<Domain> enumList = getAllEnumInfo(connection, context,
				productVersionInfo);
		for (Column column : result) {
			for (Domain domain : enumList) {
				if (eq(column.getDataTypeName(), domain.getName())) {
					column.getValues().addAll(domain.getValues());
					column.setDataType(domain.getDataType());
					break;
				}
			}
		}
		return result;
	}

	/**
	 * ENUMドメイン情報を取得します
	 * 
	 * @param connection
	 * @param context
	 */
	protected List<Domain> getAllEnumInfo(Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		PostgresEnumReader reader = new PostgresEnumReader(this.getDialect());
		reader.setReaderOptions(this.getReaderOptions());
		context.put(DbObjects.DOMAIN.getCamelCaseNameLabel(), (String) null);
		return reader.getAll(connection, context);
	}
}