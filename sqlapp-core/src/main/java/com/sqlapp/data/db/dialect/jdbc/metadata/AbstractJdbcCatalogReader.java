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

package com.sqlapp.data.db.dialect.jdbc.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.DbUtils.close;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.ColumnPrivilegeReader;
import com.sqlapp.data.db.metadata.ObjectPrivilegeReader;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ProductVersionInfo;

/**
 * JDBCのカタログ読み込みクラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractJdbcCatalogReader extends CatalogReader {

	public AbstractJdbcCatalogReader(Dialect dialect) {
		super(dialect);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.DbMetadataReader#doGetAll(java.sql
	 * .Connection, com.sqlapp.data.parameter.ParametersContext)
	 */
	@Override
	protected List<Catalog> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		ResultSet rs = null;
		try {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			rs = databaseMetaData.getCatalogs();
			List<Catalog> result = list();
			while (rs.next()) {
				Catalog obj = new Catalog(getString(rs, "TABLE_CAT"));
				result.add(obj);
			}
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			close(rs);
		}
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new JdbcSchemaReader(this.getDialect());
	}

	@Override
	public String getCurrentCatalogName(Connection connection) {
		try {
			return connection.getCatalog();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected ObjectPrivilegeReader newObjectPrivilegeReader() {
		return new JdbcObjectPrivilegeReader(this.getDialect());
	}

	@Override
	protected ColumnPrivilegeReader newColumnPrivilegeReader() {
		return new JdbcColumnPrivilegeReader(this.getDialect());
	}

}
