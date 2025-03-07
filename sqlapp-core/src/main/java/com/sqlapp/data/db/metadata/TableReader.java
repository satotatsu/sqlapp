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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ExcludeConstraint;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.TripleKeyMap;

/**
 * テーブル読み込み抽象クラス
 * 
 * @author satoh
 * 
 */
public abstract class TableReader extends AbstractSchemaObjectReader<Table> {

	protected TableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaObjectProperties getSchemaObjectProperties(){
		return SchemaObjectProperties.TABLES;
	}

	/**
	 * メタデータの詳細情報を設定するためのメソッドです。子クラスでのオーバーライドを想定しています。
	 * 
	 * @param connection
	 * @param obj
	 */
	@Override
	protected void setMetadataDetail(Connection connection,
			ParametersContext context, List<Table> tableList) throws SQLException {
		if (CommonUtils.isEmpty(tableList)){
			return;
		}
		setFilter(tableList, context);
		TripleKeyMap<String, String, String, List<Column>> columnMap = getTableObjectKeyMap(
				connection, context, getColumnReader());
		TripleKeyMap<String, String, String, List<Index>> indexMap = getTableObjectKeyMap(
				connection, context, getIndexReader());
		TripleKeyMap<String, String, String, List<UniqueConstraint>> ucMap = getTableObjectKeyMap(
				connection, context, getUniqueConstraintReader());
		TripleKeyMap<String, String, String, List<ExcludeConstraint>> ecMap = getTableObjectKeyMap(
				connection, context, getExcludeConstraintReader());
		TripleKeyMap<String, String, String, List<CheckConstraint>> ccMap = getTableObjectKeyMap(
				connection, context, getCheckConstraintReader());
		TripleKeyMap<String, String, String, List<ForeignKeyConstraint>> fcMap = getTableObjectKeyMap(
				connection, context, getForeignKeyConstraintReader());
		for (Table table : tableList) {
			table.setDialect(this.getDialect());
			List<Column> columns = columnMap.get(table.getCatalogName(),
					table.getSchemaName(), table.getName());
			if (!isEmpty(columns)) {
				table.getColumns().addAll(columns);
				columnMap.remove(table.getCatalogName(), table.getSchemaName(),
						table.getName());
			}
		}
		for (Table table : tableList) {
			List<Index> indexes = indexMap.get(table.getCatalogName(),
					table.getSchemaName(), table.getName());
			if (!isEmpty(indexes)) {
				table.getIndexes().addAll(indexes);
			}
			List<UniqueConstraint> uniqueConstraints = ucMap.get(
					table.getCatalogName(), table.getSchemaName(),
					table.getName());
			if (!isEmpty(uniqueConstraints)) {
				table.getConstraints().addAll(uniqueConstraints);
			}
			List<ExcludeConstraint> excludeConstraints = ecMap.get(
					table.getCatalogName(), table.getSchemaName(),
					table.getName());
			if (!isEmpty(excludeConstraints)) {
				table.getConstraints().addAll(excludeConstraints);
			}
			List<CheckConstraint> checkConstraints = ccMap.get(
					table.getCatalogName(), table.getSchemaName(),
					table.getName());
			if (!isEmpty(checkConstraints)) {
				table.getConstraints().addAll(checkConstraints);
			}
			List<ForeignKeyConstraint> foreignKeyConstraint = fcMap.get(
					table.getCatalogName(), table.getSchemaName(),
					table.getName());
			if (!isEmpty(foreignKeyConstraint)) {
				table.getConstraints().addAll(foreignKeyConstraint);
			}
		}
	}

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
		if (!catalogNames.isEmpty()){
			context.put("catalogName", catalogNames);
		}
		if (!schemaNames.isEmpty()){
			context.put("schemaName", schemaNames);
		}
		if (!tableNames.isEmpty()){
			context.put("tableName", tableNames);
		}
	}
	
	protected <T extends AbstractSchemaObject<? super T>> TripleKeyMap<String, String, String, List<T>> getTableObjectKeyMap(
			Connection connection, ParametersContext context, TableObjectReader<T> reader) {
		if (reader == null) {
			return new TripleKeyMap<String, String, String, List<T>>();
		}
		List<T> ccList = reader.getAllFull(connection, context);
		TripleKeyMap<String, String, String, List<T>> ccMap = reader
				.toKeyMap(ccList);
		return ccMap;
	}

	protected void setTableObjects(Connection connection,
			TableObjectReader<?> reader, Table table) {
		if (reader != null) {
			reader.loadFull(connection, table);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.db.dialect.metadata.AbstractNamedMetadataFactory#getNameLabel
	 * ()
	 */
	@Override
	protected String getNameLabel() {
		return SchemaProperties.TABLE_NAME.getLabel();
	}

	protected void setReaderParameter(TableObjectReader<?> reader) {
		if (reader != null) {
			reader.setCatalogName(this.getCatalogName());
			reader.setSchemaName(this.getSchemaName());
			reader.setObjectName(this.getObjectName());
			initializeChild(reader);
		}
	}

	public ColumnReader getColumnReader() {
		ColumnReader reader = newColumnReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract ColumnReader newColumnReader();

	protected IndexReader getIndexReader() {
		IndexReader reader = newIndexReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract IndexReader newIndexReader();

	public UniqueConstraintReader getUniqueConstraintReader() {
		UniqueConstraintReader reader = newUniqueConstraintReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract UniqueConstraintReader newUniqueConstraintReader();

	public ExcludeConstraintReader getExcludeConstraintReader() {
		ExcludeConstraintReader reader = newExcludeConstraintReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract ExcludeConstraintReader newExcludeConstraintReader();

	public CheckConstraintReader getCheckConstraintReader() {
		CheckConstraintReader reader = newCheckConstraintReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract CheckConstraintReader newCheckConstraintReader();

	public ForeignKeyConstraintReader getForeignKeyConstraintReader() {
		ForeignKeyConstraintReader reader = newForeignKeyConstraintReader();
		setReaderParameter(reader);
		return reader;
	}

	protected abstract ForeignKeyConstraintReader newForeignKeyConstraintReader();
	
	/**
	 * 指定した名称のReaderを取得します
	 * 
	 * @param name
	 */
	@SuppressWarnings("unchecked")
	protected <T extends MetadataReader<?, ?>> T getMetadataReader(String name) {
		if ("table".equalsIgnoreCase(name) || "tables".equalsIgnoreCase(name)) {
			return (T) this;
		}
		return MetadataReaderUtils.getMetadataReader(this, name);
	}

	protected Table createTable(String name) {
		Table table= new Table(name);
		table.setDialect(this.getDialect());
		return table;
	}
}
