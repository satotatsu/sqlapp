/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.jdbc.metadata;

import static com.sqlapp.util.CommonUtils.emptyToNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * 汎用のテーブル読み込みクラス
 * 
 * @author satoh
 * 
 */
public class JdbcTableReader extends TableReader {

	public JdbcTableReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * テーブルタイプ "TABLE","VIEW", "SYSTEM TABLE", "GLOBAL TEMPORARY",
	 * "LOCAL TEMPORARY", "ALIAS", "SYNONYM".
	 */
	private String[] tableTypes = new String[] { "TABLE", "SYSTEM TABLE" };

	@Override
	protected List<Table> doGetAll(Connection connection,
			ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		try {
			return JdbcMetadataUtils.getMetadata(connection,
					CommonUtils.coalesce(emptyToNull(this.getCatalogName(context)), emptyToNull(this.getCatalogName()))
					,CommonUtils.coalesce(emptyToNull(this.getSchemaName(context)), emptyToNull(this.getSchemaName()))
					,CommonUtils.coalesce(emptyToNull(this.getObjectName(context)), emptyToNull(this.getObjectName()))
					, getTableType());
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void setFilter(List<Table> tableList, ParametersContext context){
		Set<String> catalogNames=CommonUtils.treeSet();
		Set<String> schemaNames=CommonUtils.treeSet();
		Set<String> tableNames=CommonUtils.treeSet();
		for (Table table : tableList) {
			if (table.getCatalogName()!=null){
				catalogNames.add(table.getCatalogName());
			}
			if (table.getSchemaName()!=null){
				schemaNames.add(table.getSchemaName());
			}
			if (table.getName()!=null){
				tableNames.add(table.getName());
			}
		}
		if (catalogNames.size()==1){
			context.put("catalogName", catalogNames);
		}
		if (schemaNames.size()==1){
			context.put("schemaName", schemaNames);
		}
		if (tableNames.size()==1){
			context.put("tableName", tableNames);
		}
	}
	
	protected String[] getTableType() {
		return tableTypes;
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new JdbcColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new JdbcIndexReader(this.getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new JdbcPrimaryKeyConstraintReader(this.getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return null;
	}

	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new JdbcForeignKeyConstraintReader(this.getDialect());
	}

	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}
}