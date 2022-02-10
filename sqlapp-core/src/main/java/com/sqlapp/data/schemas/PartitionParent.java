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

import com.sqlapp.data.schemas.properties.HighValueProperty;
import com.sqlapp.data.schemas.properties.LowValueProperty;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.data.schemas.properties.complex.TableProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Partition Parent
 * 
 * @author satoh
 * 
 */
public final class PartitionParent extends AbstractDbObject<PartitionParent>
	implements SchemaNameProperty<PartitionParent>
	, TableNameProperty<PartitionParent>
	, TableProperty<PartitionParent>
	, LowValueProperty<PartitionParent>
	, HighValueProperty<PartitionParent>
	, HasParent<Table> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;
	private Table table = new Table();
	/** Partition lowValue */
	private String lowValue = null;
	/** Partition highValue */
	private String highValue = null;
	
	public PartitionParent() {
	}

	protected PartitionParent(Table parent) {
		this.setParent(parent);
	}
	
	@Override
	protected Supplier<PartitionParent> newInstance(){
		return ()->new PartitionParent();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedDdlObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof PartitionParent)) {
			return false;
		}
		PartitionParent val = (PartitionParent) obj;
		if (!equals(SchemaProperties.SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.TABLE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.LOW_VALUE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.HIGH_VALUE, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public Table getParent() {
		return (Table) super.getParent();
	}

	@Override
	public String getSchemaName() {
		if (this.table==null) {
			return null;
		}
		if (this.table.getSchemaName()!=null) {
			return this.table.getSchemaName();
		}
		if (this.table!=null) {
			return this.table.getSchemaName();
		}
		return null;
	}

	@Override
	public String getTableName() {
		return this.table.getName();
	}

	@Override
	public int compareTo(PartitionParent o) {
		return 0;
	}

	@Override
	public String getHighValue() {
		return this.highValue;
	}

	@Override
	public PartitionParent setHighValue(String highValue) {
		this.highValue=highValue;
		return instance();
	}

	@Override
	public String getLowValue() {
		return this.lowValue;
	}

	@Override
	public PartitionParent setLowValue(String lowValue) {
		this.lowValue=lowValue;
		return instance();
	}

	@Override
	public PartitionParent setTableName(String tableName) {
		if (!CommonUtils.eq(this.table.getName(), tableName)) {
			String schemaName=this.getSchemaName();
			this.table=new Table(tableName);
			this.table.setSchemaName(schemaName);
		}
		return instance();
	}
	
	@Override
	public PartitionParent setSchemaName(String name) {
		if (!CommonUtils.eq(this.table.getSchemaName(), name)) {
			String tableName=this.getTableName();
			this.table=new Table(tableName);
			this.table.setSchemaName(name);
		}
		return instance();
	}
	
	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		if (this.getParent()==null||(!CommonUtils.eq(this.getParent().getSchemaName(), this.getSchemaName()))) {
			stax.writeAttribute(SchemaProperties.SCHEMA_NAME, this);
		}
		stax.writeAttribute(SchemaProperties.TABLE_NAME, this);
		stax.writeAttribute(SchemaProperties.LOW_VALUE, this);
		stax.writeAttribute(SchemaProperties.HIGH_VALUE, this);
	}
	
	@Override
	protected void toString(ToStringBuilder builder) {
		builder.add(SchemaProperties.SCHEMA_NAME, this.getSchemaName());
		builder.add(SchemaProperties.TABLE_NAME, this.getTableName());
		builder.add(SchemaProperties.LOW_VALUE, this.getLowValue());
		builder.add(SchemaProperties.HIGH_VALUE, this.getLowValue());
	}
}
