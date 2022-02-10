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

package com.sqlapp.data.db.metadata;

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.MviewLog;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.TripleKeyMap;
/**
 * マテビューログ読み込み
 * @author satoh
 *
 */
public abstract class MviewLogReader extends AbstractSchemaObjectReader<MviewLog>{

	protected MviewLogReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.MVIEW_LOGS;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.metadata.AbstractNamedMetadataFactory#getNameLabel()
	 */
	@Override
	protected String getNameLabel(){
		return DbObjects.MVIEW_LOG.getCamelCaseNameLabel();
	}
	
	protected <T extends AbstractSchemaObject<? super T>> TripleKeyMap<String, String, String, List<T>> getTableObjectKeyMap(Connection connection, TableObjectReader<T> reader){
		if (reader==null){
			return new TripleKeyMap<String, String, String, List<T>>();
		}
		List<T> ccList=reader.getAllFull(connection);
		TripleKeyMap<String, String, String, List<T>> ccMap=reader.toKeyMap(ccList);
		return ccMap;
	}

	@Override
	public List<MviewLog> getAllFull(Connection connection) {
		ParametersContext context=defaultParametersContext(connection);
		List<MviewLog> tableList=getAll(connection, context);
		loadAllMetadata(connection, tableList);
		return tableList;
	}
	
	protected void loadAllMetadata(Connection connection, List<MviewLog> mviewLogList) {
		TripleKeyMap<String, String, String, List<ReferenceColumn>> columnMap=getTableObjectKeyMap(connection, getMviewLogColumnReader());
		for(MviewLog mviewLog:mviewLogList){
			mviewLog.setDialect(this.getDialect());
			List<ReferenceColumn> columns=columnMap.get(mviewLog.getCatalogName(), mviewLog.getSchemaName(), mviewLog.getMasterTableName());
			if (!isEmpty(columns)){
				mviewLog.getColumns().addAll(columns);
				columnMap.remove(mviewLog.getCatalogName(), mviewLog.getSchemaName(), mviewLog.getMasterTableName());
			}
		}
	}
	
	protected TripleKeyMap<String, String, String, List<ReferenceColumn>> getTableObjectKeyMap(Connection connection, MviewLogColumnReader reader){
		if (reader==null){
			return new TripleKeyMap<String, String, String, List<ReferenceColumn>>();
		}
		List<ReferenceColumn> ccList=reader.getAllFull(connection);
		TripleKeyMap<String, String, String, List<ReferenceColumn>> ccMap=reader.toKeyMap(ccList);
		return ccMap;
	}
	
	protected void setTableObjects(Connection connection, TableObjectReader<?> reader, Table table){
		if (reader!=null){
			reader.loadFull(connection, table);
		}
	}
	
	protected MviewLogColumnReader getMviewLogColumnReader(){
		MviewLogColumnReader reader=newMviewLogColumnReader();
		setObjectName(reader);
		return reader;
	}

	protected abstract MviewLogColumnReader newMviewLogColumnReader();
	
	protected void setObjectName(MviewLogColumnReader reader){
		if (reader!=null){
			reader.setCatalogName(this.getCatalogName());
			reader.setSchemaName(this.getSchemaName());
			reader.setObjectName(this.getObjectName());
			initializeChild(reader);
		}
	}
}
