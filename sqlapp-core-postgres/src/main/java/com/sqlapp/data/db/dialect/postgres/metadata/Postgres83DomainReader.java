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

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.ProductVersionInfo;

public class Postgres83DomainReader extends PostgresDomainReader {

	protected Postgres83DomainReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Domain> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		List<Domain> result = super.doGetAll(connection, context,
				productVersionInfo);
		List<Domain> enumList = getAllEnumInfo(connection, context,
				productVersionInfo);
		result.addAll(enumList);
		return result;
	}

	/**
	 * ENUMドメイン情報の取得
	 * 
	 * @param connection
	 * @param context
	 */
	protected List<Domain> getAllEnumInfo(Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		PostgresEnumReader reader = new PostgresEnumReader(this.getDialect());
		this.initializeChild(reader);
		return reader.getAll(connection, context);
	}
}
