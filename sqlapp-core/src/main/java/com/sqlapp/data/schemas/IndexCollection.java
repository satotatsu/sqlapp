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

package com.sqlapp.data.schemas;

import static com.sqlapp.util.CommonUtils.eq;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * インデックスのコレクション
 * 
 * @author satoh
 * 
 */
public final class IndexCollection extends
		AbstractSchemaObjectCollection<Index> implements Cloneable,
		HasParent<Table>
, NewElement<Index, IndexCollection>{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6007580646027193828L;

	/**
	 * コンストラクタ
	 * 
	 * @param table
	 */
	protected IndexCollection() {
	}
	
	/**
	 * コンストラクタ
	 * 
	 * @param table
	 */
	protected IndexCollection(Table table) {
		super(table);
	}

	@Override
	protected Supplier<IndexCollection> newInstance(){
		return ()->new IndexCollection();
	}
	
	@Override
	public IndexCollection clone(){
		return (IndexCollection)super.clone();
	}
	
	/**
	 * インデックスを追加します
	 * 
	 * @param indexName
	 *            インデックス名
	 * @param columns
	 *            インデックスのあるカラム
	 */
	public Index add(String indexName, Column... columns) {
		return add(indexName, false, columns);
	}

	/**
	 * インデックスを追加します
	 * 
	 * @param indexName
	 *            インデックス名
	 * @param columns
	 *            インデックスのあるカラム
	 */
	public Index add(String indexName, Collection<Column> columns) {
		return add(indexName, false, columns);
	}

	/**
	 * インデックスを追加します
	 * 
	 * @param indexName
	 *            インデックス名
	 * @param unique
	 *            ユニーク
	 * @param columns
	 *            インデックスのあるカラム
	 */
	public Index add(String indexName, boolean unique, Column... columns) {
		Index index = new Index(indexName);
		index.setUnique(unique);
		for (Column column : columns) {
			index.getColumns().add(column);
		}
		add(index);
		return index;
	}

	/**
	 * インデックスを追加します
	 * 
	 * @param indexName
	 *            インデックス名
	 * @param unique
	 *            ユニーク
	 * @param columns
	 *            インデックスのあるカラム
	 */
	public Index add(String indexName, boolean unique,
			Collection<Column> columns) {
		return add(indexName, unique, columns.toArray(new Column[0]));
	}

	/**
	 * 削除後のメソッド
	 */
	@Override
	protected void afterRemove(Index args) {
		if (this.getParent() != null) {
			this.getParent().getConstraints().remove(args.getName());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof IndexCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Parent#getParent()
	 */
	@Override
	public Table getParent() {
		return (Table) super.getParent();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractNamedObjectCollection#find(com.sqlapp
	 * .data.schemas.AbstractNamedObject)
	 */
	@Override
	public Index find(Index obj) {
		for (Index index : this) {
			if (!eq(index.getTableName(), obj.getTableName())) {
				continue;
			}
			if (eq(index.getName(), obj.getName())) {
				return index;
			}
			if (eq(index.getColumns(), obj.getColumns())) {
				return index;
			}
		}
		return null;
	}

	@Override
	public Index newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<Index> getElementSupplier() {
		return ()->new Index();
	}
}
