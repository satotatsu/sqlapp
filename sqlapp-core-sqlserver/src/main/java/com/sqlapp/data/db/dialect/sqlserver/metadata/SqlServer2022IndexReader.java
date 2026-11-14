/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SqlServer2022のインデックス読み込みクラス。順序付きクラスタ化列ストアインデックスのORDER列を読み込みます。
 */
public class SqlServer2022IndexReader extends SqlServer2019IndexReader {

	public SqlServer2022IndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes2022.sql");
	}
}
