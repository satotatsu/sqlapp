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
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.DbObjects;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.TypeColumn;
import com.sqlapp.util.TripleKeyMap;

public abstract class TypeReader extends AbstractSchemaObjectReader<Type>{
	/**
	 * タイプ名
	 */
	protected static final String TYPE_NAME="type_name";

	protected TypeReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.TYPES;
	}

	@Override
	protected void setMetadataDetail(Connection connection,
			ParametersContext context, List<Type> typeList) throws SQLException {
		TripleKeyMap<String, String, String, List<TypeColumn>> columnMap=getTableObjectKeyMap(connection, getColumnReader());
		for(Type type:typeList){
			type.setDialect(this.getDialect());
			List<TypeColumn> columns=columnMap.get(type.getCatalogName(), type.getSchemaName(), type.getName());
			if (!isEmpty(columns)){
				type.getColumns().addAll(columns);
				columnMap.remove(type.getCatalogName(), type.getSchemaName(), type.getName());
			}
		}
	}
	

	protected TripleKeyMap<String, String, String, List<TypeColumn>> getTableObjectKeyMap(Connection connection, TypeColumnReader reader){
		if (reader==null){
			return new TripleKeyMap<String, String, String, List<TypeColumn>>();
		}
		List<TypeColumn> ccList=reader.getAllFull(connection);
		TripleKeyMap<String, String, String, List<TypeColumn>> ccMap=reader.toKeyMap(ccList);
		return ccMap;
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.metadata.AbstractNamedMetadataFactory#getNameLabel()
	 */
	@Override
	protected String getNameLabel(){
		return DbObjects.TYPE.getCamelCaseNameLabel();
	}

	protected TypeColumnReader getColumnReader(){
		TypeColumnReader reader=newColumnFactory();
		if (reader!=null){
			reader.setCatalogName(this.getCatalogName());
			reader.setSchemaName(this.getSchemaName());
			reader.setObjectName(this.getObjectName());
			this.initializeChild(reader);
		}
		return reader;
	}

	protected abstract TypeColumnReader newColumnFactory();
}
