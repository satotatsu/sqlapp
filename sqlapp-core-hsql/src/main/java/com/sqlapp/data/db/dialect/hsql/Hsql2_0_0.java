/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-hsql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.hsql;

import static com.sqlapp.util.CommonUtils.cast;

import java.util.function.Supplier;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.converter.IntervalConverter;
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
import com.sqlapp.data.converter.PipeConverter;
import com.sqlapp.data.db.datatype.DefaultJdbcTypeHandler;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.hsql.converter.FromHsqlIntervalMonthConverter;
import com.sqlapp.data.db.dialect.hsql.converter.FromHsqlIntervalSecondConverter;
import com.sqlapp.data.db.dialect.hsql.converter.ToHsqlIntervalMonthConverter;
import com.sqlapp.data.db.dialect.hsql.converter.ToHsqlIntervalSecondConverter;
import com.sqlapp.data.db.dialect.hsql.sql.Hsql2SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.interval.Interval;

public class Hsql2_0_0 extends Hsql {
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
		// Time With Time Zone
		getDbDataTypes().addTimeWithTimeZone().setLiteral("'", "'")
				.setDefaultValueLiteral(getCurrentTimeWithTimeZoneFunction())
				.setMaxPrecision(9).setDefaultPrecision(0);
		// Timestamp With Time Zone
		getDbDataTypes()
				.addTimestampWithTimeZoneType()
				.setLiteral("'", "'")
				.setDefaultValueLiteral(
						getCurrentTimestampWithTimeZoneFunction())
				.setMaxPrecision(9).setDefaultPrecision(6);
		// INTERVAL YEAR
		getDbDataTypes()
				.addIntervalYear()
				.setDefaultPrecision(2)
				.setMaxPrecision(9)
				.setJdbcTypeHandler(
						getIntervalMonthConverter(new IntervalYearConverter()));
		// INTERVAL MONTH
		getDbDataTypes()
				.addIntervalMonth()
				.setDefaultPrecision(2)
				.setMaxPrecision(9)
				.setJdbcTypeHandler(
						getIntervalMonthConverter(new IntervalMonthConverter()));
		// INTERVAL DAY
		getDbDataTypes()
				.addIntervalDay()
				.setDefaultPrecision(2)
				.setMaxPrecision(9)
				.setJdbcTypeHandler(
						getIntervalSecondConverter(new IntervalSecondConverter()));
		// INTERVAL HOUR
		getDbDataTypes()
				.addIntervalHour()
				.setDefaultPrecision(2)
				.setMaxPrecision(9)
				.setJdbcTypeHandler(
						getIntervalSecondConverter(new IntervalHourConverter()));
		// INTERVAL MINUTE
		getDbDataTypes()
				.addIntervalMinute()
				.setDefaultPrecision(2)
				.setMaxPrecision(9)
				.setJdbcTypeHandler(
						getIntervalSecondConverter(new IntervalMinuteConverter()));
		// INTERVAL SECOND
		getDbDataTypes()
				.addIntervalSecond()
				.setDefaultPrecision(2)
				.setMaxPrecision(9)
				.setJdbcTypeHandler(
						getIntervalSecondConverter(new IntervalSecondConverter()));
		// INTERVAL YEAR TO MONTH
		getDbDataTypes().addIntervalYearToMonth().setJdbcTypeHandler(
				getIntervalMonthConverter(new IntervalYearToMonthConverter()));
		// INTERVAL DAY TO HOUR
		getDbDataTypes().addIntervalDayToHour().setJdbcTypeHandler(
				getIntervalSecondConverter(new IntervalDayToHourConverter()));
		// INTERVAL DAY TO MINUTE
		getDbDataTypes().addIntervalDayToMinute().setJdbcTypeHandler(
				getIntervalSecondConverter(new IntervalDayToMinuteConverter()));
		// INTERVAL DAY TO SECOND
		getDbDataTypes().addIntervalDayToSecond().setJdbcTypeHandler(
				getIntervalSecondConverter(new IntervalDayToSecondConverter()));
		// INTERVAL HOUR TO MINUTE
		getDbDataTypes()
				.addIntervalHourToMinute()
				.setJdbcTypeHandler(
						getIntervalSecondConverter(new IntervalHourToMinuteConverter()));
		// INTERVAL HOUR TO SECOND
		getDbDataTypes()
				.addIntervalHourToSecond()
				.setJdbcTypeHandler(
						getIntervalSecondConverter(new IntervalHourToSecondConverter()));
	}

	private static final IntervalConverter INTERVAL_CONVERTER = cast(Converters
			.getDefault().getConverter(Interval.class));

	/**
	 * HSQL固有のIntervalMonthDataのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getIntervalMonthConverter(
			final Converter<?> resultSetConveter) {
		final DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(
				java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(new PipeConverter(
				new FromHsqlIntervalMonthConverter(), resultSetConveter));
		converter.setStatementConverter(new PipeConverter(INTERVAL_CONVERTER,
				new ToHsqlIntervalMonthConverter()));
		return converter;
	}

	/**
	 * HSQL固有のIntervalSecondのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getIntervalSecondConverter(
			final Converter<?> resultSetConveter) {
		final DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(
				java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(new PipeConverter(
				new FromHsqlIntervalSecondConverter(), resultSetConveter));
		converter.setStatementConverter(new PipeConverter(INTERVAL_CONVERTER,
				new ToHsqlIntervalSecondConverter()));
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
}
