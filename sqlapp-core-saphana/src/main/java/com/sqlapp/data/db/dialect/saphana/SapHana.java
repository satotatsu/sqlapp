/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
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
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * SAP HANA固有情報クラス
 * 
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
	protected void registerDataType() {
		// VARCHAR
		getDbDataTypes().addVarchar(5000);
		// NVARCHAR
		getDbDataTypes().addNVarchar(5000, type -> {
			type.setLiteral("N'", "'");
		});
		// SHORTTEXT
		getDbDataTypes().addSearchableShortText("SHORTTEXT", 5000, type -> {
			type.setLiteral("N'", "'");
		});
		// CLOB
		getDbDataTypes().addClob("CLOB", LEN_2GB);
		// NCLOB
		getDbDataTypes().addNClob("NCLOB", LEN_2GB, type -> {
			type.setLiteral("N'", "'");
		});
		// TEXT
		getDbDataTypes().addSearchableText("TEXT", LEN_2GB, type -> {
			type.setLiteral("N'", "'");
		});
		// ALPHANUM
		getDbDataTypes().addAlphanum(127);
		// VARBINARY
		getDbDataTypes().addVarBinary(5000, type -> {
			type.setLiteral("X'", "'");
		});
		// BLOB
		getDbDataTypes().addBlob("BLOB", LEN_2GB, type -> {
			type.setLiteral("X'", "'");
		});
		// SByte
		getDbDataTypes().addTinyInt(type -> {
		});
		// Int16
		getDbDataTypes().addSmallInt(type -> {
		});
		// Int32
		getDbDataTypes().addInt("INTEGER", type -> {
		});
		// Int64
		getDbDataTypes().addBigInt(type -> {
		});
		// Single
		getDbDataTypes().addReal("REAL", type -> {
		});
		// Double
		getDbDataTypes().addDouble(type -> {
		});
		// Decimal
		getDbDataTypes().addDecimal(type -> {
		});
		// SmallDecimal
		getDbDataTypes().addDecimalFloat("SMALLDECIMAL");
		// Date
		getDbDataTypes().addDate(type -> {
		});
		// DateTime
		getDbDataTypes().addDateTime("SECONDDATE", type -> {
		});
		// TIMESTAMP
		getDbDataTypes().addTimestamp(type -> {
		});
		// Time
		getDbDataTypes().addTime(type -> {
		});
		// インデックスタイプ
		this.setIndexTypeName(IndexType.BTree);
		this.setIndexTypeName(IndexType.CPBTree);
		this.setIndexTypeName(IndexType.InvertedValue);
		this.setIndexTypeName(IndexType.InvertedHash);
		this.setIndexTypeName(IndexType.InvertedIndivisual);
	}

	/**
	 * DB名
	 */
	@Override
	public String getProductName() {
		return "SAP HANA";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getSimpleName()
	 */
	@Override
	public String getSimpleName() {
		return "hana";
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
		return null;
	}

	@Override
	public boolean supportsIdentity() {
		return true;
	}

	@Override
	public boolean supportsDefaultValueFunction() {
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
	public boolean supportsDropCascade() {
		return true;
	}

	/**
	 * インデックス名のテーブルスコープ
	 */
	@Override
	public boolean supportsIndexNameTableScope() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.dialect.DbDialect#getCatalogReader()
	 */
	@Override
	public CatalogReader getCatalogReader() {
		return new SapHanaCatalogReader(this);
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

	@Override
	public boolean recommendsNTypeChar() {
		return true;
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new SapHanaSqlFactoryRegistry(this);
	}

	@Override
	public SapHanaSqlBuilder createSqlBuilder() {
		return new SapHanaSqlBuilder(this);
	}

	@Override
	public SapHanaSqlSplitter createSqlSplitter() {
		return new SapHanaSqlSplitter(this);
	}

	@Override
	public String getTemporaryTableName(final Table table, String prefix, String suffix, boolean witSchema) {
		String name = null;
		if (!CommonUtils.isEmpty(prefix)) {
			name = prefix + table.getName();
		} else {
			name = "#" + table.getName();
		}
		if (!CommonUtils.isEmpty(suffix)) {
			name = name + suffix;
		}
		return getObjectFullName(name);
	}
}
