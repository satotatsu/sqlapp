/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 *
 * sqlapp-core-db2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-db2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-db2.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TypeColumnReader;
import com.sqlapp.data.db.metadata.TypeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.Type.MetaType;

/**
 * Postgresのタイプ読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Db2TypeReader extends TypeReader {

	protected Db2TypeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Type> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Type> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Type obj = createType(rs);
				result.add(obj);
			}
		});
		return result;
	}

	@Override
	protected TypeColumnReader newColumnFactory() {
		return new Db2TypeColumnReader(this.getDialect());
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("types.sql");
	}

	protected Type createType(ExResultSet rs) throws SQLException {
		Type obj = new Type(getString(rs, TYPE_NAME));
		// obj.setSpecificName(getString(rs, "oid"));
		// obj.setCatalogName(getString(rs, "type_catalog"));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		// obj.addTextLine(splitLine(getString(rs, "definition")));
		String metaType = getString(rs, "METATYPE");
		if ("F".equalsIgnoreCase(metaType)) {
			obj.setMetaType(MetaType.Row);
		} else if ("C".equalsIgnoreCase(metaType)) {
			obj.setMetaType(MetaType.Cursor);
		}
		obj.setRemarks(getString(rs, REMARKS));
		return obj;
	}
}
