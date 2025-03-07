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

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * DimensionHierarchyJoinKey
 * 
 * @author satoh
 * 
 */
public class DimensionHierarchyJoinKeyColumn extends AbstractDbObject<DimensionHierarchyJoinKeyColumn> implements UnOrdered,
		HasParent<DimensionHierarchyJoinKeyColumnCollection>, NameProperty<DimensionHierarchyJoinKeyColumn>
	, SchemaNameProperty<DimensionHierarchyJoinKeyColumn>, TableNameProperty<DimensionHierarchyJoinKeyColumn> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -766487951195992327L;

	private Column column=null;
	
	public DimensionHierarchyJoinKeyColumn() {

	}

	public DimensionHierarchyJoinKeyColumn(String name) {
		this.setName(name);
	}

	@Override
	protected Supplier<DimensionHierarchyJoinKeyColumn> newInstance(){
		return ()->new DimensionHierarchyJoinKeyColumn();
	}
	
	@Override
	protected String getSimpleName() {
		return DbObjects.COLUMN.getCamelCase();
	}

	/**
	 * get table name
	 * 
	 * @return table name
	 */
	@Override
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
	@Override
	public String getSchemaName() {
		if (this.column==null){
			return null;
		}
		return column.getSchemaName();
	}

	/**
	 * @return the column
	 */
	public Column getColumn() {
		return column;
	}

	@Override
	public DimensionHierarchyJoinKeyColumn setSchemaName(String schemaName) {
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
	public DimensionHierarchyJoinKeyColumn setTableName(String tableName) {
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
	public DimensionHierarchyJoinKeyColumn setName(String arg) {
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
		return this.instance();
	}

	@Override
	public String getName(){
		if (column==null){
			return null;
		}
		return column.getName();
	}

	@Override
	protected void toString(ToStringBuilder builder) {
		super.toString(builder);
		if (this.getColumn()!=null){
			builder.add(SchemaProperties.TABLE_NAME, this.getTableName());
			builder.add(SchemaProperties.NAME, this.getName());
		}
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
		if (!(obj instanceof DimensionHierarchyJoinKeyColumn)) {
			return false;
		}
		DimensionHierarchyJoinKeyColumn val=(DimensionHierarchyJoinKeyColumn)obj;
		if (!equals(SchemaProperties.SCHEMA_NAME, val,equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.TABLE_NAME, val,equalsHandler)) {
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
		stax.writeAttribute(SchemaProperties.NAME.getLabel(), this.getName());
		if (!CommonUtils.eq(this.getSchemaName(), this.getParent().getSchemaName())){
			stax.writeAttribute(SchemaProperties.SCHEMA_NAME.getLabel(), this.getSchemaName());
		}
		stax.writeAttribute(SchemaProperties.TABLE_NAME.getLabel(), this.getTableName());
	}

	@Override
	public DimensionHierarchyJoinKeyColumnCollection getParent() {
		return (DimensionHierarchyJoinKeyColumnCollection) super.getParent();
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

	@Override
	public int compareTo(DimensionHierarchyJoinKeyColumn o) {
		return 0;
	}

}
