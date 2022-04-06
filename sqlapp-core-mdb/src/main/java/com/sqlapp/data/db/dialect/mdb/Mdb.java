/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 *
 * sqlapp-core-mdb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mdb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mdb.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mdb;

import static com.sqlapp.util.CommonUtils.*;

import java.util.function.Supplier;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
/**
 * Microsoft JET固有情報クラス
 * @author SATOH
 *
 */
public class Mdb extends Dialect {
    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -6002159179591648985L;
	/**
	 * コンストラクタ
	 */
    protected Mdb(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
    }

    /**
     * データ型の登録
     */
	@Override
    protected void registerDataType(){
        //CHAR
        //getDataTypeCollection().addChar(255);
        //VARCHAR
        //getDataTypeCollection().addVarchar(255);
        //LONGVARCHAR
        //getDataTypeCollection().addLongVarchar("LONGTEXT", LEN_1GB)
        //	.setCreateFormat("LONGTEXT");
        //NCHAR
        getDbDataTypes().addNChar(255).setLiteral("'", "'");
        //NVARCHAR
        getDbDataTypes().addNVarchar("TEXT", LEN_1GB).setLiteral("'", "'");
        //LONGNVARCHAR
        getDbDataTypes().addLongNVarchar("LONGTEXT", LEN_1GB)
        .setLiteral("'", "'")
        	.setCreateFormat("LONGTEXT")
	    	.addFormats("MEMO");
        //NCLOB
        getDbDataTypes().addNClob("LONGTEXT", LEN_1GB);
        //BLOB
        getDbDataTypes().addBlob("IMAGE", LEN_2GB)
        	.setCreateFormat("IMAGE")
        	.setFormats("IMAGE")
        	.setLiteral("0x", "");
        //Boolean
        getDbDataTypes().addBit()
        	.addFormats("BOOLEAN")
        	.addFormats("LOGICAL")
        	.addFormats("LOGICAL1")
	    	.addFormats("YESNO");
        //SByte
        getDbDataTypes().addTinyInt("BYTE");
        //Int16
        getDbDataTypes().addSmallInt("SHORT")
        	.addFormats("INTEGER2")
        	.addFormats("SMALLINT");
        //Int32
        getDbDataTypes().addInt("LONG")
        	.addFormats("INT")
        	.addFormats("INTEGER")
	    	.addFormats("INTEGER4");
        //Int64
        getDbDataTypes().addBigInt("BIGINT")
        	.setCreateFormat("DECIMAL(19,0)")
    		.setFormats("DECIMAL\\s*\\(\\s*19\\s*,\\s*0\\s*\\)");
        //Serial
        getDbDataTypes().addSerial("AUTOINCREMENT");
        //GUID
        getDbDataTypes().addUUID("UNIQUEIDENTIFIER").setLiteral("'", "'")
        	.setDefaultValueLiteral("NEWID()");
        //Single
        getDbDataTypes().addReal("SINGLE")
        	.addFormats("FLOAT4")
        	.addFormats("IEEESINGLE")
        	.addFormats("REAL");
        //Double
        getDbDataTypes().addDouble("DOUBLE")
        	.addFormats("FLOAT")
        	.addFormats("FLOAT8")
        	.addFormats("IEEEDOUBLE")
        	.addFormats("NUMBER")
        	.addFormats("NUMERIC");
        //Date
        getDbDataTypes().addDateTime("DATETIME").setLiteral("#", "#")
        	.setJdbcTypeHandler(
        	new DateTimeTypeHandler(DataType.DATETIME.getJdbcType()
        			, Converters.getDefault().getConverter(java.util.Date.class)));
        //Time
        getDbDataTypes().addTime("TIME").setLiteral("#", "#");
        //Money
        getDbDataTypes().addMoney("CURRENCY").addFormats("MONEY");
        //Decimal
        getDbDataTypes().addDecimal()
        	.setMaxPrecision(28).setDefaultPrecision(15).setDefaultScale(0);
        //Numeric
        getDbDataTypes().addNumeric()
        	.setMaxPrecision(28).setDefaultPrecision(15).setDefaultScale(0);
	}

    /**
     * DB名
     */
    @Override
    public String getProductName() {
        return "MS Jet";
    }
    
    /* (non-Javadoc)
     * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
     */
    @Override
    public  String getSimpleName(){
    	return "msjet";
    }
    
    /**
     * TOP句のサポート
     */
    @Override
    public  boolean supportsTop() {
        return true;
    }

    @Override
    public String getIdentitySelectString() {
        return "select @@IDENTITY";
    }

    @Override
    public boolean supportsIdentity()
    {
        return true;
    }

    @Override
    public boolean supportsDefaultValueFunction()
    {
        return false;
    }

    @Override
    public char getCloseQuote() {
        return ']';
    }

    @Override
    public char getOpenQuote() {
        return '[';
    }

    @Override
    public boolean supportsDropCascade(){
        return true;
    }

    /**
     * インデックス名のテーブルスコープ
     */
    public boolean supportsIndexNameTableScope(){
        return true;
    }

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.DbDialect#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		return true;
	}
}
