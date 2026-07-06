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

import java.util.function.Supplier;

import com.sqlapp.data.converter.BooleanConverter;
import com.sqlapp.data.db.datatype.DataType;
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
	 * システム予約スキーマ
	 */
	private final String[] SYSTEM_SCHEMA = new String[] { "SYS" // データベース管理タスクを実行するために使用するアカウント
			, "SYSTEM" // データベース管理タスクを実行するために使用するもう1つのアカウント
			, "SYSMAN" // Oracle Enterprise
						// Managerのデータベース管理タスクを実行するために使用するアカウント。SYSおよびSYSTEMもこれらのタスクを実行できる。
			, "DBSNMP" // Oracle Enterprise ManagerのコンポーネントであるManagement
						// Agentで、データベースの監視および管理に使用する
			, "CTXSYS" // Oracle Textのアカウント
			, "MDDATA" // ジオコーダおよびルーター・データを格納するためにOracle Spatialが使用するスキーマ
			, "MDSYS" // Oracle SpatialおよびOracle interMedia Locatorの管理者アカウント
			, "DMSYS" // データ・マイニング・アカウント
			, "OLAPSYS" // OLAPメタデータ構造体の作成に使用するアカウント
			, "ORDPLUGINS" // Oracle
							// interMediaのユーザー。Oracle提供のプラグインやサード・パーティのフォーマット・プラグインは、このスキーマにインストールされる。
			, "ORDSYS" // Oracle interMediaの管理者アカウント
			, "OUTLN" // プラン・スタビリティをサポートするアカウント。プラン・スタビリティによって、同じSQL文に対して同じ実行計画を保持できる。
			, "SI_INFORMTN_SCHEMA" // SQL/MM Still Image規格の情報ビューを格納するアカウント
	};

	/**
	 * コンストラクタ
	 */
	protected Oracle(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
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
			type.setCreateFormat("RAW(16)").setLiteral("HEXTORAW('", "')").setDefaultValueLiteral("NEW_GUID()");
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
		getDbDataTypes().addTinyInt("NUMBER(2,0)", type -> {
			type.setCreateFormat("NUMBER(2,0)");
		});
		// SmallInt
		getDbDataTypes().addSmallInt("NUMBER(5,0)", type -> {
			type.setCreateFormat("NUMBER(5,0)");
		});
		// MediumInt
		getDbDataTypes().addMediumInt("NUMBER(7,0)", type -> {
			type.setCreateFormat("NUMBER(7,0)");
		});
		// Int
		getDbDataTypes().addInt("NUMBER(9,0)", type -> {
			type.setCreateFormat("NUMBER(9,0)");
		});
		// BigInt
		getDbDataTypes().addBigInt("NUMBER(19,0)", type -> {
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
		// INTERVAL YAER TO MONTH
		getDbDataTypes().addIntervalYearToMonth(type -> {
			type.setDefaultPrecision(2);
		});
		// INTERVAL DAY TO SECOND
		getDbDataTypes().addIntervalDayToSecond(type -> {
			type.setMaxPrecision(9).setDefaultPrecision(2).setMaxScale(9).setDefaultScale(6);
		});
		// ROWID
		getDbDataTypes().addRowId(type -> {
		});
		// ANYDATA
		getDbDataTypes().addAnyData("ANYDATA");
		// Single
		getDbDataTypes().addSqlXml("XMLType", type -> {
		});
		;
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

	@Override
	public String getSequenceNextValString(final String sequenceName) {
		if (this.getSelectDummyTableName() != null) {
			return "select " + sequenceName + ".nextval from dual";
		} else {
			return "select " + sequenceName + ".nextval";
		}
	}

	@Override
	public boolean supportsSequence() {
		return true;
	}

	@Override
	public boolean supportsWith() {
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
		return SYSTEM_SCHEMA;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#hashCode()
	 */
	@Override
	public int hashCode() {
		return getProductName().hashCode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#equals(java.lang.Object)
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
		return new OracleCatalogReader(this);
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
		if (!super.matchDataTypeName(dataType, dataTypeName)) {
			if (dataType != null && dataType.isCharacter()
					&& CommonUtils.eqIgnoreCase(dataTypeName, dataType.name() + "2")) {
				return true;
			}
			return true;
		} else {
			return true;
		}
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
		return getObjectFullName(table.getSchemaName(), name);
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

}
