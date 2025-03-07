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

import static com.sqlapp.util.CommonUtils.eqIgnoreCase;
import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexCollection;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.TripleKeyMap;

public abstract class IndexReader extends TableObjectReader<Index> {
	/**
	 * INDEX TYPE
	 */
	public static final String INDEX_TYPE = "index_type";
	public static final String PCT_FREE = "pct_free";
	/**
	 * 100-PCT_FREE
	 */
	public static final String FILL_FACTOR = "fill_factor";

	protected IndexReader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * インデックス名
	 */
	private String indexName = null;

	public String getIndexName() {
		return indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	@Override
	public void loadFull(Connection connection, Table table) {
		List<Index> list = getAllFull(connection);
		int size = list.size();
		List<Index> c = getSchemaObjectList(table);
		UniqueConstraint uc = table.getConstraints().getPrimaryKeyConstraint();
		// ユニーク制約から優先で設定する
		for (int i = 0; i < size; i++) {
			Index obj = list.get(i);
			if (!obj.isUnique()) {
				continue;
			}
			if (!isPrimaryKey(uc, obj)) {
				// プライマリキーは追加しない
				c.add(obj);
			}
		}
		// 非ユニーク制約を設定する
		for (int i = 0; i < size; i++) {
			Index obj = list.get(i);
			if (obj.isUnique()) {
				continue;
			}
			if (!isPrimaryKey(uc, obj)) {
				// プライマリキーは追加しない
				c.add(obj);
			}
		}
	}
	
	@Override
	public void load(Connection connection, Table table) {
		List<Index> list = getAll(connection);
		int size = list.size();
		List<Index> c = getSchemaObjectList(table);
		UniqueConstraint uc = table.getConstraints().getPrimaryKeyConstraint();
		// ユニーク制約から優先で設定する
		for (int i = 0; i < size; i++) {
			Index obj = list.get(i);
			if (!obj.isUnique()) {
				continue;
			}
			if (!isPrimaryKey(uc, obj)) {
				// プライマリキーは追加しない
				c.add(obj);
			}
		}
		// 非ユニーク制約を設定する
		for (int i = 0; i < size; i++) {
			Index obj = list.get(i);
			if (obj.isUnique()) {
				continue;
			}
			if (!isPrimaryKey(uc, obj)) {
				// プライマリキーは追加しない
				c.add(obj);
			}
		}
	}

	private boolean isPrimaryKey(UniqueConstraint uc, Index obj) {
		if (uc != null && !eqIgnoreCase(uc.getName(), obj.getName())
				&& uc.isPrimaryKey()) {
			return true;
		}
		return false;
	}

	/**
	 * リストからTripleKeyMapに変換します
	 * 
	 * @param list
	 */
	@Override
	protected TripleKeyMap<String, String, String, List<Index>> toKeyMap(
			List<Index> list) {
		TripleKeyMap<String, String, String, List<Index>> map = new TripleKeyMap<String, String, String, List<Index>>();
		for (Index obj : list) {
			List<Index> tableConsts = map.get(obj.getCatalogName(),
					obj.getSchemaName(), obj.getTableName());
			if (tableConsts == null) {
				tableConsts = list();
				map.put(obj.getCatalogName(), obj.getSchemaName(),
						obj.getTableName(), tableConsts);
			}
			tableConsts.add(obj);
		}
		return map;
	}

	/**
	 * カタログ名、スキーマ名、テーブル名、インデックス名を含むパラメタコンテキストを作成します。
	 * 
	 */
	@Override
	protected ParametersContext defaultParametersContext(Connection connection) {
		ParametersContext context = newParametersContext(connection,
				this.getCatalogName(), this.getSchemaName());
		this.setTableName(context,
				nativeCaseString(connection, this.getObjectName()));
		context.put(getNameLabel(),
				nativeCaseString(connection, this.getIndexName()));
		return context;
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
		return SchemaProperties.INDEX_NAME.getLabel();
	}

	protected IndexCollection getSchemaObjectList(Table table) {
		return table.getIndexes();
	}

}
