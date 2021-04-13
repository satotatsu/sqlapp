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

import java.util.Map;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerSqlBuilder;
import com.sqlapp.data.schemas.Table;

/**
 * SQLServer2008用のテーブル作成
 * 
 * @author tatsuo satoh
 * 
 */
public class SqlServer2008CreateTableFactory extends SqlServer2005CreateTableFactory {

	@Override
	protected void addOption(final Table table, final SqlServerSqlBuilder builder) {
		super.addOption(table, builder);
		final Map<String, String> map = table.getSpecifics();
		if(table.isCompression()) {
			builder.lineBreak();
			builder.with().space().brackets(()->{
				builder.dataCompression();
				if ("ROW".equalsIgnoreCase(table.getCompressionType())) {
					builder.row();
				}else if ("PAGE".equalsIgnoreCase(table.getCompressionType())) {
					builder.page();
				}else if ("COLUMNSTORE".equalsIgnoreCase(table.getCompressionType())) {
					builder.columnstore();
				}else if ("COLUMNSTORE_ARCHIVE".equalsIgnoreCase(table.getCompressionType())) {
					builder.columnstoreArchive();
				} else {
					builder.row();
				}
			});
		}
		final Boolean val = Converters.getDefault().convertObject(
				map.get("HAS_CHANGE_TRACKING"), Boolean.class);
		if (val != null) {
			builder.lineBreak();
			if (val.booleanValue()) {
				builder.enable().changeTracking();
			} else {
				builder.lineBreak();
				builder.disable().changeTracking();
			}
		}
	}
	
	
}
