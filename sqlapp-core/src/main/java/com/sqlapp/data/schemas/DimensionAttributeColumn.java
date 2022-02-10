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

import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Dimension Attribute Column
 * 
 * @author satoh
 * 
 */
public class DimensionAttributeColumn extends
		AbstractSchemaObject<DimensionAttributeColumn> implements UnOrdered,
		HasParent<DimensionAttributeColumnCollection>
	, SchemaNameProperty<DimensionAttributeColumn>, TableNameProperty<DimensionAttributeColumn> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -766487951195992327L;

	private Column column=null;
	
	public DimensionAttributeColumn() {

	}

	public DimensionAttributeColumn(String name) {
		super(name);
		this.setName(name);
	}
	
	@Override
	protected Supplier<DimensionAttributeColumn> newInstance(){
		return ()->new DimensionAttributeColumn();
	}
	
	@Override
	protected String getSimpleName() {
		return DbObjects.COLUMN.getCamelCase();
	}

	/**
	 * カラム名を取得します
	 * 
	 * @return カラム名
	 */
	@Override
	public String getName() {
		if (this.column==null){
			return null;
		}
		return column.getName();
	}

	/**
	 * get table name
	 * 
	 * @return table name
	 */
	public String getTableName() {
		if (this.column==null){
			return null;
		}
		return column.getTableName();
	}

	/**
	 * get schema name
	 * 
	 * @return schema name
	 */
	public String getSchemaName() {
		if (this.column==null){
			return null;
		}
		return column.getSchemaName();
	}

	@Override
	public DimensionAttributeColumn clone() {
		DimensionAttributeColumn clone = new DimensionAttributeColumn();
		cloneProperties(clone);
		return clone;
	}

	/**
	 * @return the column
	 */
	public Column getColumn() {
		return column;
	}

	/**
	 * @param column the column to set
	 */
	public DimensionAttributeColumn setColumn(Column column) {
		this.column = column;
		return this.instance();
	}
	
	@Override
	public DimensionAttributeColumn setSchemaName(String schemaName) {
		if (this.column==null){
			this.column=new Column();
			this.column.setSchemaName(schemaName);
		} else{
			if (!CommonUtils.eq(this.column.getSchemaName(), schemaName)){
				Column column=new Column(this.column.getName());
				column.setSchemaName(schemaName);
				this.column=column;
			}
		}
		return this.instance();
	}

	@Override
	public DimensionAttributeColumn setTableName(String tableName) {
		if (this.column==null){
			this.column=new Column();
			this.column.setTableName(tableName);
		} else{
			if (!CommonUtils.eq(this.column.getTableName(), tableName)){
				Column column=new Column(this.column.getName());
				column.setTableName(tableName);
				this.column=column;
			}
		}
		return this.instance();
	}

	/**
	 * @param arg
	 *            the arg to set
	 */
	@Override
	public DimensionAttributeColumn setName(String arg) {
		if (arg == null || arg.length() == 0) {
			this.column=null;
			return this.instance();
		}
		String[] vals = arg.split("[, |]");
		Column column=new Column(CommonUtils.last(vals));
		if (vals.length>1){
			column.setTableName(vals[vals.length-2]);
		}
		if (vals.length>2){
			column.setSchemaName(vals[vals.length-3]);
		}
		if (vals.length>3){
			column.setCatalogName(vals[vals.length-4]);
		}
		this.column=column;
		super.setName(column.getName());
		return this.instance();
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.SCHEMA_NAME, this.getSchemaName());
		builder.add(SchemaProperties.TABLE_NAME, this.getTableName());
		builder.add(SchemaProperties.NAME, this.getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof DimensionAttributeColumn)) {
			return false;
		}
		DimensionAttributeColumn val=(DimensionAttributeColumn)obj;
		if (!equals(SchemaProperties.SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.TABLE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.NAME, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		if (!CommonUtils.eq(this.getSchemaName(), this.getParent().getSchemaName())){
			stax.writeAttribute(SchemaProperties.SCHEMA_NAME.getLabel(), this.getSchemaName());
		}
		stax.writeAttribute(SchemaProperties.TABLE_NAME.getLabel(), this.getTableName());
	}

	@Override
	public DimensionAttributeColumnCollection getParent() {
		return (DimensionAttributeColumnCollection) super.getParent();
	}

	@Override
	public String toString() {
		return this.getName();
	}

	@Override
	protected void validate() {
		super.validate();
		if (this.getColumn()==null){
			return;
		}
		Schema schema=this.getAncestor(Schema.class);
		if (this.getColumn().getSchemaName()==null){
			schema=this.getAncestor(Schema.class);
		} else{
			SchemaCollection schemas=this.getAncestor(SchemaCollection.class);
			if (schemas==null){
				schema=this.getAncestor(Schema.class);
			}
		}
		if (schema==null){
			return;
		}
		Table table=schema.getTables().get(this.getColumn().getTableName());
		if (table==null){
			return;
		}
		Column col=table.getColumns().get(this.getColumn().getName());
		if (col!=null){
			this.column=col;
		}
	}

}
