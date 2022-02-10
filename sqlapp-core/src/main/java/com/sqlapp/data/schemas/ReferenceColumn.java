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

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.IncludedColumnProperty;
import com.sqlapp.data.schemas.properties.LengthProperty;
import com.sqlapp.data.schemas.properties.NullsOrderProperty;
import com.sqlapp.data.schemas.properties.OrderProperty;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.data.schemas.properties.WithProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * カラムの参照を保持するオブジェクト
 * 
 * @author satoh
 * 
 */
public final class ReferenceColumn extends
		AbstractNamedObject<ReferenceColumn> implements
		HasParent<ReferenceColumnCollection>
		, SchemaNameProperty<ReferenceColumn>
		, TableNameProperty<ReferenceColumn>
		, OrderProperty<ReferenceColumn>
		, LengthProperty<ReferenceColumn>
		, NullsOrderProperty<ReferenceColumn>
		,IncludedColumnProperty<ReferenceColumn>
		,WithProperty<ReferenceColumn>
	{

	/** serialVersionUID */
	private static final long serialVersionUID = -6483603161537304602L;
	/**
	 * schema name
	 */
	private String schemaName = null;
	/**
	 * テーブル名
	 */
	private String tableName = null;

	@Override
	protected Supplier<ReferenceColumn> newInstance(){
		return ()->new ReferenceColumn();
	}

	@Override
	public String getSchemaName() {
		if (column!=null){
			return column.getSchemaName();
		}
		Table table=getTable();
		if (table != null) {
			return table.getSchemaName();
		}
		return schemaName;
	}

	@Override
	public ReferenceColumn setSchemaName(String value) {
		this.schemaName=value;
		return this;
	}
	
	@Override
	public String getTableName() {
		if (column!=null){
			return column.getTableName();
		}
		Table table=getTable();
		if (table != null) {
			return table.getName();
		}
		return tableName;
	}

	@Override
	public ReferenceColumn setTableName(String tableName) {
		this.tableName = tableName;
		return this;
	}

	protected Table getTable(){
		return this.getAncestor(Table.class);
	}
	
	/** カラム */
	private Column column = null;
	/**
	 * ASC,DESC
	 */
	private Order order = (Order)SchemaProperties.ORDER.getDefaultValue();
	/** Length */
	private Long length = (Long)SchemaProperties.LENGTH.getDefaultValue();
	/** NULLのソート順 */
	private NullsOrder nullsOrder = (NullsOrder)SchemaProperties.NULLS_ORDER.getDefaultValue();
	/** 付加列か? */
	private boolean includedColumn =(Boolean)SchemaProperties.INCLUDED_COLUMN.getDefaultValue();
	/** Exclude制約の演算子 */
	private String with = (String)SchemaProperties.WITH.getDefaultValue();

	/**
	 * コンストラクタ
	 */
	protected ReferenceColumn() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param column
	 */
	public ReferenceColumn(Column column) {
		this.column = column;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param column
	 * @param order
	 */
	public ReferenceColumn(Column column, Order order) {
		this.column = column;
		this.order = order;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public ReferenceColumn(String name) {
		super(name);
	}

	@Override
	protected String getSimpleName() {
		return DbObjects.COLUMN.getCamelCase();
	}

	/**
	 * Columnへの参照がある場合は、Columnの名称を、そうでない場合はこのオブジェクトの名称を取得します
	 * 
	 */
	@Override
	public String getName() {
		if (column == null) {
			return super.getName();
		}
		return column.getName();
	}

	/**
	 * 名称を設定します
	 * 
	 */
	@Override
	public ReferenceColumn setName(String name) {
		if (CommonUtils.eq(this.getName(), name)){
			return instance();
		}
		if (this.column != null) {
			this.column=new Column(name);
		} else {
			super.setName(name);
		}
		return this;
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.LENGTH.getLabel(), this.getLength());
		stax.writeAttribute(SchemaProperties.ORDER.getLabel(), convertOrder(this.getOrder()));
		stax.writeAttribute(SchemaProperties.NULLS_ORDER.getLabel(), this.getNullsOrder());
		if (this.isIncludedColumn()) {
			stax.writeAttribute(SchemaProperties.INCLUDED_COLUMN.getLabel(), this.isIncludedColumn());
		}
		stax.writeAttribute(SchemaProperties.WITH.getLabel(), this.getWith());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof ReferenceColumn)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		ReferenceColumn val = (ReferenceColumn) obj;
		if (!equals(SchemaProperties.LENGTH, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ORDER, val, equalsHandler, EqualsUtils.getEqualsSupplier(convertOrder(this.getOrder()), convertOrder(val.getOrder())))) {
			return false;
		}
		if (!equals(SchemaProperties.NULLS_ORDER, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.INCLUDED_COLUMN, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.WITH, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#like(java.lang.Object)
	 */
	@Override
	public boolean like(Object obj) {
		if (!(obj instanceof ReferenceColumn)) {
			return false;
		}
		ReferenceColumn cst = (ReferenceColumn) obj;
		if (!CommonUtils.eq(this.getName(), cst.getName())) {
			return false;
		}
		return true;
	}

	private Order convertOrder(Order order) {
		if (Order.Asc == order) {
			return null;
		}
		return order;
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		if (this.getLength() != null) {
			builder.add(SchemaProperties.LENGTH, this.getLength());
		}
		if (this.getOrder() != null && this.getOrder() != Order.Asc) {
			builder.add(SchemaProperties.ORDER, this.getOrder());
		}
		if (this.getNullsOrder() != null) {
			builder.add(SchemaProperties.NULLS_ORDER, this.getNullsOrder());
		}
		if (this.isIncludedColumn()) {
			builder.add(SchemaProperties.INCLUDED_COLUMN, this.isIncludedColumn());
		}
		if (this.getWith() != null) {
			builder.add(SchemaProperties.WITH, this.getWith());
		}
	}

	/**
	 * @return the order
	 */
	@Override
	public Order getOrder() {
		return order;
	}

	/**
	 * @param order
	 *            the order to set
	 */
	@Override
	public ReferenceColumn setOrder(Order order) {
		this.order = order;
		return this;
	}

	@Override
	public Long getLength() {
		return this.length;
	}

	@Override
	public ReferenceColumn setLength(long length) {
		this.length=length;
		return this;
	}

	@Override
	public ReferenceColumn setLength(Number length) {
		if (length!=null) {
			this.length=length.longValue();
		} else {
			this.length=null;
		}
		return this;
	}

	/**
	 * @return the nullsOrder
	 */
	@Override
	public NullsOrder getNullsOrder() {
		return nullsOrder;
	}

	/**
	 * @param nullsOrder
	 *            the nullsOrder to set
	 */
	@Override
	public ReferenceColumn setNullsOrder(NullsOrder nullsOrder) {
		this.nullsOrder = nullsOrder;
		return this;
	}

	/**
	 * @return the column
	 */
	protected Column getColumn() {
		return column;
	}

	/**
	 * @param column
	 *            the column to set
	 */
	protected ReferenceColumn setColumn(Column column) {
		super.setName(null);
		this.column = column;
		return this;
	}

	@Override
	public boolean isIncludedColumn() {
		return includedColumn;
	}

	@Override
	public ReferenceColumn setIncludedColumn(boolean includedColumn) {
		this.includedColumn = includedColumn;
		return this;
	}

	/**
	 * @return the with
	 */
	@Override
	public String getWith() {
		return with;
	}

	/**
	 * @param with
	 *            the with to set
	 */
	@Override
	public ReferenceColumn setWith(String with) {
		this.with = with;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public ReferenceColumnCollection getParent() {
		return (ReferenceColumnCollection) super.getParent();
	}

	@Override
	protected ReferenceColumnXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new ReferenceColumnXmlReaderHandler();
	}

}
