/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.saphana;

import static com.sqlapp.util.CommonUtils.LEN_2GB;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.saphana.metadata.SapHanaCatalogReader;
import com.sqlapp.data.db.dialect.saphana.sql.SapHanaSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlSplitter;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
/**
 * SAP HANA固有情報クラス
 * @author SATOH
 *
 */
public class SapHana extends Dialect {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2868407251415226663L;

	/**
	 * コンストラクタ
	 */
    protected SapHana(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
    }

    /**
     * データ型の登録
     */
	@Override
    protected void registerDataType(){
        //VARCHAR
		getDbDataTypes().addVarchar(5000);
        //NVARCHAR
        getDbDataTypes().addNVarchar(5000).setLiteral("N'", "'");
        //SHORTTEXT
        getDbDataTypes().addSearchableShortText("SHORTTEXT", 5000).setLiteral("N'", "'");
        //CLOB
        getDbDataTypes().addClob("CLOB", LEN_2GB);
        //NCLOB
        getDbDataTypes().addNClob("NCLOB", LEN_2GB).setLiteral("N'", "'");
        //TEXT
        getDbDataTypes().addSearchableText("TEXT", LEN_2GB).setLiteral("N'", "'");
        //ALPHANUM
        getDbDataTypes().addAlphanum(127);
        //VARBINARY
        getDbDataTypes().addVarBinary(5000).setLiteral("X'", "'");
        //BLOB
        getDbDataTypes().addBlob("BLOB", LEN_2GB).setLiteral("X'", "'");
        //SByte
        getDbDataTypes().addTinyInt();
        //Int16
        getDbDataTypes().addSmallInt();
        //Int32
        getDbDataTypes().addInt("INTEGER");
        //Int64
        getDbDataTypes().addBigInt();
        //Single
        getDbDataTypes().addReal("REAL");
        //Double
        getDbDataTypes().addDouble("DOUBLE");
        //Decimal
        getDbDataTypes().addDecimal();
        //SmallDecimal
        getDbDataTypes().addDecimalFloat("SMALLDECIMAL");
        //Date
        getDbDataTypes().addDate();
        //DateTime
        getDbDataTypes().addDateTime("SECONDDATE");
        //TIMESTAMP
        getDbDataTypes().addTimestamp();
        //Time
        getDbDataTypes().addTime();
	}

    /**
     * DB名
     */
    @Override
    public String getProductName() {
        return "SAP HANA";
    }
    
    /* (non-Javadoc)
     * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
     */
    @Override
    public  String getSimpleName(){
    	return "hana";
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
        return null;
    }

    @Override
    public boolean supportsIdentity(){
        return true;
    }

    @Override
    public boolean supportsDefaultValueFunction(){
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
    public boolean supportsDropCascade(){
        return true;
    }

    /**
     * インデックス名のテーブルスコープ
     */
    @Override
	public boolean supportsIndexNameTableScope(){
        return true;
    }

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new SapHanaCatalogReader(this);
	}
    
	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.DbDialect#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj){
		if (!super.equals(obj)){
			return false;
		}
		return true;
	}

	@Override
    public boolean recommendsNTypeChar(){
        return true;
    }
    
	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new SapHanaSqlFactoryRegistry(this);
	}
	
	@Override
	public SapHanaSqlBuilder createSqlBuilder(){
		return new SapHanaSqlBuilder(this);
	}
	
	
	@Override
	public SapHanaSqlSplitter createSqlSplitter(){
		return new SapHanaSqlSplitter(this);
	}
}
