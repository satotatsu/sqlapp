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

import static com.sqlapp.util.CommonUtils.eqIgnoreCase;
import static com.sqlapp.util.CommonUtils.list;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;

import com.sqlapp.data.schemas.properties.SchemaNameGetter;
import com.sqlapp.data.schemas.properties.TableNameGetter;
import com.sqlapp.util.SeparatedStringBuilder;

/**
 * カラムの参照を保持するオブジェクトのコレクション
 * 
 * @author satoh
 * 
 */
public final class ReferenceColumnCollection extends
		AbstractNamedObjectCollection<ReferenceColumn> implements UnOrdered,
		SchemaNameGetter,
		TableNameGetter,
		HasParent<AbstractDbObject<?>>
, NewElement<ReferenceColumn, ReferenceColumnCollection>{

	/** ReferenceColumn */
	private static final long serialVersionUID = 8912043724651049178L;
	
	/**
	 * コンストラクタ
	 */
	protected ReferenceColumnCollection() {
	}

	/**
	 * コンストラクタ
	 */
	protected ReferenceColumnCollection(AbstractDbObject<?> parent) {
		super(parent);
	}

	@Override
	protected Supplier<ReferenceColumnCollection> newInstance(){
		return ()->new ReferenceColumnCollection();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractList#iterator()
	 */
	@Override
	public Iterator<ReferenceColumn> iterator() {
		super.renew();
		return super.iterator();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractList#listIterator()
	 */
	@Override
	public ListIterator<ReferenceColumn> listIterator() {
		super.renew();
		return super.listIterator();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#get(java.lang.String)
	 */
	@Override
	public ReferenceColumn get(final String name) {
		ReferenceColumn column = super.get(name);
		if (column != null) {
			if (eqIgnoreCase(name, column.getName())) {
				return column;
			}
		}
		super.renew();
		return super.get(name);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#get(java.lang.String[])
	 */
	@Override
	public List<ReferenceColumn> getAll(final String... names) {
		super.renew();
		return super.getAll(names);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#get(java.util.List)
	 */
	@Override
	public List<ReferenceColumn> getAll(final List<String> names) {
		super.renew();
		return super.getAll(names);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#get(java.util.Collection)
	 */
	@Override
	public List<ReferenceColumn> getAll(final Collection<String> names) {
		super.renew();
		return super.getAll(names);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof ReferenceColumnCollection)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/**
	 * 指定したカラムを追加します
	 * 
	 * @param name
	 */
	public ReferenceColumn add(String name) {
		if (getTable() != null) {
			Column column = getTable().getColumns().get(name);
			if (column != null) {
				ReferenceColumn rColumn = new ReferenceColumn(column);
				super.add(rColumn);
				return rColumn;
			}
		}
		ReferenceColumn rColumn = new ReferenceColumn(name);
		super.add(rColumn);
		validate();
		return rColumn;
	}

	/**
	 * 指定したカラムを追加します
	 * 
	 * @param column
	 */
	public ReferenceColumn add(Column column) {
		if (getTable() != null) {
			Column orgColumn = getTable().getColumns().get(column.getName());
			if (orgColumn != null) {
				ReferenceColumn rColumn = new ReferenceColumn(orgColumn);
				super.add(rColumn);
				return rColumn;
			}
		}
		ReferenceColumn rColumn = new ReferenceColumn(column);
		super.add(rColumn);
		validate();
		return rColumn;
	}

	/**
	 * 指定したカラムを追加します
	 * 
	 * @param columns
	 */
	public void addAll(Column... columns) {
		for (Column column : columns) {
			super.add(new ReferenceColumn(column));
		}
		validate();
	}

	/**
	 * 指定したカラム、Orderを追加します
	 * 
	 * @param name
	 * @param order
	 */
	public ReferenceColumn add(String name, Order order) {
		ReferenceColumn rColumn = add(name);
		rColumn.setOrder(order);
		return rColumn;
	}

	/**
	 * 指定したカラム、nullsOrderを追加します
	 * 
	 * @param name
	 * @param nullsOrder
	 */
	public ReferenceColumn add(String name, NullsOrder nullsOrder) {
		ReferenceColumn rColumn = add(name);
		rColumn.setNullsOrder(nullsOrder);
		return rColumn;
	}

	/**
	 * 指定したカラム、Orderを追加します
	 * 
	 * @param column
	 * @param order
	 */
	public ReferenceColumn add(Column column, Order order) {
		ReferenceColumn rColumn = add(column);
		rColumn.setOrder(order);
		return rColumn;
	}
	
	/**
	 * 指定したカラム、nullsOrderを追加します
	 * 
	 * @param column
	 * @param nullsOrder
	 */
	public ReferenceColumn add(Column column, NullsOrder nullsOrder) {
		ReferenceColumn rColumn = add(column);
		rColumn.setNullsOrder(nullsOrder);
		return rColumn;
	}

	/**
	 * 指定したカラム、Orderを追加します
	 * 
	 * @param column
	 * @param includedColumn
	 */
	public ReferenceColumn add(Column column, boolean includedColumn) {
		ReferenceColumn rColumn = add(column);
		rColumn.setIncludedColumn(includedColumn);
		return rColumn;
	}

	/**
	 * 指定したカラム(複数)の追加
	 * 
	 * @param names
	 */
	public ReferenceColumnCollection add(final String... names) {
		int size = names.length;
		for (int i = 0; i < size; i++) {
			add(names[i]);
		}
		return this;
	}

	/**
	 * 一括追加
	 * 
	 * @param c
	 */
	public void addAll(List<String> c) {
		for (String str : c) {
			add(str);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#remove(java.lang.String)
	 */
	@Override
	public boolean remove(String o) {
		renew();
		boolean bool= super.remove(o);
		validate();
		return bool;
	}
	
	public Table getTable(){
		String schemaName=null;
		String tableName=null;
		Table obj=null;
		for(ReferenceColumn refColumn:this){
			if (refColumn.getColumn()!=null){
				obj=refColumn.getColumn().getTable();
			}
			if (obj!=null){
				return obj;
			}
			if (refColumn.getSchemaName()!=null){
				schemaName=refColumn.getTableName();
			}
			if (refColumn.getTableName()!=null){
				tableName=refColumn.getTableName();
				break;
			}
		}
		Schema schema=null;
		if (schemaName==null){
			schema=this.getAncestor(Schema.class);
		} else{
			SchemaCollection schemas=this.getAncestor(SchemaCollection.class);
			if (schemas!=null){
				schema=schemas.get(schemaName);
			}
		}
		if (schema==null){
			obj= this.getAncestor(Table.class);
		} else{
			obj=schema.getTable(tableName);
		}
		return obj;
	}

	/**
	 * @return the index
	 */
	public Index getIndex() {
		return this.getAncestor(Index.class);
	}

	@Override
	public AbstractDbObject<?> getParent() {
		return (AbstractDbObject<?>)super.getParent();
	}

	/**
	 * @param parent
	 *            the parent to set
	 */
	protected ReferenceColumnCollection setParent(AbstractSchemaObject<?> parent) {
		super.setParent(parent);
		if (parent instanceof Table){
			setTable((Table)parent);
		}
		return instance();
	}
	
	private ReferenceColumnCollection instance(){
		return this;
	}

	/**
	 * @param table
	 *            the table to set
	 */
	protected ReferenceColumnCollection setTable(Table table) {
		if (table == null) {
			return instance();
		}
		int size = this.size();
		for (int i = 0; i < size; i++) {
			ReferenceColumn rColumn = this.get(i);
			rColumn.setTableName(null);
			Column column = table.getColumns().get(rColumn.getName());
			if (column != null) {
				rColumn.setColumn(column);
			}
		}
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObjectList#clone()
	 */
	@Override
	public ReferenceColumnCollection clone() {
		return (ReferenceColumnCollection)super.clone();
	}

	public List<Column> toColumns() {
		List<Column> list = list(this.size());
		for (ReferenceColumn rCol : this) {
			list.add(rCol.getColumn());
		}
		return list;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObjectList#sort()
	 */
	@Override
	public void sort() {
	}

	/**
	 * カラムの文字列表現を取得します
	 * 
	 */
	public String toStringSimple() {
		SeparatedStringBuilder sep = new SeparatedStringBuilder(", ");
		sep.setStart("(").setEnd(")");
		for (ReferenceColumn column : this) {
			StringBuilder builder = new StringBuilder(column.getName());
			if (column.getOrder() != null) {
				if (Order.Asc != column.getOrder()) {
					builder.append(" ");
					builder.append(column.getOrder());
				}
				if (column.getNullsOrder() != null) {
					builder.append(" ");
					builder.append(column.getNullsOrder());
				}
				if (column.isIncludedColumn()) {
					builder.append(" included");
				}
			}
			sep.add(builder.toString());
		}
		return sep.toString();
	}

	@Override
	protected ReferenceColumnCollectionXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new ReferenceColumnCollectionXmlReaderHandler();
	}

	@Override
	public String getTableName() {
		Table table=this.getTable();
		if (table!=null){
			return table.getName();
		}
		return null;
	}

	@Override
	public String getSchemaName() {
		Table table=this.getTable();
		if (table!=null){
			return table.getSchemaName();
		}
		return null;
	}
	
	@Override
	protected void validate(){
		super.validate();
	}

	@Override
	public ReferenceColumn newElement() {
		return super.newElementInternal();
	}

	@Override
	protected Supplier<ReferenceColumn> getElementSupplier() {
		return ()->new ReferenceColumn();
	}
}
