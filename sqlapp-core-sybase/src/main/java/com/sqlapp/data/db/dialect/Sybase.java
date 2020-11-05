/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect;

import static com.sqlapp.data.db.datatype.DataType.CHAR;
import static com.sqlapp.data.db.datatype.DataType.DATETIME;
import static com.sqlapp.data.db.datatype.DataType.NCHAR;
import static com.sqlapp.data.db.datatype.DataType.NVARCHAR;
import static com.sqlapp.data.db.datatype.DataType.SMALLDATETIME;
import static com.sqlapp.data.db.datatype.DataType.VARCHAR;
import static com.sqlapp.util.CommonUtils.LEN_1GB;
import static com.sqlapp.util.CommonUtils.LEN_2GB;
import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.size;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.sybase.metadata.SybaseCatalogReader;
import com.sqlapp.data.db.dialect.sybase.sql.SybaseSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.sybase.util.SybaseSqlBuilder;
import com.sqlapp.data.db.dialect.sybase.util.SybaseSqlSplitter;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Column;

public class Sybase extends Dialect{
    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -1069234810830978752L;

    protected Sybase(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
    }
    
	/**
	 * データ型の登録
	 */
	@Override
	protected void registerDataType() {
		// CHAR
		getDbDataTypes().addChar(8000);
		// VARCHAR
		getDbDataTypes().addVarchar(8000);
		// LONGVARCHAR
		getDbDataTypes().addLongVarchar("TEXT", LEN_2GB - 1)
				.setCreateFormat("TEXT").setFormats("NTEXT");
		// NCHAR
		getDbDataTypes().addNChar(4000);
		// NVARCHAR
		getDbDataTypes().addNVarchar(4000);
		// LONGVARCHAR
		getDbDataTypes().addLongVarchar("NTEXT", LEN_1GB - 1)
				.setCreateFormat("NTEXT").setFormats("NTEXT")
				.addFormats("NATIONAL\\s+TEXT");
		// BINARY
		getDbDataTypes().addBinary(8000).setLiteral("0x", "");
		// VARBINARY
		getDbDataTypes().addVarBinary(8000).setLiteral("0x", "");
		// BLOB
		getDbDataTypes().addBlob("IMAGE", LEN_2GB - 1).setCreateFormat("IMAGE")
				.setFormats("IMAGE").setLiteral("0x", "");
		// Bit
		getDbDataTypes().addBit();
		// SByte
		getDbDataTypes().addTinyInt().addFormats("TINYINT IDENTITY");
		// SMALLINT
		getDbDataTypes().addSmallInt().addFormats("SMALLINT IDENTITY");
		// INT
		getDbDataTypes().addInt().addFormats("INT IDENTITY");
		// Int64
		getDbDataTypes().addBigInt().addFormats("BIGINT IDENTITY");
		// GUID
		getDbDataTypes().addUUID("UNIQUEIDENTIFIER").setLiteral("'", "'")
				.setDefaultValueLiteral("NEWID()");
		// Single
		getDbDataTypes().addReal();
		// Single
		getDbDataTypes().addFloat(53);
		// SmallDateTime
		getDbDataTypes().addSmallDateTime().setLiteral("{ts '", "'}")
				.setCreateFormat("SMALLDATETIME")
				.setDefaultValueLiteral(getCurrentDateTimeFunction());
		// DateTime
		getDbDataTypes().addDateTime().setLiteral("{ts '", "'}")
				.setCreateFormat("DATETIME")
				.setDefaultValueLiteral(getCurrentDateTimeFunction());
		// SmallMoney
		getDbDataTypes().addSmallMoney("SMALLMONEY");
		// Money
		getDbDataTypes().addMoney("MONEY");
		// Decimal
		getDbDataTypes().addDecimal().setMaxPrecision(38)
				.setDefaultPrecision(19).setDefaultScale(5)
				.addPrecisionScaleFormat("DEC");
		// Numeric
		getDbDataTypes().addNumeric().setMaxPrecision(38)
				.setDefaultPrecision(19).setDefaultScale(5);
		// 行バージョン型
		getDbDataTypes().addRowVersion("TIMESTAMP").setLiteral("0x", "");
		// SYSNAME型
		getDbDataTypes().addSqlIdentifierType("SYSNAME");
		//ANYDATA
		getDbDataTypes().addAnyData("VARIANT");
		// 推奨される型の登録
		getDbDataTypes().registerRecommend(CHAR, NVARCHAR);
		getDbDataTypes().registerRecommend(NCHAR, NVARCHAR);
		getDbDataTypes().registerRecommend(VARCHAR, NVARCHAR);
		getDbDataTypes().registerRecommend(SMALLDATETIME, DATETIME);
	}

    
    /**
     * DB製品名
     */
    @Override
    public String getProductName() {
        return "SQL Server";
    }
    
    /* (non-Javadoc)
     * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
     */
    @Override
    public  String getSimpleName(){
    	return "sybase";
    }
    
	@Override
    public int hashCode(){
    	return getProductName().hashCode();
    }

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.Dialect#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
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
		return new SybaseCatalogReader(this);
	}

	@Override
	public String getObjectFullName(final String catalogName,
			final String schemaName, final String objectName) {
		StringBuilder builder = new StringBuilder(size(catalogName)
				+ size(schemaName) + size(objectName) + 2);
		if (!isEmpty(catalogName)) {
			builder.append(catalogName);
			builder.append('.');
			if (!isEmpty(schemaName)) {
				builder.append(schemaName);
			}
			builder.append('.');
		} else {
			if (!isEmpty(schemaName)) {
				builder.append(schemaName);
				builder.append('.');
			}
		}
		builder.append(objectName);
		return builder.toString();
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.Dialect#isOptimisticLockColumn(com.sqlapp.data.schemas.Column)
	 */
	@Override
	public boolean isOptimisticLockColumn(Column column) {
		if (column.getDataType().isBinary()
				&& column.getName().equalsIgnoreCase("TIMESTAMP")) {
			return true;
		}
		return super.isOptimisticLockColumn(column);
	}

	@Override
	protected SqlFactoryRegistry createSqlFactoryRegistry() {
		return new SybaseSqlFactoryRegistry(this);
	}
	
	@Override
	public SybaseSqlBuilder createSqlBuilder(){
		return new SybaseSqlBuilder(this);
	}
	
	@Override
	public SybaseSqlSplitter createSqlSplitter(){
		return new SybaseSqlSplitter(this);
	}
	
	@Override
	protected String doQuote(String target){
		StringBuilder builder = new StringBuilder(target.length() + 2);
		builder.append(getOpenQuote()).append(target.replace("]", "]]")).append(getCloseQuote());
		return builder.toString();
	}
	

	@Override
	public void setChangeAndResetSqlDelimiter(SqlOperation operation){
		if (!operation.getSqlText().contains(";")){
			return;
		}
		operation.setTerminator("GO");
		operation.setEndStatementTerminator("GO");
	}

	@Override
	public boolean isDdlRollbackable(){
		return true;
	}
}
