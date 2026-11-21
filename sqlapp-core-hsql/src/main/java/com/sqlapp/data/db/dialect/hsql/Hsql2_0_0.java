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

package com.sqlapp.data.db.dialect.hsql;

import java.util.function.Supplier;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.DurationConverter;
import com.sqlapp.data.converter.IntervalDayToHourConverter;
import com.sqlapp.data.converter.IntervalDayToMinuteConverter;
import com.sqlapp.data.converter.IntervalDayToSecondConverter;
import com.sqlapp.data.converter.IntervalHourConverter;
import com.sqlapp.data.converter.IntervalHourToMinuteConverter;
import com.sqlapp.data.converter.IntervalHourToSecondConverter;
import com.sqlapp.data.converter.IntervalMinuteConverter;
import com.sqlapp.data.converter.IntervalMonthConverter;
import com.sqlapp.data.converter.IntervalSecondConverter;
import com.sqlapp.data.converter.IntervalYearConverter;
import com.sqlapp.data.converter.IntervalYearToMonthConverter;
import com.sqlapp.data.converter.PeriodConverter;
import com.sqlapp.data.db.datatype.DefaultJdbcTypeHandler;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.hsql.sql.Hsql2SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.jdbc.sql.CorrelationStrategy;

public class Hsql2_0_0 extends Hsql {
	@Override
	protected com.sqlapp.data.db.dialect.ColumnMigration.Result convertColumnForMigration(
			final com.sqlapp.data.schemas.Column source) {
		return migrateNativeTimestamp(source, 6, false, null);
	}

	static final long MAX_LOB_LENGTH = 1024L * 1024 * 1024 * 1024;
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 343690471806596981L;

	protected Hsql2_0_0(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		super.registerDataType();
		registerLobTypes(Integer.MAX_VALUE);
		registerLobTypes(MAX_LOB_LENGTH);
		// BINARY(n): fixed length and zero padded, distinct from VARBINARY.
		getDbDataTypes().addBinary("BINARY", Integer.MAX_VALUE, type -> {
			type.setDefaultLength(1);
			type.setLiteral("X'", "'");
		});
		// Time With Time Zone
		getDbDataTypes().addTimeWithTimeZone(type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral(getCurrentTimeWithTimeZoneFunction()).setMaxPrecision(9)
					.setDefaultPrecision(0);
		});
		// Timestamp With Time Zone
		getDbDataTypes().addTimestampWithTimeZone(type -> {
			type.setLiteral("'", "'").setDefaultValueLiteral(getCurrentTimestampWithTimeZoneFunction())
					.setMaxPrecision(9).setDefaultPrecision(6);
		});
		for (var type : com.sqlapp.data.db.datatype.DataType.values()) {
			if (type.getMetaTtype() == com.sqlapp.data.db.datatype.MetaType.INTERVAL
					&& type != com.sqlapp.data.db.datatype.DataType.INTERVAL
					&& type != com.sqlapp.data.db.datatype.DataType.INTERVAL_YEAR_TO_DAY)
				getDbDataTypes().addStandardInterval(type, 9, 9, 0);
		}
	}

	@Override
	public String getSqlValueDefinition(com.sqlapp.data.schemas.Column column, Object value) {
		if (column.getDataType() != null
				&& column.getDataType().getMetaTtype() == com.sqlapp.data.db.datatype.MetaType.INTERVAL) {
			var handler = (com.sqlapp.data.db.datatype.StandardIntervalJdbcTypeHandler) getDbDataTypes()
					.getDbTypeStrict(column.getDataType()).getJdbcTypeHandler();
			return handler.literal(value, column);
		}
		return super.getSqlValueDefinition(column, value);
	}

	private void registerLobTypes(long maximum) {
		getDbDataTypes().addClob(maximum, type -> {
			type.setDefaultLength(1024L * 1024 * 1024);
			type.addColumnTypeMatcher("CHARACTER LARGE OBJECT");
			type.setCreateFormat("CLOB(", ")");
		});
		getDbDataTypes().addBlob("BLOB", maximum, type -> {
			type.setDefaultLength(1024L * 1024 * 1024);
			type.addColumnTypeMatcher("BINARY LARGE OBJECT");
			type.setCreateFormat("BLOB(", ")");
		});
	}

	/**
	 * HSQL固有のIntervalMonthDataのコンバータを取得するためのメソッド
	 *
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getIntervalMonthConverter(final Converter<?> resultSetConveter) {
		final DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(resultSetConveter);
		converter.setStatementConverter(new PeriodConverter());
		return converter;
	}

	/**
	 * HSQL固有のIntervalSecondのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getIntervalSecondConverter(final Converter<?> resultSetConveter) {
		final DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(resultSetConveter);
		converter.setStatementConverter(new DurationConverter());
		return converter;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.HSQL#hashCode()
	 */
	@Override
	public int hashCode() {
		return super.hashCode() + 1;
	}

	/**
	 * 同値判定
	 */
	@Override
	public boolean equals(final Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		return true;
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Hsql2SqlFactoryRegistry(this);
	}

	@Override
	public boolean supportsSequencePreallocation() {
		return true;
	}

	@Override
	public boolean supportsColumnFormula() {
		return true;
	}

	@Override
	public CorrelationStrategy getCorrelationStrategy() {
		return CorrelationStrategy.BY_KEY;
	}

	@Override
	public boolean supportsStandardOffsetFetchRows() {
		return true;
	}

	@Override
	public boolean supportsRowValueComparison() {
		return true;
	}

	@Override
	public boolean supportsRowValueComparisonWithParameters() {
		return false;
	}
}
