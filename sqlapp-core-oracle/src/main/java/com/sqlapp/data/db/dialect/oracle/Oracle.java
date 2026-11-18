/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle;

import static com.sqlapp.data.db.datatype.DataType.CLOB;
import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.Objects;
import java.util.function.Supplier;

import com.sqlapp.data.converter.BooleanConverter;
import com.sqlapp.data.converter.StandardIntervalConverter;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.FloatType;
import com.sqlapp.data.db.datatype.StandardIntervalCodec;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.metadata.OracleCatalogReader;
import com.sqlapp.data.db.dialect.oracle.sql.OracleSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTerminator;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * Oracle固有情報クラス
 * 
 * @author SATOH
 * 
 */
public class Oracle extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 5104596865028027867L;
	/**
	 * システム予約スキーマ。12c以降はDBA_USERS.ORACLE_MAINTAINEDでも判定できるが、ここでは名前だけで判定できる既知の
	 * Oracle管理スキーマを列挙する。
	 */
	private static final String[] SYSTEM_SCHEMA = new String[] { "SYS", "SYSTEM", "SYSMAN", "DBSNMP", "CTXSYS",
			"MDDATA", "MDSYS", "DMSYS", "OLAPSYS", "ORDPLUGINS", "ORDSYS", "ORDDATA", "OUTLN", "SI_INFORMTN_SCHEMA",
			"XDB", "ANONYMOUS", "WMSYS", "EXFSYS", "LBACSYS", "DVSYS", "DVF", "AUDSYS", "APPQOSSYS",
			"GSMADMIN_INTERNAL", "GSMCATUSER", "GSMUSER", "GSMROOTUSER", "GGSYS", "OJVMSYS", "DBSFWUSER",
			"REMOTE_SCHEDULER_AGENT", "SYSBACKUP", "SYSDG", "SYSKM", "SYSRAC", "SYS$UMF", "XS$NULL", "DIP",
			"ORACLE_OCM", "SPATIAL_CSW_ADMIN_USR", "SPATIAL_WFS_ADMIN_USR", "FLOWS_FILES", "APEX_PUBLIC_USER",
			"MGMT_VIEW", "OWBSYS", "OWBSYS_AUDIT", "OPS$ORACLE", "VECSYS" };

	/**
	 * MAX_STRING_SIZE
	 */
	private final OracleMaxStringSize maxStringSize;

	/**
	 * コンストラクタ
	 */
	protected Oracle(final Supplier<Dialect> nextVersionDialectSupplier) {
		this(nextVersionDialectSupplier, OracleMaxStringSize.STANDARD);
	}

	/**
	 * コンストラクタ
	 * 
	 * @param nextVersionDialectSupplier 次のバージョンのDialect
	 * @param maxStringSize              MAX_STRING_SIZE
	 */
	protected Oracle(final Supplier<Dialect> nextVersionDialectSupplier, final OracleMaxStringSize maxStringSize) {
		super(nextVersionDialectSupplier);
		this.maxStringSize = Objects.requireNonNull(maxStringSize, "maxStringSize");
		if (maxStringSize == OracleMaxStringSize.EXTENDED) {
			if (!supportsExtendedMaxStringSize()) {
				throw new IllegalArgumentException(
						"MAX_STRING_SIZE=EXTENDED requires Oracle 12c or later: " + this.getClass().getSimpleName());
			}
			registerExtendedStringTypes();
		}
	}

	/**
	 * MAX_STRING_SIZE=EXTENDEDをサポートするか
	 */
	public boolean supportsExtendedMaxStringSize() {
		return false;
	}

	/**
	 * MAX_STRING_SIZEを返します。
	 * 
	 * @return MAX_STRING_SIZE
	 */
	public OracleMaxStringSize getMaxStringSize() {
		return maxStringSize;
	}

	/**
	 * 同じバージョンで指定したMAX_STRING_SIZEのDialectを返します。
	 * 
	 * @param maxStringSize MAX_STRING_SIZE
	 * @return Dialect
	 * @throws IllegalArgumentException 12c未満でEXTENDEDを指定した場合
	 */
	public Oracle withMaxStringSize(final OracleMaxStringSize maxStringSize) {
		return DialectHolder.getDialect(this, maxStringSize);
	}

	/**
	 * MAX_STRING_SIZE=EXTENDEDのVARCHAR2/NVARCHAR2/RAWを登録します。
	 */
	protected void registerExtendedStringTypes() {
		final OracleMaxStringSize size = OracleMaxStringSize.EXTENDED;
		// VARCHAR
		getDbDataTypes().addVarchar("VARCHAR2", size.getMaxVarcharLength(), type -> {
			type.setSupportCharacterSemantics(CharacterSemantics.Byte, CharacterSemantics.Char);
		});
		// NVARCHAR
		getDbDataTypes().addNVarchar("NVARCHAR2", size.getMaxVarcharLength());
		// VARBINARY
		getDbDataTypes().addVarBinary("RAW", size.getMaxRawLength(), type -> {
			type.setLiteral("HEXTORAW('", "')");
		});
	}

	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(2000, type -> {
			type.setSupportCharacterSemantics(CharacterSemantics.Byte, CharacterSemantics.Char);
		});
		// VARCHAR
		getDbDataTypes().addVarchar("VARCHAR2", 4000, type -> {
			type.setSupportCharacterSemantics(CharacterSemantics.Byte, CharacterSemantics.Char);
		});
		// CLOB
		getDbDataTypes().addClob(LEN_2GB, type -> {
			type.setCreateFormat("CLOB");
		});
		// LONGVARCHAR
		getDbDataTypes().addLongVarchar("LONG", LEN_2GB, type -> {
			type.setDeprecated(getDbDataTypes().getDbType(CLOB));
		});
		// NCHAR
		getDbDataTypes().addNChar(2000);
		// NVARCHAR
		getDbDataTypes().addNVarchar("NVARCHAR2", 4000);
		// NCLOB
		getDbDataTypes().addNClob(LEN_2GB, type -> {
			type.setCreateFormat("NCLOB");
		});
		// BLOB
		getDbDataTypes().addBlob(LEN_2GB, type -> {
			type.setCreateFormat("BLOB").setLiteral("HEXTORAW('", "')");
		});
		// GUID
		getDbDataTypes().addUUID("UUID(RAW(16))", type -> {
			type.setCreateFormat("RAW(16)").setLiteral("HEXTORAW('", "')").setDefaultValueLiteral("SYS_GUID()");
		});
		// VARBINARY
		getDbDataTypes().addVarBinary("RAW", 2000, type -> {
			type.setLiteral("HEXTORAW('", "')");
		});
		// LONGVARBINARY
		getDbDataTypes().addLongVarBinary("LONG RAW", LEN_2GB, type -> {
			type.setLiteral("HEXTORAW('", "')").setDeprecated(getDbDataTypes().getDbType(DataType.BLOB));
		});
		// Bit
		getDbDataTypes().addBoolean("BIT", type -> {
			final BooleanConverter booleanConverter = new BooleanConverter();
			booleanConverter.setFalseString("0");
			booleanConverter.setTrueString("1");
			type.setConverter(booleanConverter);
			type.setCreateFormat("NUMBER(1,0)");
		});
		// TinyInt
		getDbDataTypes().addTinyInt("NUMBER(3,0)", type -> {
			type.clearColumnTypeMatchers();
			type.setCreateFormat("NUMBER(3,0)");
		});
		// SmallInt
		getDbDataTypes().addSmallInt("NUMBER(5,0)", type -> {
			type.clearColumnTypeMatchers();
			type.setCreateFormat("NUMBER(5,0)");
		});
		// MediumInt
		getDbDataTypes().addMediumInt("NUMBER(7,0)", type -> {
			type.clearColumnTypeMatchers();
			type.setCreateFormat("NUMBER(7,0)");
		});
		// Int
		getDbDataTypes().addInt("NUMBER(10,0)", type -> {
			type.clearColumnTypeMatchers();
			type.setCreateFormat("NUMBER(10,0)");
		});
		// BigInt
		getDbDataTypes().addBigInt("NUMBER(19,0)", type -> {
			type.clearColumnTypeMatchers();
			type.setCreateFormat("NUMBER(19,0)");
		});
		// Decimal
		getDbDataTypes().addDecimal("NUMBER", type -> {
			type.setMaxPrecision(38).setDefaultScale(0);
		});
		// Single
		getDbDataTypes().addReal("BINARY_FLOAT", type -> {
		});
		// Double
		getDbDataTypes().addDouble("BINARY_DOUBLE", type -> {
		});
		// Float
		getDbDataTypes().addFloat(126);
		FloatType.class.cast(getDbDataTypes().getDbType(DataType.FLOAT)).setDefaultPrecision(126);
		// DATETIME
		getDbDataTypes().addDateTime("DATE", type -> {
			type.setDefaultValueLiteral(getCurrentDateTimeFunction());
		});
		// TIMESTAMP
		getDbDataTypes().addTimestamp(type -> {
			type.setDefaultValueLiteral(getCurrentTimestampFunction()).setDefaultPrecision(6);
		});
		// TIMESTAMP WITH TIMEZONE
		getDbDataTypes().addTimestampWithTimeZone(type -> {
			type.setDefaultValueLiteral(getCurrentTimestampFunction()).setDefaultPrecision(6).setOctetSize(13);
		});
		// INTERVAL YEAR TO MONTH
		getDbDataTypes().addIntervalYearToMonth(type -> {
			type.setDefaultPrecision(2).setMaxPrecision(9);
			configureInterval(type);
		});
		// INTERVAL DAY TO SECOND
		getDbDataTypes().addIntervalDayToSecond(type -> {
			type.setMaxPrecision(9).setDefaultPrecision(2).setMaxScale(9).setDefaultScale(6);
			configureInterval(type);
		});
		// ROWID
		getDbDataTypes().addRowId(type -> {
		});
		// ANYDATA
		getDbDataTypes().addAnyData("ANYDATA");
		// XMLType
		getDbDataTypes().addSqlXml("XMLType", type -> {
			type.setCreateFormat("XMLType");
		});
		// Indexタイプの設定
		setIndexTypeName("NORMAL", IndexType.BTree);
		setIndexTypeName("FUNCTION-BASED NORMAL", IndexType.Function);
		setIndexTypeName("FUNCTION-BASED BITMAP", IndexType.FunctionBitmap);
		setIndexTypeName("FUNCTION-BASED DOMAIN", IndexType.FunctionDomain);
		setIndexTypeName("IOT - TOP", IndexType.Clustered);
	}

	/**
	 * DB製品名
	 */
	@Override
	public String getProductName() {
		return "Oracle";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
	 */
	@Override
	public String getSimpleName() {
		return "oracle";
	}

	/**
	 * 現在日付(時刻部分を切り捨てたDATE)の取得関数
	 */
	@Override
	public String getCurrentDateFunction() {
		return "TRUNC(CURRENT_DATE)";
	}

	/**
	 * 現在日時(DATE)の取得関数。OracleのDATEは秒までの日時を持つ。
	 */
	@Override
	public String getCurrentDateTimeFunction() {
		return "CURRENT_DATE";
	}

	@Override
	public String getSequenceNextValString(final String sequenceName) {
		if (this.getSelectDummyTableName() != null) {
			return "select " + sequenceName + ".nextval from dual";
		} else {
			return "select " + sequenceName + ".nextval";
		}
	}

	private void configureInterval(DbDataType<?> type) {
		type.setColumnTypeMatcher(new StandardIntervalCodec(type.getDataType(), 2, 6).columnTypeMatcher());
		var converter = new StandardIntervalConverter(type.getDataType());
		type.setConverter(converter).setFormatter(converter)
				.setJdbcTypeHandler(new OracleIntervalJdbcTypeHandler(type.getDataType()));
		String qualifier = type.getDataType().name().substring("INTERVAL_".length()).replace('_', ' ');
		type.setLiteral("INTERVAL '", "' " + qualifier);
	}

	@Override
	public String getSqlValueDefinition(Column column, Object value) {
		var type = column.getDataType();
		if (type == DataType.INTERVAL_YEAR_TO_MONTH || type == DataType.INTERVAL_DAY_TO_SECOND) {
			var handler = (OracleIntervalJdbcTypeHandler) getDbDataTypes().getDbTypeStrict(type).getJdbcTypeHandler();
			return handler.literal(value, column);
		}
		return super.getSqlValueDefinition(column, value);
	}

	@Override
	public boolean supportsSequence() {
		return true;
	}

	@Override
	public boolean supportsWith() {
		return true;
	}

	@Override
	public boolean supportsMerge() {
		return true;
	}

	/**
	 * ROWNUM句のサポート
	 */
	@Override
	public boolean supportsRownum() {
		return true;
	}

	/**
	 * DBカタログのサポート
	 */
	@Override
	public boolean supportsCatalog() {
		return false;
	}

	/**
	 * DBスキーマのサポート
	 */
	@Override
	public boolean supportsSchema() {
		return true;
	}

	@Override
	public boolean supportsFunctionOverload() {
		return false;
	}

	@Override
	public boolean supportsProcedureOverload() {
		return false;
	}

	@Override
	public char getCloseQuote() {
		return '"';
	}

	@Override
	public char getOpenQuote() {
		return '"';
	}

	@Override
	public boolean supportsCascadeDelete() {
		return true;
	}

	@Override
	public boolean supportsDropCascade() {
		return true;
	}

	@Override
	public boolean supportsDefaultValueFunction() {
		return false;
	}

	/**
	 * executeBatch実行時の成功時件数を 個別に返す事が出来るか?
	 */
	@Override
	public boolean supportsBatchExecuteResult() {
		return false;
	}

	@Override
	public boolean supportsRuleOnDelete(final CascadeRule rule) {
		if (rule == CascadeRule.None || rule == CascadeRule.SetNull || rule == CascadeRule.Cascade) {
			return true;
		}
		return false;
	}

	/**
	 * システム予約しているスキーマを返します
	 * 
	 * @return システム予約しているスキーマ
	 */
	@Override
	public String[] getSystemSchema() {
		return SYSTEM_SCHEMA.clone();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new OracleCatalogReader(this);
	}

	/**
	 * 同値判定。同じバージョンでもMAX_STRING_SIZEが異なれば別のDialect。
	 */
	@Override
	public boolean equals(final Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		return this.maxStringSize == ((Oracle) obj).maxStringSize;
	}

	@Override
	public int hashCode() {
		return super.hashCode() * 31 + maxStringSize.ordinal();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.Dialect#createDbOperationFactory()
	 */
	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new OracleSqlFactoryRegistry(this);
	}

	@Override
	public OracleSqlBuilder createSqlBuilder() {
		return new OracleSqlBuilder(this);
	}

	@Override
	public OracleSqlSplitter createSqlSplitter() {
		return new OracleSqlSplitter(this);
	}

	@Override
	public void setChangeAndResetSqlDelimiter(final String sql, final SqlTerminator sqlTerminator) {
		if (!sql.contains(";")) {
			return;
		}
		sqlTerminator.setTerminator("/");
		sqlTerminator.setEndStatementTerminator("/");
	}

	@Override
	public String getSelectDummyTableName() {
		return "DUAL";
	}

	@Override
	public boolean matchDataTypeName(final DataType dataType, final String dataTypeName) {
		if (super.matchDataTypeName(dataType, dataTypeName)) {
			return true;
		}
		// VARCHAR2/NVARCHAR2
		return dataType != null && dataType.isCharacter()
				&& CommonUtils.eqIgnoreCase(dataTypeName, dataType.name() + "2");
	}

	@Override
	public String getTemporaryTableName(final Table table, String prefix, String suffix, boolean witSchema) {
		String name = null;
		if (!CommonUtils.isEmpty(prefix)) {
			name = prefix + table.getName();
		} else {
			name = table.getName();
		}
		if (!CommonUtils.isEmpty(suffix)) {
			name = name + suffix;
		}
		if (witSchema) {
			return getObjectFullName(table.getSchemaName(), name);
		}
		return getObjectFullName(name);
	}

	@Override
	public boolean isTemporaryTableSessionDrop() {
		return false;
	}

	@Override
	public boolean supportsBatchUpdateCount() {
		return false;
	}

	/**
	 * TABLE_NAME AS alias
	 */
	@Override
	public boolean supportsTableNameAlias() {
		return false;
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
