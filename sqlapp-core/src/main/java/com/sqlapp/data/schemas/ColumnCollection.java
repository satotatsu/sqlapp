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

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * カラムのコレクション
 * 
 */
public final class ColumnCollection extends
		AbstractSchemaObjectCollection<Column> implements UnOrdered,
		HasParent<Table>
	, NewElement<Column, ColumnCollection>
	{
	/**
	 * コンストラクタ
	 */
	protected ColumnCollection() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param table
	 */
	protected ColumnCollection(Table table) {
		super(table);
	}
	
	@Override
	protected Supplier<ColumnCollection> newInstance(){
		return ()->new ColumnCollection();
	}
	
	@Override
	public ColumnCollection clone(){
		return (ColumnCollection)super.clone();
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4500961268819976110L;

	/**
	 * 指定した名称のカラムを追加します
	 * 
	 * @param name
	 *            カラム名
	 */
	public ColumnCollection add(String name) {
		Column column = new Column(name);
		this.add(column);
		return this;
	}
	
	public ColumnCollection add(String name, Consumer<Column> cons) {
		Column obj = new Column(name);
		this.add(obj);
		cons.accept(obj);
		return this;
	}

	/**
	 * 追加前の準備メソッド
	 */
	@Override
	protected boolean beforeAdd(Column column) {
		if (getParent() != null) {
			column.setTableName(null);
			getParent().getRows().addColumn(column);
		}
		return true;
	}

	/**
	 * 削除前の準備メソッド
	 */
	@Override
	protected boolean beforeRemove(Column column) {
		if (getParent() != null) {
			getParent().getRows().compactionColumn(column);
		}
		return true;
	}

	/**
	 * スキーマ情報の初期化
	 * 
	 * @param column
	 */
	@Override
	protected void initializeSchemaInfo(Column column) {
		if (getParent() == null) {
			super.initializeSchemaInfo(column);
			return;
		}
		if (equalsIgnoreCase(column.getCatalogName(), getParent()
				.getCatalogName())) {
			column.setCatalogName(null);
		} else {
			column.setCatalogName(getParent().getCatalogName());
		}
		if (equalsIgnoreCase(column.schemaName, getParent().getSchemaName())) {
			column.setSchemaName(null);
		} else {
			column.setCatalogName(getParent().getSchemaName());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof ColumnCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/**
	 * 配列への変換
	 */
	public Column[] toArray() {
		return this.toArray(new Column[0]);
	}

	@Override
	public void sort() {
	}

	@Override
	public Table getParent() {
		return (Table) super.getParent();
	}

	/**
	 * 追加後のメソッド
	 */
	@Override
	protected void afterAdd(Column column) {
		column.validate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractNamedObjectCollection#find(com.sqlapp
	 * .data.schemas.AbstractNamedObject)
	 */
	@Override
	public Column find(Column obj) {
		Column column = this.get(obj.getName());
		if (column != null) {
			return column;
		}
		int size = this.size();
		for (int i = 0; i < size; i++) {
			column = this.get(i);
			if (column.getOrdinal() != obj.getOrdinal()) {
				continue;
			}
			if (column.like(obj)) {
				if (obj.getParent() == null) {
					return column;
				}
				Column eqNameColumn = obj.getParent().get(column.getName());
				if (eqNameColumn == null) {
					return column;
				}
			}
		}
		return null;
	}
	
	@Override
	protected Supplier<Column> getElementSupplier() {
		return ()->new Column();
	}
	
	@Override
	public Column newElement(){
		return super.newElementInternal();
	}
}
