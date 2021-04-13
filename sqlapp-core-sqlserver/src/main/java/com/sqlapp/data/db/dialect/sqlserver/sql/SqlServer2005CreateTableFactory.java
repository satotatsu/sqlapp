/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.db.sql.AddObjectDetail;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Partitioning;
import com.sqlapp.data.schemas.Table;

/**
 * SQLServer2008用のテーブル作成
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2005CreateTableFactory extends
		AbstractCreateTableFactory<SqlServerSqlBuilder> {

	@Override
	protected void addOption(final Table table, final SqlServerSqlBuilder builder) {
		if(table.getPartitioning()!=null){
			final AddObjectDetail<Partitioning,SqlServerSqlBuilder> addObjectDetail=this.getAddObjectDetail(table.getPartitioning(), SqlType.CREATE);
			if (addObjectDetail!=null){
				addObjectDetail.addObjectDetail(table.getPartitioning(), builder);
			}
		}
	}
	
//	@Override
//	protected void addIndexDefinitions(final Table table, final SqlServerSqlBuilder builder){
//		for (final Index index : table.getIndexes()) {
//			if (!table.getConstraints().contains(index.getName())) {
//				addIndexDefinition(index, builder);
//			}
//		}
//	}
//
//	/**
//	 * インデックスを追加します
//	 * 
//	 * @param index
//	 * @param builder
//	 */
//	protected void addIndexDefinition(final Index index, final SqlServerSqlBuilder builder) {
//		final AddTableObjectDetailFactory<Index, SqlServerSqlBuilder> indexOperation=this.getAddTableObjectDetailOperationFactory(index);
//		if (indexOperation!=null) {
//			builder.lineBreak().comma();
//			indexOperation.addObjectDetail(index, null, builder);
//		}
//	}
	
//	@Override
//	protected void addIndexDefinitions(final Table table,final List<SqlOperation> result) {
//		//create tableと一緒に生成する
//	}
}
