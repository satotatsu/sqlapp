/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres;

import static com.sqlapp.util.CommonUtils.LEN_1GB;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Function;
import java.util.function.Supplier;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.IntervalDayConverter;
import com.sqlapp.data.converter.IntervalDayToHourConverter;
import com.sqlapp.data.converter.IntervalDayToMinuteConverter;
import com.sqlapp.data.converter.IntervalDayToSecondConverter;
import com.sqlapp.data.converter.IntervalHourConverter;
import com.sqlapp.data.converter.IntervalHourToMinuteConverter;
import com.sqlapp.data.converter.IntervalHourToSecondConverter;
import com.sqlapp.data.converter.IntervalMinuteConverter;
import com.sqlapp.data.converter.IntervalMinuteToSecondConverter;
import com.sqlapp.data.converter.IntervalMonthConverter;
import com.sqlapp.data.converter.IntervalSecondConverter;
import com.sqlapp.data.converter.IntervalYearConverter;
import com.sqlapp.data.converter.IntervalYearToMonthConverter;
import com.sqlapp.data.converter.PipeConverter;
import com.sqlapp.data.db.datatype.DefaultJdbcTypeHandler;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;
import com.sqlapp.data.db.datatype.NumericType;
import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.LengthColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.PrecisionColumnTypeMatcher;
import com.sqlapp.data.db.dialect.DefaultCase;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGBoxConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGCircleConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGIntervalConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGLineConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGLsegConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGPathConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGPointConverter;
import com.sqlapp.data.db.dialect.postgres.converter.FromPGPolygonConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGBoxConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGCircleConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGIntervalConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGLineConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGLsegConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGPathConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGPointConverter;
import com.sqlapp.data.db.dialect.postgres.converter.ToPGPolygonConverter;
import com.sqlapp.data.db.dialect.postgres.db.datatype.util.PostgresArrayColumnTypeMatcher;
import com.sqlapp.data.db.dialect.postgres.metadata.PostgresCatalogReader;
import com.sqlapp.data.db.dialect.postgres.sql.PostgresSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.postgres.util.PostgresJdbcHandler;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlSplitter;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * PostgreSQL固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Postgres extends Dialect {
	@Override
	public String getIdentityInsertDefaultValue(Column column) {
		if (column.getIdentityGenerationType() == IdentityGenerationType.ByDefault) {
			return "default";
		}
		return null;
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7843214207236066501L;

	private static void registerIntervalMatcher(DbDataType<?> type) {
		String name = type.getDataType().getTypeName();
		boolean seconds = type.getDataType() == DataType.INTERVAL || name.endsWith("SECOND");
		type.setColumnTypeMatcher(new RegexColumnTypeMatcher(
				name.replace(" ", "\\s+") + (seconds ? "(?:\\s*\\(\\s*(?<length>[0-9]+)\\s*\\))?" : ""),
				(matcher, information) -> {
					if (seconds && matcher.group("length") != null)
						information.setLength(matcher.group("length"));
				}));
	}

	protected static final Function<ColumnTypeMatcher, ColumnTypeMatcher> columnTypeMatcherConverter = (
			matcher) -> new PostgresArrayColumnTypeMatcher(matcher);

	/**
	 * コンストラクタ
	 */
	protected Postgres(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public boolean setDbType(final String productDataType, final Long lengthOrPrecision, final Integer scale,
			final DataTypeLengthProperties<?> column) {
		boolean matched = super.setDbType(productDataType, lengthOrPrecision, scale, column);
		if (matched && (column.getDataType() == DataType.RANGE || column.getDataType() == DataType.MULTIRANGE)) {
			column.setDataTypeName(productDataType);
		}
		return matched;
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		getDbDataTypes().addRange("RANGE", type -> {
			type.setCreateFormat("RANGE");
			type.addPetternColumnTypeMatcher(
					"(?<dataTypeName>INT4RANGE|INT8RANGE|NUMRANGE|TSRANGE|TSTZRANGE|DATERANGE)",
					(matcher, information) -> information.setDataTypeName(matcher.group("dataTypeName")));
		});
		// CHAR
		getDbDataTypes().addChar(32672, type -> {
			type.setColumnTypeMatcher(new LengthColumnTypeMatcher("(CHAR(ACTER)?|BPCHAR)", ""));
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
			type.setSupportsArray(true);
		});
		// VARCHAR
		getDbDataTypes().addVarchar(32672, type -> {
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		getDbDataTypes().addVarchar("TEXT", LEN_1GB, type -> {
			type.setCreateFormat("TEXT").setFixedLength(true).setDefaultLength(LEN_1GB);
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// CLOB
		// getDataTypes().addClob("TEXT", LEN_1GB).setCreateFormat("TEXT");
		// BLOB
		getDbDataTypes().addBlob("BYTEA", LEN_1GB, type -> {
			type.setCreateFormat("BYTEA").setLiteral("decode('", "', 'hex')");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Boolean
		getDbDataTypes().addBoolean("BOOLEAN", type -> {
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// BINARY
		getDbDataTypes().addBinary("BIT", LEN_1GB, type -> {
			type.setLiteral("decode('", "', 'hex')");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// VARBINARY
		getDbDataTypes().addVarBinary("VARBIT", LEN_1GB, type -> {
			type.setLiteral("decode('", "', 'hex')");
			type.addColumnTypeMatcher("BIT\\s+VARYING");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Int16
		getDbDataTypes().addSmallInt(type -> {
			type.addColumnTypeMatcher("INT2");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Int32
		getDbDataTypes().addInt(type -> {
			type.addColumnTypeMatcher("INT4");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Int64
		getDbDataTypes().addBigInt(type -> {
			type.addColumnTypeMatcher("INT8");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Serial
		getDbDataTypes().addSerial("SERIAL", type -> {
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// BigSerial
		getDbDataTypes().addBigSerial("BIGSERIAL", type -> {
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Numeric
		getDbDataTypes().addNumeric(type -> {
			type.setMaxPrecision(1000).setMaxScale(1000);
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// GUID
		getDbDataTypes().addUUID("UUID", type -> {
			type.setLiteral("{", "}");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Single
		getDbDataTypes().addReal("FLOAT4", type -> {
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Double
		getDbDataTypes().addDouble(type -> {
			type.addColumnTypeMatcher("FLOAT8");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Money
		getDbDataTypes().addMoney("MONEY", type -> {
			type.setLiteral("", "::text::money").setSurrogateType(new NumericType().setMaxPrecision(17).setScale(2))
					.setFixedPrecision(false).setFixedScale(false);
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// XML
		getDbDataTypes().addSqlXml("XML", type -> {
			type.setLiteral("XML '", "'");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// SmallDateTime
		getDbDataTypes().addSmallDateTime("abstime", type -> {
			type.setDefaultValueLiteral(getCurrentDateFunction());
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Date
		getDbDataTypes().addDate(type -> {
			type.setDefaultValueLiteral(getCurrentDateFunction());
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Time
		getDbDataTypes().addTime(type -> {
			type.setDefaultValueLiteral(getCurrentTimeFunction()).setLiteral("TIME '", "'");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Time With Time Zone
		getDbDataTypes().addTimeWithTimeZone("TIMETZ", type -> {
			type.setDefaultPrecision(6).setDefaultValueLiteral(getCurrentTimeFunction())
					.setLiteral("TIME WITH TIME ZONE '", "'");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Timestamp
		getDbDataTypes().addTimestamp(type -> {
			type.setDefaultValueLiteral(getCurrentTimestampFunction()).setLiteral("TIMESTAMP '", "'");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// Timestamp With Time Zone
		getDbDataTypes().addTimestampWithTimeZone("TIMESTAMPTZ", type -> {
			type.setColumnTypeMatcher(new PrecisionColumnTypeMatcher("TIMESTAMP", "WITH\\s+TIME\\s+ZONE"));
			type.setLiteral("TIMESTAMP WITH TIME ZONE '", "'").setDefaultPrecision(6)
					.setDefaultValueLiteral(getCurrentTimestampFunction());
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL
		getDbDataTypes().addInterval(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL YEAR
		getDbDataTypes().addIntervalYear(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setCreateFormat("INTERVAL YEAR").setJdbcTypeHandler(getIntervalConverter(new IntervalYearConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL MONTH
		getDbDataTypes().addIntervalMonth(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setCreateFormat("INTERVAL MONTH")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalMonthConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL DAY
		getDbDataTypes().addIntervalDay(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setCreateFormat("INTERVAL DAY").setJdbcTypeHandler(getIntervalConverter(new IntervalDayConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL HOUR
		getDbDataTypes().addIntervalHour(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setCreateFormat("INTERVAL HOUR").setJdbcTypeHandler(getIntervalConverter(new IntervalHourConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL MINUTE
		getDbDataTypes().addIntervalMinute(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setCreateFormat("INTERVAL MINUTE")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalMinuteConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL SECOND
		getDbDataTypes().addIntervalSecond(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setCreateFormat("INTERVAL SECOND")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalSecondConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL YEAR TO MONTH
		getDbDataTypes().addIntervalYearToMonth(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setCreateFormat("INTERVAL YEAR TO MONTH")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalYearToMonthConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL DAY TO HOUR
		getDbDataTypes().addIntervalDayToHour(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setCreateFormat("INTERVAL DAY TO HOUR")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalDayToHourConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL DAY TO MINUTE
		getDbDataTypes().addIntervalDayToMinute(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setCreateFormat("INTERVAL DAY TO MINUTE")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalDayToMinuteConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL DAY TO SECOND
		getDbDataTypes().addIntervalDayToSecond(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setDefaultScale(null);
			type.setCreateFormat("INTERVAL DAY TO SECOND")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalDayToSecondConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL HOUR TO MINUTE
		getDbDataTypes().addIntervalHourToMinute(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setCreateFormat("INTERVAL HOUR TO MINUTE")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalHourToMinuteConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INTERVAL HOUR TO SECOND
		getDbDataTypes().addIntervalHourToSecond(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setCreateFormat("INTERVAL HOUR TO SECOND")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalHourToSecondConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});

		// INTERVAL MINUTE TO SECOND
		getDbDataTypes().addIntervalMinuteToSecond(type -> {
			type.setDefaultPrecision(null);
			registerIntervalMatcher(type);
			type.setCreateFormat("INTERVAL MINUTE TO SECOND")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalMinuteToSecondConverter()));
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// INET
		getDbDataTypes().addInetType(type -> {
			type.setLiteralPrefix("inet '").setLiteralSuffix("'");
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// CIDR
		getDbDataTypes().addCidrType(type -> {
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// MACADDR
		getDbDataTypes().addMacAddrType(type -> {
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// OID
		getDbDataTypes().addRowId("OID", type -> {
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// POINT
		getDbDataTypes().addPointType(type -> {
			type.setJdbcTypeHandler(getPointConverter());
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// CIRCLE
		getDbDataTypes().addCircleType(type -> {
			type.setJdbcTypeHandler(getCircleConverter());
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// BOX
		getDbDataTypes().addBoxType(type -> {
			type.setJdbcTypeHandler(getBoxConverter());
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// LINE
		getDbDataTypes().addLineType(type -> {
			type.setJdbcTypeHandler(getLineConverter());
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// LSEG
		getDbDataTypes().addLsegType(type -> {
			type.setJdbcTypeHandler(getLsegConverter());
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// PATH
		getDbDataTypes().addPathType(type -> {
			type.setJdbcTypeHandler(getPathConverter());
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		// POLYGON
		getDbDataTypes().addPolygonType(type -> {
			type.setJdbcTypeHandler(getPolygonConverter());
			type.setSupportsArray(true);
			type.convertColumnTypeMatchers(columnTypeMatcherConverter);
		});
		//
	}

	/**
	 * Postgres固有のIntervalのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getIntervalConverter(final Converter<?> resultSetConveter) {
		final DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(new PipeConverter(new FromPGIntervalConverter(), resultSetConveter));
		converter.setStatementConverter(new ToPGIntervalConverter());
		return converter;
	}

	/**
	 * Postgres固有のPointのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getPointConverter() {
		final DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(new FromPGPointConverter());
		converter.setStatementConverter(new ToPGPointConverter());
		return converter;
	}

	/**
	 * Postgres固有のCircleのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getCircleConverter() {
		final DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(new FromPGCircleConverter());
		converter.setStatementConverter(new ToPGCircleConverter());
		return converter;
	}

	/**
	 * Postgres固有のCircleのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getBoxConverter() {
		final DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(new FromPGBoxConverter());
		converter.setStatementConverter(new ToPGBoxConverter());
		return converter;
	}

	/**
	 * Postgres固有のCircleのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getLsegConverter() {
		final DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(new FromPGLsegConverter());
		converter.setStatementConverter(new ToPGLsegConverter());
		return converter;
	}

	/**
	 * Postgres固有のLineのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getLineConverter() {
		final DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(new FromPGLineConverter());
		converter.setStatementConverter(new ToPGLineConverter());
		return converter;
	}

	/**
	 * Postgres固有のPathのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getPathConverter() {
		final DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(new FromPGPathConverter());
		converter.setStatementConverter(new ToPGPathConverter());
		return converter;
	}

	/**
	 * Postgres固有のPolygonのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getPolygonConverter() {
		final DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(new FromPGPolygonConverter());
		converter.setStatementConverter(new ToPGPolygonConverter());
		return converter;
	}

	/**
	 * DB製品名
	 */
	@Override
	public String getProductName() {
		return "PostgreSQL";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
	 */
	@Override
	public String getSimpleName() {
		return "postgres";
	}

	@Override
	public String getSequenceNextValString(final String sequenceName) {
		return "select nextval ('" + sequenceName + "')";
	}

	@Override
	public String getIdentitySelectString() {
		return "select lastval()";
	}

	@Override
	public boolean supportsIdentity() {
		return true;
	}

	@Override
	public boolean supportsSequence() {
		return true;
	}

	/**
	 * LIMIT句のサポート
	 */
	@Override
	public boolean supportsLimit() {
		return true;
	}

	/**
	 * Offset句のサポート
	 */
	@Override
	public boolean supportsLimitOffset() {
		return true;
	}

	@Override
	public boolean supportsDropCascade() {
		return true;
	}

	/**
	 * カラムに紐づくSEQUENCEのサポート PostgreSQLのserial4,serial8対策
	 */
	@Override
	public boolean supportsColumnSequence() {
		return true;
	}

	@Override
	public boolean supportsCascadeDelete() {
		return true;
	}

	@Override
	public boolean supportsRuleOnDelete(final CascadeRule rule) {
		return true;
	}

	@Override
	public boolean supportsCascadeUpdate() {
		return true;
	}

	@Override
	public boolean supportsRuleOnUpdate(final CascadeRule rule) {
		return true;
	}

	@Override
	public boolean supportsCascadeRistrict() {
		return true;
	}

	@Override
	public boolean supportsDefaultValueFunction() {
		return true;
	}

	@Override
	public boolean supportsFunctionOverload() {
		return true;
	}

	@Override
	public boolean supportsProcedureOverload() {
		return true;
	}

	@Override
	public int hashCode() {
		return getProductName().hashCode();
	}

	@Override
	public DefaultCase getDefaultCase() {
		return DefaultCase.LowerCase;
	}

	@Override
	public String nativeCaseString(final String value) {
		if (isEmpty(value)) {
			return value;
		}
		if (isQuoted(value)) {
			return value;
		}
		return value.toLowerCase();
	}

	public String selectRecursiveSql(final Table table, final boolean backTrace) {
		return null;
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new PostgresCatalogReader(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.Dialect#createDbOperationFactory()
	 */
	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new PostgresSqlFactoryRegistry(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.Dialect#supportsCatalog()
	 */
	@Override
	public boolean supportsCatalog() {
		return true;
	}

	@Override
	public PostgresSqlBuilder createSqlBuilder() {
		return new PostgresSqlBuilder(this);
	}

	@Override
	public PostgresSqlSplitter createSqlSplitter() {
		return new PostgresSqlSplitter(this);
	}

	// Reserved and identifier-sensitive PostgreSQL grammar words. Quoting a word
	// that became reserved in a newer release is also valid on older servers.
	private static final java.util.Set<String> IDENTIFIER_KEYWORDS = java.util.Set.of(
			"all", "analyse", "analyze", "and", "any", "array", "as", "asc", "asymmetric", "authorization",
			"binary", "both", "case", "cast", "check", "collate", "collation", "column", "concurrently",
			"constraint", "create", "cross", "current_catalog", "current_date", "current_role", "current_schema",
			"current_time", "current_timestamp", "current_user", "default", "deferrable", "desc", "distinct", "do",
			"else", "end", "except", "false", "fetch", "for", "foreign", "freeze", "from", "full", "grant",
			"group", "having", "ilike", "in", "initially", "inner", "intersect", "into", "is", "isnull", "join",
			"lateral", "leading", "left", "like", "limit", "localtime", "localtimestamp", "natural", "not",
			"notnull", "null", "offset", "on", "only", "or", "order", "outer", "overlaps", "placing", "primary",
			"references", "returning", "right", "select", "session_user", "similar", "some", "symmetric", "table",
			"tablesample", "then", "to", "trailing", "true", "union", "unique", "user", "using", "variadic",
			"verbose", "when", "where", "window", "with");

	@Override
	public boolean needQuote(String target) {
		if (target == null || target.isEmpty() || isQuoted(target)) return false;
		return IDENTIFIER_KEYWORDS.contains(target.toLowerCase(java.util.Locale.ROOT))
				|| Character.isDigit(target.charAt(0)) || super.needQuote(target);
	}

	@Override
	protected String doQuote(final String target) {
		final StringBuilder builder = new StringBuilder(target.length() + 2);
		builder.append(getOpenQuote()).append(target.replace("\"", "\"\"")).append(getCloseQuote());
		return builder.toString();
	}

	@Override
	public PostgresJdbcHandler createJdbcHandler(final SqlNode sqlNode) {
		final PostgresJdbcHandler jdbcHandler = new PostgresJdbcHandler(sqlNode);
		return jdbcHandler;
	}

	@Override
	public boolean isDdlRollbackable() {
		return true;
	}

	@Override
	public boolean supportsValues() {
		return true;
	}

	@Override
	public boolean supportsRowValueComparison() {
		return true;
	}

	@Override
	public boolean supportsRowValueComparisonIn() {
		return true;
	}

}
