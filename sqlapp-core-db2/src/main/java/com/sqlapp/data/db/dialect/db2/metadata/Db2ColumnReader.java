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
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Sequence;

/**
 * DB2カラム読み込み
 * 
 * @author satoh
 * 
 */
public class Db2ColumnReader extends ColumnReader {

	protected Db2ColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(Connection connection, ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNode(productVersionInfo);
		final List<Column> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Column column = createColumn(rs);
				result.add(column);
			}
		});
		return result;
	}

	protected Column createColumn(ExResultSet rs) throws SQLException {
		Column obj = new Column(getString(rs, COLUMN_NAME));
		obj.setTableName(getString(rs, TABLE_NAME));
		obj.setSchemaName(getString(rs, SCHEMA_NAME));
		obj.setDefaultValue(getString(rs, "DEFAULT_VALUE"));
		obj.setNullable("Y".equals(getString(rs, "NULLS")));
		obj.setIdentity("Y".equals(getString(rs, "IDENTITY")));
		if (obj.isIdentity()) {
			if (getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110) {
				String mode = getString(rs, "GENERATED");
				if (!"A".equals(mode) && !"D".equals(mode))
					throw new SQLException("Invalid Db2 identity generation mode: " + obj.getName());
				for (String key : List.of("START", "INCREMENT", "MINVALUE", "MAXVALUE")) {
					java.math.BigDecimal value = rs.getBigDecimal(key);
					if (value != null)
						try {
							value.longValueExact();
						} catch (ArithmeticException e) {
							obj.getSpecifics().put("DB2_IDENTITY_" + key, value.toBigIntegerExact().toString());
						}
				}
			}
			obj.setIdentityGenerationType(
					"A".equalsIgnoreCase(getString(rs, "GENERATED")) ? IdentityGenerationType.Always
							: IdentityGenerationType.ByDefault);
			obj.setSequence(new Sequence());
			obj.setIdentityStep(rs.getBigDecimal("INCREMENT"));
			obj.setIdentityStartValue(rs.getBigDecimal("START"));
			obj.setIdentityMaxValue(rs.getBigDecimal("MAXVALUE"));
			obj.setIdentityMinValue(rs.getBigDecimal("MINVALUE"));
			// Long-valued column XML attributes cannot represent DECIMAL(31,0).
			// Keep those exact values in DB2 specifics rather than exposing wrapped longs.
			if (obj.getSpecifics().containsKey("DB2_IDENTITY_START"))
				obj.setIdentityStartValue((Number) null);
			if (obj.getSpecifics().containsKey("DB2_IDENTITY_INCREMENT"))
				obj.setIdentityStep((Number) null);
			if (obj.getSpecifics().containsKey("DB2_IDENTITY_MINVALUE"))
				obj.setIdentityMinValue((Number) null);
			if (obj.getSpecifics().containsKey("DB2_IDENTITY_MAXVALUE"))
				obj.setIdentityMaxValue((Number) null);
			obj.setIdentityCycle("Y".equalsIgnoreCase(getString(rs, "CYCLE")));
			long cache = rs.getLong("CACHE");
			if (cache != -1) {
				obj.setIdentityCacheSize(cache);
			}
			obj.setIdentityOrder("Y".equalsIgnoreCase(getString(rs, "ORDER")));
		}
		Long length = this.getLong(rs, "LENGTH");
		Integer scale = this.getInteger(rs, "SCALE");
		obj.setCollation(getString(rs, "COLLATIONNAME"));
		obj.setRemarks(getString(rs, REMARKS));
		String productDataType = getString(rs, "TYPENAME");
		this.getDialect().setDbType(productDataType, length, scale, obj);
		if (getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110) {
			if ("XML".equals(productDataType))
				obj.setDataType(com.sqlapp.data.db.datatype.DataType.SQLXML);
			if ("BLOB".equals(productDataType))
				obj.setDataType(com.sqlapp.data.db.datatype.DataType.BLOB);
			if ("CLOB".equals(productDataType))
				obj.setDataType(com.sqlapp.data.db.datatype.DataType.CLOB);
			if ("DBCLOB".equals(productDataType))
				obj.setDataType(com.sqlapp.data.db.datatype.DataType.NCLOB);
			if (obj.getDataType() == com.sqlapp.data.db.datatype.DataType.BLOB
					|| obj.getDataType() == com.sqlapp.data.db.datatype.DataType.CLOB
					|| obj.getDataType() == com.sqlapp.data.db.datatype.DataType.NCLOB) {
				obj.setLength(length);
				if (length != null)
					obj.getSpecifics().put("DB2_LOB_LENGTH", length.toString());
				if (length != null)
					obj.getSpecifics().put("DB2_LOB_OCTET_LENGTH", length.toString());
			}
			String typeSchema = getString(rs, "TYPESCHEMA");
			if (typeSchema != null && !"SYSIBM".equals(typeSchema)) {
				obj.setDataType(com.sqlapp.data.db.datatype.DataType.OTHER);
				obj.setDataTypeName(
						com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.qualified(typeSchema, productDataType));
				obj.setLength((Long) null).setScale(null);
			}
			String codepage = getString(rs, "CODEPAGE");
			if (codepage != null)
				obj.getSpecifics().put("CODEPAGE", codepage);
			if (typeSchema != null)
				obj.getSpecifics().put("TYPESCHEMA", typeSchema);
			for (String key : List.of("COMPACT", "INLINE_LENGTH", "COLLATIONSCHEMA")) {
				String value = getString(rs, key);
				if (value != null && !value.isBlank())
					obj.getSpecifics().put(key, value);
			}
		}
		String logged = trim(getString(rs, "LOGGED"));
		if (!isEmpty(logged)) {
			setSpecifics(rs, "LOGGED", obj);
		}
		return obj;
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString(
				getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110 ? "columns1110.sql" : "columns.sql");
	}

}
