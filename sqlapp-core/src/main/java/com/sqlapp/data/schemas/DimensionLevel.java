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
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.SkipWhenNullProperty;
import com.sqlapp.data.schemas.properties.object.DimensionLevelColumnsProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Dimension Level
 * 
 * @author satoh
 * 
 */
public class DimensionLevel extends AbstractSchemaObject<DimensionLevel>
		implements HasParent<DimensionLevelCollection>
		, DimensionLevelColumnsProperty<DimensionLevel>
		, SkipWhenNullProperty<DimensionLevel>
		{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -766487951195992327L;

	public DimensionLevel() {

	}

	public DimensionLevel(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<DimensionLevel> newInstance(){
		return ()->new DimensionLevel();
	}

	/**
	 * レベルがSKIP WHEN NULL句で宣言されているか
	 */
	private boolean skipWhenNull = (Boolean)SchemaProperties.SKIP_WHEN_NULL.getDefaultValue();
	/**
	 * カラムのコレクション
	 */
	private DimensionLevelColumnCollection columns = new DimensionLevelColumnCollection(
			this);
	/**
	 * 新規でDimensionLevelColumnを取得します
	 * 
	 */
	public DimensionLevelColumn newColumn() {
		DimensionLevelColumn column = new DimensionLevelColumn();
		column.setParent(columns);
		return column;
	}

	/**
	 * @param columns the columns to set
	 */
	protected DimensionLevel setColumns(DimensionLevelColumnCollection columns) {
		this.columns = columns;
		if (columns!=null){
			columns.setParent(this);
		}
		return instance();
	}
	
	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaObjectProperties.DIMENSION_LEVEL_COLUMNS, this.getColumns().toString("(", ")"));
		builder.add(SchemaProperties.SKIP_WHEN_NULL, this.isSkipWhenNull());
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
		if (!(obj instanceof DimensionLevel)) {
			return false;
		}
		DimensionLevel val = (DimensionLevel) obj;
		if (!equals(SchemaObjectProperties.DIMENSION_LEVEL_COLUMNS, val,
				equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SKIP_WHEN_NULL, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		if (this.isSkipWhenNull()){
			stax.writeAttribute(SchemaProperties.SKIP_WHEN_NULL.getLabel(), this.isSkipWhenNull());
		}
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(columns)) {
			columns.writeXml(stax);
		}
	}

	@Override
	protected String getSimpleName() {
		return "level";
	}

	/**
	 * @return the skipWhenNull
	 */
	public boolean isSkipWhenNull() {
		return skipWhenNull;
	}

	/**
	 * @param skipWhenNull
	 *            the skipWhenNull to set
	 */
	public DimensionLevel setSkipWhenNull(boolean skipWhenNull) {
		this.skipWhenNull = skipWhenNull;
		return this;
	}

	protected Table getTableFromParent(Table table) {
		if (table == null) {
			return table;
		}
		if (this.getParent() == null) {
			return table;
		}
		if (this.getParent().getSchema() == null) {
			return table;
		}
		Schema getSchema = null;
		if (eq(table.getSchemaName(), this.getSchemaName())) {
			getSchema = this.getParent().getSchema();
		} else {
			if (this.getParent().getSchema().getParent() != null) {
				getSchema = this.getParent().getSchema().getParent()
						.get(table.getSchemaName());
			}
		}
		if (getSchema == null) {
			return table;
		}
		Table ret = getSchema.getTable(table.getName());
		if (ret != null) {
			return ret;
		}
		return table;
	}

	/**
	 * @return the columns
	 */
	@Override
	public DimensionLevelColumnCollection getColumns() {
		return columns;
	}

	/**
	 * @param arg
	 *            the arg to set
	 */
	public DimensionLevel setColumns(String arg) {
		if (arg == null || arg.length() == 0) {
			columns.clear();
			return this;
		}
		String[] vals = arg.split("[, |]");
		for (String val : vals) {
			columns.add(val);
		}
		return this;
	}

	@Override
	public DimensionLevelCollection getParent() {
		return (DimensionLevelCollection) super.getParent();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#validate()
	 */
	@Override
	protected void validate() {
		super.validate();
	}
}
