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

import com.sqlapp.data.schemas.properties.ColumnNameProperty;
import com.sqlapp.data.schemas.properties.EnableProperty;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.data.schemas.properties.TableNameProperty;
import com.sqlapp.data.schemas.properties.TableSchemaNameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;

/**
 * Mask
 * 
 * @author satoh
 * 
 */
public final class Mask extends AbstractSchemaObject<Mask> implements
		HasParent<MaskCollection>
	, SchemaNameProperty<Mask>
	, TableSchemaNameProperty<Mask>
	, TableNameProperty<Mask>
	, ColumnNameProperty<Mask>
	, EnableProperty<Mask>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -233129040207248096L;

	private Column column=null;
	
	private boolean enable=true;
	
	/**
	 * コンストラクタ
	 */
	public Mask() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Mask(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<Mask> newInstance(){
		return ()->new Mask();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedDdlObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Mask)) {
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
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public MaskCollection getParent() {
		return (MaskCollection) super.getParent();
	}

	/**
	 * @return the column
	 */
	public Column getColumn() {
		if (column==null){
			return null;
		}
		if (column.getParent()==null){
			Table table=SchemaUtils.getTableFromParent(column.getSchemaName()!=null?column.getSchemaName():this.getSchemaName(), column.getTableName(), this);
			if (table!=null){
				Column col=table.getColumns().get(this.column.getName());
				if (col!=null){
					this.column=col;
				}
			}
		}
		return column;
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
	}

	@Override
	public String getColumnName() {
		if (this.getColumn()!=null){
			return this.getColumn().getName();
		}
		return null;
	}

	@Override
	public Mask setColumnName(String columnName) {
		if (this.column==null){
			this.column=new Column();
			this.column.setName(columnName);
		} else{
			if (!CommonUtils.eq(this.column.getName(), columnName)&&this.column.getName()!=null){
				Column column=new Column(columnName);
				this.column=column;
			} else{
				this.column.setName(columnName);
			}
		}
		return instance();
	}

	@Override
	public String getTableName() {
		if (this.getColumn()!=null){
			return this.getColumn().getTableName();
		}
		return null;
	}
	
	public Table getTable(){
		if (this.getColumn()!=null){
			Table table=this.getColumn().getTable();
			if (table!=null){
				return table;
			}
			table=new Table(this.getColumn().getTableName());
			table.setSchemaName(this.getColumn().getSchemaName());
			return table;
		}
		return null;
	}

	@Override
	public Mask setTableName(String tableName) {
		if (this.column==null){
			this.column=new Column();
			this.column.setTableName(tableName);
		} else{
			if (!CommonUtils.eq(this.column.getTableName(), tableName)&&this.column.getTableName()!=null){
				Column column=new Column(this.column.getName());
				column.setTableName(tableName);
				this.column=column;
			} else{
				this.column.setTableName(tableName);
			}
		}
		return instance();
	}

	@Override
	public String getTableSchemaName() {
		if (this.getColumn()!=null){
			return this.getColumn().getSchemaName();
		}
		return null;
	}

	@Override
	public Mask setTableSchemaName(String tableSchemaName) {
		if (this.column==null){
			this.column=new Column();
			this.column.setSchemaName(tableSchemaName);
		} else{
			if (!CommonUtils.eq(this.column.getSchemaName(), tableSchemaName)&&this.column.getSchemaName()!=null){
				Column column=new Column(this.column.getName());
				column.setSchemaName(tableSchemaName);
				this.column=column;
			} else{
				this.column.setTableName(tableSchemaName);
			}
		}
		return instance();
	}

	@Override
	public boolean isEnable() {
		return this.enable;
	}

	@Override
	public Mask setEnable(boolean value) {
		this.enable=value;
		return instance();
	}
	
	@Override
	protected void validate(){
		super.validate();
		this.getColumn();
	}

}
