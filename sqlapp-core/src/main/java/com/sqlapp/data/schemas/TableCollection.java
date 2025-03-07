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

import com.sqlapp.data.schemas.function.AddDbObjectPredicate;

/**
 * Tableのコレクション
 * 
 */
public class TableCollection extends
		AbstractSchemaObjectCollection<Table> implements HasParent<Schema>,
		RowIteratorHandlerProperty
		, NewElement<Table, TableCollection>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2475015269706867532L;

	/**
	 * コンストラクタ
	 */
	protected TableCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected TableCollection(Schema parent) {
		super(parent);
	}
	
	@Override
	protected Supplier<TableCollection> newInstance(){
		return ()->new TableCollection();
	}

	@Override
	public TableCollection clone(){
		return (TableCollection)super.clone();
	}
	
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof TableCollection)) {
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
	public Schema getParent() {
		return this.getSchema();
	}

	@Override
	protected TableCollectionXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new TableCollectionXmlReaderHandler();
	}

	/**
	 * @param addDbObjectFilter
	 *            the addDbObjectFilter to set
	 */
	@Override
	public void setAddDbObjectPredicate(AddDbObjectPredicate addDbObjectFilter) {
		super.setAddDbObjectPredicate(addDbObjectFilter);
		for (Table table : this) {
			table.setAddDbObjectFilter(addDbObjectFilter);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.RowIteratorHandlerProperty#setRowIteratorHandler
	 * (com.sqlapp.data.schemas.RowIteratorHandler)
	 */
	@Override
	public void setRowIteratorHandler(RowIteratorHandler rowIteratorHandler) {
		for (Table table : this) {
			table.setRowIteratorHandler(rowIteratorHandler);
		}
	}

	/**
	 * create順にソートします。
	 */
	public TableCollection sortAsCreateOrder() {
		List<Table> c=SchemaUtils.getNewSortedList(this.inner, Table.TableOrder.CREATE.getComparator());
		this.inner.clear();
		this.inner.addAll(c);
		renew();
		return this;
	}

	/**
	 * DROP順にソートします。
	 */
	public TableCollection sortAsDropOrder() {
		List<Table> c=SchemaUtils.getNewSortedList(this.inner, Table.TableOrder.DROP.getComparator());
		this.inner.clear();
		this.inner.addAll(c);
		renew();
		return this;
	}

	@Override
	protected Supplier<Table> getElementSupplier() {
		return ()->new Table();
	}

	@Override
	public Table newElement() {
		return super.newElementInternal();
	}
	
	@Override
	protected boolean beforeRemove(Table arg) {
		super.beforeRemove(arg);
		arg.setPartitionParent(null);
		return true;
	}

}
