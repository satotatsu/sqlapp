/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-symfoware.
 *
 * sqlapp-core-symfoware is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-symfoware is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-symfoware.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.symfoware;
import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.function.Supplier;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.symfoware.metadata.SymfowareCatalogReader;
import com.sqlapp.data.db.metadata.CatalogReader;

public class Symfoware extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2599667727207717949L;

    protected Symfoware(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
    }

	/**
     * データ型の登録
     */
	@Override
    protected void registerDataType(){
    	super.registerDataType();
        //CHAR
        getDbDataTypes().addChar(32000);
        //VARCHAR
        getDbDataTypes().addVarchar(32000);
        //LONG VARCHAR
        getDbDataTypes().addLongVarchar("VARCHAR", LEN_2GB);
        //NCHAR
        getDbDataTypes().addNChar(LEN_2GB);
        //NVARCHAR
        getDbDataTypes().addNVarchar(LEN_2GB);
        //Blob
        getDbDataTypes().addBlob("BLOB", LEN_2GB);
        //Bit
        getDbDataTypes().addBit("SMALLINT", "0");
        //Int16
        getDbDataTypes().addSmallInt();
        //Int32
        getDbDataTypes().addInt("INTEGER");
        //BigInt
        getDbDataTypes().addBigInt("INT8(DECIMAL(20,0))")
        	.setCreateFormat("DECIMAL(19,0)")
        	.setFormats("DECIMAL\\s*\\(\\s*(1[1-9])\\s*,\\s*0\\s*\\)");
        	//UUID
        getDbDataTypes().addUUID().setAsVarcharType();
        //REAL
        getDbDataTypes().addReal();
        //Double
        getDbDataTypes().addDouble();
        //Float
        getDbDataTypes().addFloat(52);
        //Date
        getDbDataTypes().addDate()
        	.setDefaultValueLiteral(getCurrentDateFunction());
        //Time
        getDbDataTypes().addTime()
        	.setDefaultValueLiteral(getCurrentTimeFunction());
        //Timestamp
        getDbDataTypes().addTimestamp()
        	.setDefaultValueLiteral(getCurrentTimestampFunction())
        	.setJdbcTypeHandler(
	        	new DateTimeTypeHandler(DataType.DATETIME.getJdbcType()
	        			, Converters.getDefault().getConverter(java.util.Date.class)));
        //INTERVAL YAER TO MONTH
        getDbDataTypes().addIntervalYearToMonth()
        	.setCreateFormat("INTERVAL YAER TO MONTH");
        //INTERVAL YAER
        getDbDataTypes().addIntervalYear()
        	.setCreateFormat("INTERVAL YAER");
        //INTERVAL MONTH
        getDbDataTypes().addIntervalMonth()
        	.setCreateFormat("INTERVAL MONTH");
        //INTERVAL DAY TO HOUR
        getDbDataTypes().addIntervalDayToHour()
        	.setCreateFormat("INTERVAL DAY TO HOUR");
        //INTERVAL DAY TO MINUTE
        getDbDataTypes().addIntervalDayToMinute()
        	.setCreateFormat("INTERVAL DAY TO MINUTE");
        //INTERVAL DAY TO SECOND
        getDbDataTypes().addIntervalDayToSecond()
        	.setCreateFormat("INTERVAL DAY TO SECOND");
        //INTERVAL DAY
        getDbDataTypes().addIntervalDay()
        	.setCreateFormat("INTERVAL DAY");
        //INTERVAL HOUR TO MINUTE
        getDbDataTypes().addIntervalHourToMinute()
        	.setCreateFormat("INTERVAL HOUR TO MINUTE");
        //INTERVAL HOUR TO SECOND
        getDbDataTypes().addIntervalHourToSecond()
        	.setCreateFormat("INTERVAL HOUR TO SECOND");
        //INTERVAL HOUR
        getDbDataTypes().addIntervalHour()
        	.setCreateFormat("INTERVAL HOUR");
        //INTERVAL MINUTE TO SECOND
        getDbDataTypes().addIntervalMinuteToSecond()
        	.setCreateFormat("INTERVAL MINUTE TO SECOND");
        //INTERVAL MINUTE
        getDbDataTypes().addIntervalMinute()
        	.setCreateFormat("INTERVAL MINUTE");
        //INTERVAL SECOND
        getDbDataTypes().addIntervalMinute()
        	.setCreateFormat("INTERVAL SECOND");
        //Numeric
        getDbDataTypes().addNumeric().setMaxPrecision(18);
	}
	
    /**
     * DB製品名
     */
    @Override
    public String getProductName() {
        return "Symfoware";
    }
    
    /* (non-Javadoc)
     * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
     */
    @Override
    public  String getSimpleName(){
    	return "symfoware";
    }
    
	@Override
    public int hashCode(){
    	return getProductName().hashCode();
    }

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new SymfowareCatalogReader(this);
	}

	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		return true;
	}
}
