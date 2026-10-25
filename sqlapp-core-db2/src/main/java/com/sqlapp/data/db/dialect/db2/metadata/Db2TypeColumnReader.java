/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-db2.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.db2.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.trim;

import java.sql.Connection;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TypeColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.TypeColumn;

/**
 * DB2のタイプカラム読み込みクラス
 * 
 * @author satoh
 * 
 */
public class Db2TypeColumnReader extends TypeColumnReader {

	protected Db2TypeColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<TypeColumn> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<TypeColumn> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				TypeColumn column = createTypeColumn(rs);
				result.add(column);
			}
		});
		return result;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache()
				.getString(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110 ? "typeColumns1110.sql"
						: "typeColumns.sql");
	}

	protected TypeColumn createTypeColumn(ExResultSet rs) throws SQLException {
		TypeColumn obj = createObject(getString(rs, COLUMN_NAME));
		obj.setTypeName(getString(rs, TYPE_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		Long length = this.getLong(rs, "LENGTH");
		Integer scale = this.getInteger(rs, "SCALE");
		String productDataType = getString(rs, DATA_TYPE);
		this.getDialect().setDbType(productDataType, length, scale, obj);
		if (getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110) {
			if (productDataType == null || productDataType.isBlank())
				throw new SQLException("Missing Db2 row/attribute field data type");
			obj.setDialect(getDialect());
			obj.getSpecifics().put("TYPENAME", productDataType);
			for (String key : List.of("LENGTH", "SCALE", "CODEPAGE", "TYPESTRINGUNITS", "STRINGUNITSLENGTH",
					"COLLATIONSCHEMA", "COLLATIONNAME", "COMPACT", "ORDINAL")) {
				String value = getString(rs, key);
				if (value != null)
					obj.getSpecifics().put(key, value);
			}
			String schema = getString(rs, "data_type_schema"), module = getString(rs, "data_type_module");
			if (schema != null)
				obj.getSpecifics().put("DB2_TYPE_SCHEMA", schema);
			if (module != null)
				obj.getSpecifics().put("DB2_TYPE_MODULE", module);
		}
		String logged = trim(getString(rs, "LOGGED"));
		if (!isEmpty(logged)) {
			setSpecifics(rs, "LOGGED", obj);
		}
		return obj;
	}

}
