/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-hirdb.
 *
 * sqlapp-core-hirdb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hirdb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hirdb.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.hirdb;

import static com.sqlapp.util.CommonUtils.*;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
/**
 * HiRDB
 * @author satoh
 *
 */
public class Hirdb extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2599667727207717949L;

	private static final int CHAR_SIZE_MAX=30000;
	private static final int VARCHAR_SIZE_MAX=32000;
	private static final long SIZE_MAX=LEN_2GB-1;

    protected Hirdb(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
    }

    /**
     * データ型の登録
     */
	@Override
    protected void registerDataType(){
        //CHAR
        getDbDataTypes().addChar(CHAR_SIZE_MAX);
        //VARCHAR
        getDbDataTypes().addVarchar(VARCHAR_SIZE_MAX);
        //NCHAR
        getDbDataTypes().addNChar(CHAR_SIZE_MAX/2);
        //NVARCHAR
        getDbDataTypes().addNVarchar(VARCHAR_SIZE_MAX/2);
        //LONG NVARCHAR
        getDbDataTypes().addLongVarchar("NVARCHAR", VARCHAR_SIZE_MAX/2);
        //MCHAR
        getDbDataTypes().addMChar(CHAR_SIZE_MAX);
        //MVARCHAR
        getDbDataTypes().addMVarchar(VARCHAR_SIZE_MAX);
        //BINARY
        getDbDataTypes().addBinary(SIZE_MAX);
        //BLOB
        getDbDataTypes().addBlob("BLOB", SIZE_MAX).setCreateFormat("BLOB")
        	.setLiteral("X'", "'");
        //Boolean(関数の戻り値としてしか使えない)
        //Bit
        getDbDataTypes().addBit("BIT").setCreateFormat("NUMBER(1,0)");
        //Int16
        getDbDataTypes().addSmallInt();
        //Int32
        getDbDataTypes().addInt("INTEGER");
        //Int64
        getDbDataTypes().addBigInt();
        //GUID
        getDbDataTypes().addUUID().setAsVarcharType();
        //XML
        getDbDataTypes().addSqlXml("XML").setMaxLength(2147483647)
            	.addFormats("XML\\s*\\(\\s*[^\\s]+(AS\\s+BINARY)\\s*\\)\\s*");
        //Single
        getDbDataTypes().addReal().addFormats("SMALLFLT");
        //Double
        getDbDataTypes().addDouble().addFormats("FLOAT");
        //Date
        getDbDataTypes().addDate().setLiteral("'", "'")
    		.setDefaultValueLiteral(getCurrentDateFunction());
        //Time
        getDbDataTypes().addTime().setLiteral("'", "'")
        	.setDefaultValueLiteral(getCurrentTimeFunction());
        //Timestamp
        getDbDataTypes().addTimestamp().setLiteral("'", "'")
        	.setDefaultValueLiteral(getCurrentTimestampFunction())
        	.setMaxPrecision(6);
        //INTERVAL YAER TO DAY
        getDbDataTypes().addIntervalYearToDay().setLiteral("", "");
        //INTERVAL HOUR TO SECOND
        getDbDataTypes().addIntervalHourToSecond().setLiteral("", "");
        //Decimal
        getDbDataTypes().addDecimal()
			.setMaxPrecision(38).setDefaultPrecision(15).setDefaultScale(0);
        //Numeric
        getDbDataTypes().addNumeric()
		.setMaxPrecision(38).setDefaultPrecision(15).setDefaultScale(0);
	}

    /**
     * DB製品名
     */
    @Override
    public String getProductName() {
        return "HiRDB";
    }

    /* (non-Javadoc)
     * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
     */
    @Override
    public  String getSimpleName(){
    	return "hirdb";
    }
    
	@Override
    public int hashCode(){
    	return getProductName().hashCode();
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
