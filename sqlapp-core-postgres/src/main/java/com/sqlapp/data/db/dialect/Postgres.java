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
package com.sqlapp.data.db.dialect;

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
import com.sqlapp.data.db.converter.postgres.FromPGCircleConverter;
import com.sqlapp.data.db.converter.postgres.FromPGIntervalConverter;
import com.sqlapp.data.db.converter.postgres.FromPGBoxConverter;
import com.sqlapp.data.db.converter.postgres.FromPGLineConverter;
import com.sqlapp.data.db.converter.postgres.FromPGLsegConverter;
import com.sqlapp.data.db.converter.postgres.FromPGPathConverter;
import com.sqlapp.data.db.converter.postgres.FromPGPointConverter;
import com.sqlapp.data.db.converter.postgres.FromPGPolygonConverter;
import com.sqlapp.data.db.converter.postgres.ToPGCircleConverter;
import com.sqlapp.data.db.converter.postgres.ToPGIntervalConverter;
import com.sqlapp.data.db.converter.postgres.ToPGLineConverter;
import com.sqlapp.data.db.converter.postgres.ToPGBoxConverter;
import com.sqlapp.data.db.converter.postgres.ToPGLsegConverter;
import com.sqlapp.data.db.converter.postgres.ToPGPathConverter;
import com.sqlapp.data.db.converter.postgres.ToPGPointConverter;
import com.sqlapp.data.db.converter.postgres.ToPGPolygonConverter;
import com.sqlapp.data.db.datatype.DefaultJdbcTypeHandler;
import com.sqlapp.data.db.datatype.JdbcTypeHandler;
import com.sqlapp.data.db.datatype.NumericType;
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
	protected Postgres(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(32672);
		// VARCHAR
		getDbDataTypes().addVarchar(32672);
		getDbDataTypes().addVarchar("TEXT", LEN_1GB).setFormats("TEXT\\s*")
				.setCreateFormat("TEXT").setFixedLength(true).setDefaultLength(LEN_1GB);
		// CLOB
		// getDataTypes().addClob("TEXT", LEN_1GB).setCreateFormat("TEXT");
		// BLOB
		getDbDataTypes().addBlob("BYTEA", LEN_1GB).setCreateFormat("BYTEA").setLiteral("decode('", "', 'hex')");
		// Boolean
		getDbDataTypes().addBoolean("BOOL");
		// BINARY
		getDbDataTypes().addBinary("BIT", LEN_1GB).setLiteral("decode('", "', 'hex')");
		// VARBINARY
		getDbDataTypes().addVarBinary("VARBIT", LEN_1GB).setLiteral("decode('", "', 'hex')");
		// Int16
		getDbDataTypes().addSmallInt().addFormats("INT2");
		// Int32
		getDbDataTypes().addInt().addFormats("INT4").addFormats("INTEGER");
		// Int64
		getDbDataTypes().addBigInt().addFormats("INT8");
		// Serial
		getDbDataTypes().addSerial("SERIAL");
		// BigSerial
		getDbDataTypes().addBigSerial("BIGSERIAL");
		// Numeric
		getDbDataTypes().addNumeric().setMaxPrecision(1000).setMaxScale(1000);
		// GUID
		getDbDataTypes().addUUID("UUID").setLiteral("{", "}");
		// Single
		getDbDataTypes().addReal("FLOAT4");
		// Double
		getDbDataTypes().addDouble().addFormats("DOUBLE PRECISION").addFormats("FLOAT8");
		// Money
		getDbDataTypes()
				.addMoney("MONEY")
				.setLiteral("", "::text::money")
				.setSurrogateType(
						new NumericType().setMaxPrecision(17).setScale(2)).setFixedPrecision(false).setFixedScale(false);
		// XML
		getDbDataTypes().addSqlXml("XML").setLiteral("XML '", "'");
		// SmallDateTime
		getDbDataTypes().addSmallDateTime("abstime").setDefaultValueLiteral(
				getCurrentDateFunction());
		// Date
		getDbDataTypes().addDate().setDefaultValueLiteral(
				getCurrentDateFunction());
		// Time
		getDbDataTypes().addTime().setDefaultValueLiteral(
				getCurrentTimeFunction());
		// Time With Time Zone
		getDbDataTypes().addTimeWithTimeZone("TIMETZ").setDefaultPrecision(6)
				.setDefaultValueLiteral(getCurrentTimeFunction());
		// Timestamp
		getDbDataTypes().addTimestamp().setDefaultValueLiteral(
				getCurrentTimestampFunction());
		// Timestamp With Time Zone
		getDbDataTypes().addTimestampWithTimeZoneType("TIMESTAMPTZ")
				.setDefaultPrecision(6)
				.setDefaultValueLiteral(getCurrentTimestampFunction());
		// INTERVAL
		getDbDataTypes().addInterval();
		// INTERVAL YAER
		getDbDataTypes()
				.addIntervalYear()
				.setCreateFormat("INTERVAL YAER")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalYearConverter()));
		// INTERVAL MONTH
		getDbDataTypes()
				.addIntervalMonth()
				.setCreateFormat("INTERVAL MONTH")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalMonthConverter()));
		// INTERVAL DAY
		getDbDataTypes()
				.addIntervalDay()
				.setCreateFormat("INTERVAL DAY")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalDayConverter()));
		// INTERVAL HOUR
		getDbDataTypes()
				.addIntervalHour()
				.setCreateFormat("INTERVAL HOUR")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalHourConverter()));
		// INTERVAL MINUTE
		getDbDataTypes()
				.addIntervalMinute()
				.setCreateFormat("INTERVAL MINUTE")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalMinuteConverter()));
		// INTERVAL SECOND
		getDbDataTypes()
				.addIntervalSecond()
				.setCreateFormat("INTERVAL SECOND")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalSecondConverter()));
		// INTERVAL YAER TO MONTH
		getDbDataTypes()
				.addIntervalYearToMonth()
				.setCreateFormat("INTERVAL YAER TO MONTH")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalYearToMonthConverter()));
		// INTERVAL DAY TO HOUR
		getDbDataTypes()
				.addIntervalDayToHour()
				.setCreateFormat("INTERVAL DAY TO HOUR")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalDayToHourConverter()));
		// INTERVAL DAY TO MINUTE
		getDbDataTypes()
				.addIntervalDayToMinute()
				.setCreateFormat("INTERVAL DAY TO MINUTE")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalDayToMinuteConverter()));
		// INTERVAL DAY TO SECOND
		getDbDataTypes()
				.addIntervalDayToSecond()
				.setCreateFormat("INTERVAL DAY TO SECOND")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalDayToSecondConverter()));
		// INTERVAL HOUR TO MINUTE
		getDbDataTypes()
				.addIntervalHourToMinute()
				.setCreateFormat("INTERVAL HOUR TO MINUTE")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalHourToMinuteConverter()));
		// INTERVAL HOUR TO SECOND
		getDbDataTypes()
				.addIntervalHourToSecond()
				.setCreateFormat("INTERVAL HOUR TO SECOND")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalHourToSecondConverter()));
		// INTERVAL MINUTE TO SECOND
		getDbDataTypes()
				.addIntervalMinuteToSecond()
				.setCreateFormat("INTERVAL MINUTE TO SECOND")
				.setJdbcTypeHandler(
						getIntervalConverter(new IntervalMinuteToSecondConverter()));
		// INET
		getDbDataTypes().addInetType().setLiteralPrefix("inet '").setLiteralSuffix("'");
		// CIDR
		getDbDataTypes().addCidrType();
		// MACADDR
		getDbDataTypes().addMacAddrType();
		//OID
		getDbDataTypes().addRowId("OID");
		//POINT
		getDbDataTypes().addPointType()
				.setJdbcTypeHandler(getPointConverter());
		//CIRCLE
		getDbDataTypes().addCircleType()
				.setJdbcTypeHandler(getCircleConverter());
		//BOX
		getDbDataTypes().addBoxType()
				.setJdbcTypeHandler(getBoxConverter());
		//LINE
		getDbDataTypes().addLineType()
				.setJdbcTypeHandler(getLineConverter());
		//LSEG
		getDbDataTypes().addLsegType()
				.setJdbcTypeHandler(getLsegConverter());
		//PATH
		getDbDataTypes().addPathType()
				.setJdbcTypeHandler(getPathConverter());
		//POLYGON
		getDbDataTypes().addPolygonType()
			.setJdbcTypeHandler(getPolygonConverter());
		//
	}

	/**
	 * Postgres固有のIntervalのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getIntervalConverter(Converter<?> resultSetConveter) {
		DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(
				java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(new PipeConverter(
				new FromPGIntervalConverter(), resultSetConveter));
		converter.setStatementConverter(new ToPGIntervalConverter());
		return converter;
	}

	/**
	 * Postgres固有のPointのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getPointConverter() {
		DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(
				java.sql.JDBCType.OTHER);
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
		DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(
				java.sql.JDBCType.OTHER);
		converter.setResultSetconverter(
				new FromPGCircleConverter());
		converter.setStatementConverter(new ToPGCircleConverter());
		return converter;
	}

	/**
	 * Postgres固有のCircleのコンバータを取得するためのメソッド
	 * 
	 * @param resultSetConveter
	 */
	private JdbcTypeHandler getBoxConverter() {
		DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(
				java.sql.JDBCType.OTHER);
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
		DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(
				java.sql.JDBCType.OTHER);
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
		DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(
				java.sql.JDBCType.OTHER);
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
		DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(
				java.sql.JDBCType.OTHER);
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
		DefaultJdbcTypeHandler converter = new DefaultJdbcTypeHandler(
				java.sql.JDBCType.OTHER);
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
	public String getSequenceNextValString(String sequenceName) {
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
	public boolean supportsRuleOnDelete(CascadeRule rule) {
		return true;
	}

	@Override
	public boolean supportsCascadeUpdate() {
		return true;
	}

	@Override
	public boolean supportsRuleOnUpdate(CascadeRule rule) {
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
	public String nativeCaseString(String value) {
		if (isEmpty(value)) {
			return value;
		}
		if (isQuoted(value)) {
			return value;
		}
		return value.toLowerCase();
	}

	public String selectRecursiveSql(Table table, boolean backTrace) {
		return null;
	}

	/**
	 * 同値判定
	 */
	@Override
	public boolean equals(Object obj) {
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
	protected SqlFactoryRegistry createSqlFactoryRegistry() {
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
	public PostgresSqlBuilder createSqlBuilder(){
		return new PostgresSqlBuilder(this);
	}
	
	@Override
	public PostgresSqlSplitter createSqlSplitter(){
		return new PostgresSqlSplitter(this);
	}

	@Override
	protected String doQuote(String target){
		StringBuilder builder = new StringBuilder(target.length() + 2);
		builder.append(getOpenQuote()).append(target.replace("\"", "\"\"")).append(getCloseQuote());
		return builder.toString();
	}
	
	@Override
	public PostgresJdbcHandler createJdbcHandler(SqlNode sqlNode){
		PostgresJdbcHandler jdbcHandler=new PostgresJdbcHandler(sqlNode);
		return jdbcHandler;
	}
	
	@Override
	public boolean isDdlRollbackable(){
		return true;
	}

}
