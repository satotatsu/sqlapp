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

package com.sqlapp.data.schemas;

import java.util.List;
import java.util.function.Supplier;

import com.sqlapp.util.CommonUtils;

/**
 * カラムのコレクション
 * 
 */
final class DummyColumnCollection extends
	AbstractNamedObjectCollection<DummyColumn> implements UnOrdered,
		HasParent<DummyTable>
, NewElement<DummyColumn, DummyColumnCollection>{
	/**
	 * コンストラクタ
	 * 
	 * @param table
	 */
	protected DummyColumnCollection() {
	}

	@Override
	protected Supplier<DummyColumnCollection> newInstance(){
		return ()->new DummyColumnCollection();
	}
	
	@Override
	protected String getSimpleName() {
		return "columns";
	}
	
	/**
	 * コンストラクタ
	 * 
	 * @param table
	 */
	protected DummyColumnCollection(DummyTable table) {
		super(table);
	}

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4500961268819976110L;

	/**
	 * 指定した名称のカラムを追加します
	 * 
	 * @param columnName
	 *            カラム名
	 */
	public DummyColumnCollection add(String columnName) {
		DummyColumn column = new DummyColumn(columnName);
		this.add(column);
		return this;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof DummyColumnCollection)) {
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
		List<Column> columns=CommonUtils.list();
		for(DummyColumn col:this){
			Column column=col.toColumn();
			columns.add(column);
		}
		return columns.toArray(new Column[0]);
	}

	@Override
	public void sort() {
	}

	@Override
	public DummyTable getParent() {
		return (DummyTable) super.getParent();
	}

	/**
	 * 追加後のメソッド
	 */
	@Override
	protected void afterAdd(DummyColumn column) {
		column.validate();
	}

	@Override
	protected DummyColumnCollectionXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new DummyColumnCollectionXmlReaderHandler();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractNamedObjectCollection#find(com.sqlapp
	 * .data.schemas.AbstractNamedObject)
	 */
	@Override
	public DummyColumn find(DummyColumn obj) {
		DummyColumn column = this.get(obj.getName());
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
				DummyColumn eqNameColumn = obj.getParent().get(column.getName());
				if (eqNameColumn == null) {
					return column;
				}
			}
		}
		return null;
	}

	@Override
	public DummyColumn newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<DummyColumn> getElementSupplier() {
		return ()->new DummyColumn();
	}
}
