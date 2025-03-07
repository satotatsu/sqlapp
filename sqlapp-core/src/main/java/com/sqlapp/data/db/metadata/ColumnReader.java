/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.TripleKeyMap;

public abstract class ColumnReader extends TableObjectReader<Column>{

	protected ColumnReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * カラム名
	 */
	private String columnName=null;

	@Override
	public void loadFull(Connection connection, Table table) {
		List<Column> list=getAllFull(connection);
		int size=list.size();
		List<Column> c=getSchemaObjectList(table);
		for(int i=0;i<size;i++){
			Column obj=list.get(i);
			initialize(obj);
			c.add(obj);
		}
	}
	
	@Override
	public void load(Connection connection, Table table) {
		List<Column> list=getAll(connection);
		int size=list.size();
		List<Column> c=getSchemaObjectList(table);
		for(int i=0;i<size;i++){
			Column obj=list.get(i);
			initialize(obj);
			c.add(obj);
		}
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.dialect.metadata.DbMetadataReader#doGetAllAfter(java.sql.Connection, java.util.List)
	 */
	@Override
	protected void doGetAllAfter(Connection connection, List<Column> list){
		TripleKeyMap<String, String, String, Domain> tkDomainMap=CommonUtils.tripleKeyMap();
		TripleKeyMap<String, String, String, Type> tkTypeMap=CommonUtils.tripleKeyMap();
		SchemaReader schemaReader=this.getParent().getParent();
		DomainReader domainReader=schemaReader.getDomainReader();
		TypeReader typeReader=schemaReader.getTypeReader();
		if (domainReader!=null){
			List<Domain> domains=domainReader.getAllFull(connection);
			for(Domain domain:domains){
				tkDomainMap.put(domain.getCatalogName(), domain.getSchemaName(), domain.getName(), domain);
			}
		}
		if (typeReader!=null){
			List<Type> types=typeReader.getAllFull(connection);
			for(Type type:types){
				tkTypeMap.put(type.getCatalogName(), type.getSchemaName(), type.getName(), type);
			}
		}
		for(Column column:list){
			if (column.getDataType()!=DataType.OTHER){
				continue;
			}
			Domain domain=tkDomainMap.get(column.getCatalogName(), column.getSchemaName(), column.getDataTypeName());
			if (domain!=null){
				column.setDataType(DataType.DOMAIN);
				continue;
			}
			Type type=tkTypeMap.get(column.getCatalogName(), column.getSchemaName(), column.getDataTypeName());
			if (type!=null){
				column.setDataType(DataType.STRUCT);
				continue;
			}
			logger.info("Unsupported dbTypeName="+column.getDataTypeName()+", column="+column);
		}

	}

	
	/**
	 * リストからTripleKeyMapに変換します
	 * @param list
	 */
	@Override
	protected TripleKeyMap<String, String, String, List<Column>> toKeyMap(List<Column> list){
		TripleKeyMap<String, String, String, List<Column>> map=new TripleKeyMap<String, String, String, List<Column>>();
		for(Column obj:list){
			List<Column> tableConsts=map.get(obj.getCatalogName(), obj.getSchemaName(), obj.getTableName());
			if (tableConsts==null){
				tableConsts=list();
				map.put(obj.getCatalogName(), obj.getSchemaName(), obj.getTableName(), tableConsts);
			}
			tableConsts.add(obj);
		}
		return map;
	}

	protected ColumnCollection getSchemaObjectList(Table table) {
		return table.getColumns();
	}

	/**
	 * カタログ名、スキーマ名、テーブル名、カラム名を含むパラメタコンテキストを作成します。
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection){
		ParametersContext context=newParametersContext(connection, this.getCatalogName(), this.getSchemaName());
		this.setTableName(context, nativeCaseString(connection,  this.getObjectName()));
		context.put(getNameLabel(), nativeCaseString(connection,  this.getColumnName()));
		context.put("containsHiddenColumns", this.getReaderOptions().isContainsHiddenColumns());
		return context;
	}

	@Override
	protected String getNameLabel(){
		return SchemaProperties.COLUMN_NAME.getLabel();
	}

	public String getColumnName() {
		return columnName;
	}

	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}

	protected String getColumnName(ParametersContext context){
		return (String)context.get(getNameLabel());
	}
	
	protected Column createColumn(String name){
		Column column=new Column(name);
		column.setDialect(this.getDialect());
		return column;
	}
}
