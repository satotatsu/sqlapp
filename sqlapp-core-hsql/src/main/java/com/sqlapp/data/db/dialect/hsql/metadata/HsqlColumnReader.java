/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.hsql.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.max;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * HSQLのカラム作成クラス
 * 
 * @author satoh
 * 
 */
public class HsqlColumnReader extends ColumnReader {

	public HsqlColumnReader(Dialect dialect) {
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
				Column obj = createColumn(rs);
				result.add(obj);
			}
		});
		return result;
	}

	protected Column createColumn(ExResultSet rs) throws SQLException {
		Column obj = new Column(getString(rs, COLUMN_NAME));
		boolean nullable = toBoolean(getString(rs, "IS_NULLABLE"));
		String data_type = getString(rs, "DATA_TYPE");
		boolean identity = toBoolean(getString(rs, "IS_IDENTITY"));
		if (identity) {
			setIdentityInfo(rs, obj);
		}
		String interval_type = getString(rs, "INTERVAL_TYPE");
		String domainName = getString(rs, DOMAIN_NAME);
		// String domain_name=getString(rs, "DOMAIN_NAME");
		// String udt_name=getString(rs, "UDT_NAME");
		Long char_maxlength = getLong(rs, "CHARACTER_MAXIMUM_LENGTH");
		Long numeric_precision = getLong(rs, "NUMERIC_PRECISION");
		Integer numeric_scale = getInteger(rs, "NUMERIC_SCALE");
		Long datetime_scale = getLong(rs, "DATETIME_PRECISION");
		if (!isEmpty(domainName)) {
			obj.setDataTypeName(domainName);
			obj.setDataType(DataType.DOMAIN);
		} else if (!isEmpty(interval_type)) {
			Long interval_precision = getLong(rs, "INTERVAL_PRECISION");
			obj.setNullable(nullable);
			obj.setIdentity(identity);
			this.getDialect().setDbType(data_type + " " + interval_type, interval_precision, null, obj);
		} else {
			obj.setNullable(nullable);
			obj.setIdentity(identity);
			this.getDialect().setDbType(data_type, max(char_maxlength, numeric_precision, datetime_scale),
					numeric_scale, obj);
			obj.setScale(numeric_scale);
		}
		obj.setDefaultValue(getString(rs, "COLUMN_DEFAULT"));
		obj.setCatalogName(getString(rs, TABLE_CATALOG));
		obj.setSchemaName(getString(rs, TABLE_SCHEMA));
		obj.setTableName(getString(rs, TABLE_NAME));
		// int char_octet_maxlength=rs.getInt("CHARACTER_OCTET_LENGTH");
		// column.setOctetLength(char_octet_maxlength);
		obj.setRemarks(getString(rs, "COMMENT"));
		return obj;
	}

	protected void setIdentityInfo(ExResultSet rs, Column obj) throws SQLException {
		String sequence_name = getString(rs, "SEQUENCE_NAME");
		if (sequence_name != null) {
			String sequence_data_type = getString(rs, "SEQUENCE_DATA_TYPE");
			Column seqColumn = new Column();
			seqColumn.setDataTypeName(sequence_data_type);
			this.getDialect().setDbType(sequence_data_type, null, null, seqColumn);
			Sequence sequence = new Sequence(sequence_name);
			// sequence.setDataTypeName(sequence_data_type);
			sequence.setDataType(seqColumn.getDataType());
			sequence.setStartValue(getLong(rs, "SATART_WITH"));
			sequence.setIncrementBy(getLong(rs, "INCREMENT"));
			sequence.setMaxValue(getLong(rs, "MAXIMUM_VALUE"));
			sequence.setMinValue(getLong(rs, "MINIMUM_VALUE"));
			sequence.setLastValue(getLong(rs, "NEXT_VALUE"));
			obj.setSequence(sequence);
		} else {
			String identity_generation = getString(rs, "IDENTITY_GENERATION");
			Long identity_start = getLong(rs, "IDENTITY_START");
			Long identity_increment = getLong(rs, "IDENTITY_INCREMENT");
			Long identity_maximum = getLong(rs, "IDENTITY_MAXIMUM");
			Long identity_miniimum = getLong(rs, "IDENTITY_MINIMUM");
			boolean identity_cycle = !"NO".equals(getString(rs, "IDENTITY_CYCLE"));
			String is_generated = getString(rs, "IS_GENERATED");
			String generation_expression = getString(rs, "GENERATION_EXPRESSION");
			obj.setIdentityStartValue(identity_start);
			obj.setIdentityStep(identity_increment);
			obj.setIdentityMaxValue(identity_maximum);
			obj.setIdentityMinValue(identity_miniimum);
			obj.setIdentityCycle(identity_cycle);
			obj.setDefaultValue(identity_generation);
		}
	}

	protected SqlNode getSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns.sql");
	}
}
