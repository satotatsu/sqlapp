/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 *
 * sqlapp-core-sqlite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlite is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlite.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlSplitter;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Table;

import static com.sqlapp.util.CommonUtils.*;

import java.util.function.Supplier;

/**
 * SQLite
 * @author satoh
 *
 */
public class Sqlite extends Dialect {
    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 3804986124610623903L;

    protected Sqlite(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
    }

    /**
     * データ型の登録
     */
	@Override
    protected void registerDataType(){
        //VARCHAR
        getDbDataTypes().addVarchar("TEXT", LEN_2GB).setCreateFormat("TEXT");
        //Binary
        getDbDataTypes().addBlob("BLOB", LEN_2GB).setCreateFormat("BLOB")
        	.setLiteral("X'", "'");
        //Boolean
        getDbDataTypes().addBoolean();
        //TinyInt
        getDbDataTypes().addTinyInt("INTEGER");
        //SmallInt
        getDbDataTypes().addSmallInt("INTEGER");
        //Int
        getDbDataTypes().addInt("INTEGER");
        //Int64
        getDbDataTypes().addBigInt("INTEGER");
        //GUID
        getDbDataTypes().addUUID("BLOB");
        //Double
        getDbDataTypes().addDouble("REAL");
        //Decimal
        getDbDataTypes().addNumeric();
	}

    /**
     * DB製品名
     */
    @Override
    public String getProductName() {
        return "SQLite";
    }

    /* (non-Javadoc)
     * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
     */
    @Override
    public  String getSimpleName(){
    	return "sqlite";
    }
    
    /**
     * TOP句のサポート
     */
    @Override
    public boolean supportsTop() {
        return true;
    }

    @Override
    public String getIdentitySelectString() {
        return "select last_insert_rowid()";
    }

    @Override
    public boolean supportsIdentity() {
        return true;
    }

    @Override
    public char getCloseQuote() {
        return ']';
    }

    @Override
    public char getOpenQuote() {
        return '[';
    }

    /**
     * 現在日付の取得関数
     */
    @Override
    public  String getCurrentDateFunction(){
        return null;
    }

    /**
     * 現在日時の取得関数
     */
    @Override
    public  String getCurrentDateTimeFunction(){
        return null;
    }

    /**
     * 現在日時(Timestamp)の取得関数
     */
    @Override
    public String getCurrentTimestampFunction(){
        return null;
    }

    /**
     * 現在日時(Timestamp)タイムゾーン付きの取得関数
     */
    @Override
    public String getCurrentTimestampWithTimeZoneFunction(){
        return null;
    }

    /**
     * 現在日時(Timestamp)タイムゾーン付きの取得関数
     */
    @Override
    public String getCurrentTimeFunction(){
        return null;
    }

    @Override
    public boolean supportsDropCascade(){
        return true;
    }

    @Override
    public  boolean supportsCascadeDelete()
    {
        return true;
    }

    @Override
    public boolean supportsRuleOnDelete(CascadeRule rule){
        return true;
    }

    @Override
    public boolean supportsCascadeUpdate(){
        return true;
    }

    @Override
    public boolean supportsRuleOnUpdate(CascadeRule rule){
        return true;
    }

    @Override
    public boolean supportsDefaultValueFunction() {
        return false;
    }

    public  String selectRecursiveSql(Table table, boolean backTrace){
        return null;
    }

	@Override
    public int hashCode(){
    	return getProductName().hashCode();
    }

    /**
     * 同値判定
     */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		return true;
	}
	
	@Override
	public SqliteSqlSplitter createSqlSplitter(){
		return new SqliteSqlSplitter(this);
	}
}
