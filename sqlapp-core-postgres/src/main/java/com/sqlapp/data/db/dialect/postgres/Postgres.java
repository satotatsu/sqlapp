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
import com.sqlapp.data.db.dialect.postgres.metadata.PostgresCatalogReader;
import com.sqlapp.data.db.dialect.postgres.sql.PostgresSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.postgres.util.PostgresJdbcHandler;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlSplitter;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * PostgreSQL固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Postgres extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7843214207236066501L;

	/**
	 * コンストラクタ
	 */
	protected Postgres(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(32672, type -> {
			type.addFormats("BPCHAR\\s*\\(\\s*([0-9]+)\\s*\\)");
			type.setSupportsArray(true);
		});
		// VARCHAR
		getDbDataTypes().addVarchar(32672, type -> {
		});
		getDbDataTypes().addVarchar("TEXT", LEN_1GB, type -> {
			type.setFormats("TEXT\\s*").setCreateFormat("TEXT").setFixedLength(true).setDefaultLength(LEN_1GB);
		});
		// CLOB
		// getDataTypes().addClob("TEXT", LEN_1GB).setCreateFormat("TEXT");
		// BLOB
		getDbDataTypes().addBlob("BYTEA", LEN_1GB, type -> {
			type.setCreateFormat("BYTEA").setLiteral("decode('", "', 'hex')");
		});
		// Boolean
		getDbDataTypes().addBoolean("BOOLEAN", type -> {
			type.addFormats("BOOL").setSupportsArray(true);
		});
		// BINARY
		getDbDataTypes().addBinary("BIT", LEN_1GB, type -> {
			type.setLiteral("decode('", "', 'hex')").setSupportsArray(true);
		});
		// VARBINARY
		getDbDataTypes().addVarBinary("VARBIT", LEN_1GB, type -> {
			type.setLiteral("decode('", "', 'hex')").setSupportsArray(true);
		});
		// Int16
		getDbDataTypes().addSmallInt(type -> {
			type.addFormats("INT2").setSupportsArray(true);
		});
		// Int32
		getDbDataTypes().addInt(type -> {
			type.addFormats("INT4").addFormats("INTEGER").setSupportsArray(true);
		});
		// Int64
		getDbDataTypes().addBigInt(type -> {
			type.addFormats("INT8").setSupportsArray(true);
		});
		// Serial
		getDbDataTypes().addSerial("SERIAL", type -> {
		});
		// BigSerial
		getDbDataTypes().addBigSerial("BIGSERIAL", type -> {
		});
		// Numeric
		getDbDataTypes().addNumeric(type -> {
			type.setMaxPrecision(1000).setMaxScale(1000).setSupportsArray(true);
		});
		// GUID
		getDbDataTypes().addUUID("UUID", type -> {
			type.setLiteral("{", "}").setSupportsArray(true);
		});
		// Single
		getDbDataTypes().addReal("FLOAT4", type -> {
			type.setSupportsArray(true);
		});
		// Double
		getDbDataTypes().addDouble(type -> {
			type.addFormats("FLOAT8").setSupportsArray(true);
		});
		// Money
		getDbDataTypes().addMoney("MONEY", type -> {
			type.setLiteral("", "::text::money").setSurrogateType(new NumericType().setMaxPrecision(17).setScale(2))
					.setFixedPrecision(false).setFixedScale(false).setSupportsArray(true);
		});
		// XML
		getDbDataTypes().addSqlXml("XML", type -> {
			type.setLiteral("XML '", "'");
		});
		// SmallDateTime
		getDbDataTypes().addSmallDateTime("abstime", type -> {
			type.setDefaultValueLiteral(getCurrentDateFunction()).setSupportsArray(true);
		});
		// Date
		getDbDataTypes().addDate(type -> {
			type.setDefaultValueLiteral(getCurrentDateFunction()).setSupportsArray(true);
		});
		// Time
		getDbDataTypes().addTime(type -> {
			type.setDefaultValueLiteral(getCurrentTimeFunction()).setSupportsArray(true).setLiteral("TIME '", "'");
		});
		// Time With Time Zone
		getDbDataTypes().addTimeWithTimeZone("TIMETZ", type -> {
			type.setDefaultPrecision(6).setDefaultValueLiteral(getCurrentTimeFunction()).setSupportsArray(true)
					.setLiteral("TIME WITH TIME ZONE '", "'");
		});
		// Timestamp
		getDbDataTypes().addTimestamp(type -> {
			type.setDefaultValueLiteral(getCurrentTimestampFunction()).setSupportsArray(true).setLiteral("TIMESTAMP '",
					"'");
		});
		// Timestamp With Time Zone
		getDbDataTypes().addTimestampWithTimeZoneType("TIMESTAMPTZ", type -> {
			type.setLiteral("TIMESTAMP WITH TIME ZONE '", "'").setDefaultPrecision(6)
					.setDefaultValueLiteral(getCurrentTimestampFunction()).setSupportsArray(true);
		});
		// INTERVAL
		getDbDataTypes().addInterval(type -> {
			type.setSupportsArray(true);
		});
		// INTERVAL YAER
		getDbDataTypes().addIntervalYear(type -> {
			type.setCreateFormat("INTERVAL YAER").setJdbcTypeHandler(getIntervalConverter(new IntervalYearConverter()))
					.setSupportsArray(true);
		});
		// INTERVAL MONTH
		getDbDataTypes().addIntervalMonth(type -> {
			type.setCreateFormat("INTERVAL MONTH")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalMonthConverter())).setSupportsArray(true);
		});
		// INTERVAL DAY
		getDbDataTypes().addIntervalDay(type -> {
			type.setCreateFormat("INTERVAL DAY").setJdbcTypeHandler(getIntervalConverter(new IntervalDayConverter()))
					.setSupportsArray(true);
		});
		// INTERVAL HOUR
		getDbDataTypes().addIntervalHour(type -> {
			type.setCreateFormat("INTERVAL HOUR").setJdbcTypeHandler(getIntervalConverter(new IntervalHourConverter()))
					.setSupportsArray(true);
		});
		// INTERVAL MINUTE
		getDbDataTypes().addIntervalMinute(type -> {
			type.setCreateFormat("INTERVAL MINUTE")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalMinuteConverter())).setSupportsArray(true);
		});
		// INTERVAL SECOND
		getDbDataTypes().addIntervalSecond(type -> {
			type.setCreateFormat("INTERVAL SECOND")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalSecondConverter())).setSupportsArray(true);
		});
		// INTERVAL YAER TO MONTH
		getDbDataTypes().addIntervalYearToMonth(type -> {
			type.setCreateFormat("INTERVAL YAER TO MONTH")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalYearToMonthConverter()))
					.setSupportsArray(true);
		});
		// INTERVAL DAY TO HOUR
		getDbDataTypes().addIntervalDayToHour(type -> {
			type.setCreateFormat("INTERVAL DAY TO HOUR")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalDayToHourConverter())).setSupportsArray(true);
		});
		// INTERVAL DAY TO MINUTE
		getDbDataTypes().addIntervalDayToMinute(type -> {
			type.setCreateFormat("INTERVAL DAY TO MINUTE")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalDayToMinuteConverter()))
					.setSupportsArray(true);
		});
		// INTERVAL DAY TO SECOND
		getDbDataTypes().addIntervalDayToSecond(type -> {
			type.setCreateFormat("INTERVAL DAY TO SECOND")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalDayToSecondConverter()))
					.setSupportsArray(true);
		});
		// INTERVAL HOUR TO MINUTE
		getDbDataTypes().addIntervalHourToMinute(type -> {
			type.setCreateFormat("INTERVAL HOUR TO MINUTE")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalHourToMinuteConverter()))
					.setSupportsArray(true);
		});
		// INTERVAL HOUR TO SECOND
		getDbDataTypes().addIntervalHourToSecond(type -> {
			type.setCreateFormat("INTERVAL HOUR TO SECOND")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalHourToSecondConverter()))
					.setSupportsArray(true);
		});

		// INTERVAL MINUTE TO SECOND
		getDbDataTypes().addIntervalMinuteToSecond(type -> {
			type.setCreateFormat("INTERVAL MINUTE TO SECOND")
					.setJdbcTypeHandler(getIntervalConverter(new IntervalMinuteToSecondConverter()))
					.setSupportsArray(true);
		});
		// INET
		getDbDataTypes().addInetType(type -> {
			type.setLiteralPrefix("inet '").setLiteralSuffix("'").setSupportsArray(true);
		});
		// CIDR
		getDbDataTypes().addCidrType(type -> {
			type.setSupportsArray(true);
		});
		// MACADDR
		getDbDataTypes().addMacAddrType(type -> {
			type.setSupportsArray(true);
		});
		// OID
		getDbDataTypes().addRowId("OID", type -> {
			type.setSupportsArray(true);
		});
		// POINT
		getDbDataTypes().addPointType(type -> {
			type.setJdbcTypeHandler(getPointConverter()).setSupportsArray(true);
		});
		// CIRCLE
		getDbDataTypes().addCircleType(type -> {
			type.setJdbcTypeHandler(getCircleConverter()).setSupportsArray(true);
		});
		// BOX
		getDbDataTypes().addBoxType(type -> {
			type.setJdbcTypeHandler(getBoxConverter()).setSupportsArray(true);
		});
		// LINE
		getDbDataTypes().addLineType(type -> {
			type.setJdbcTypeHandler(getLineConverter()).setSupportsArray(true);
		});
		// LSEG
		getDbDataTypes().addLsegType(type -> {
			type.setJdbcTypeHandler(getLsegConverter()).setSupportsArray(true);
		});
		// PATH
		getDbDataTypes().addPathType(type -> {
			type.setJdbcTypeHandler(getPathConverter()).setSupportsArray(true);
		});
		// POLYGON
		getDbDataTypes().addPolygonType(type -> {
			type.setJdbcTypeHandler(getPolygonConverter()).setSupportsArray(true);
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

}
