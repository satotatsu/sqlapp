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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.object.DimensionAttributeColumnsProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Dimension Attribute
 * 
 * @author satoh
 * 
 */
public class DimensionAttribute extends AbstractSchemaObject<DimensionAttribute>
		implements HasParent<DimensionAttributeCollection>, DimensionAttributeColumnsProperty<DimensionAttribute> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -766487951195992327L;
	
	private DimensionAttributeColumnCollection columns=new DimensionAttributeColumnCollection(this);
	
	public DimensionAttribute() {

	}

	public DimensionAttribute(String name) {
		super(name);
	}

	@Override
	protected Supplier<DimensionAttribute> newInstance(){
		return ()->new DimensionAttribute();
	}
	
	@Override
	public DimensionAttribute clone() {
		DimensionAttribute clone = new DimensionAttribute();
		cloneProperties(clone);
		return clone;
	}

	@Override
	protected void cloneProperties(DimensionAttribute clone) {
		super.cloneProperties(clone);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaObjectProperties.DIMENSION_ATTRIBUTE_COLUMNS, this.getColumns());
	}

	/**
	 * @return the columns
	 */
	public DimensionAttributeColumnCollection getColumns() {
		return columns;
	}

	/**
	 * @param columns the columns to set
	 */
	protected DimensionAttribute setColumns(DimensionAttributeColumnCollection columns) {
		this.columns = columns;
		if (columns!=null){
			columns.setParent(this);
		}
		return instance();
	}

	@Override
	protected String getSimpleName() {
		return "attribute";
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
		if (!(obj instanceof DimensionAttribute)) {
			return false;
		}
		DimensionAttribute val = (DimensionAttribute) obj;
		if (!equals(SchemaObjectProperties.DIMENSION_ATTRIBUTE_COLUMNS, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	public DimensionAttributeCollection getParent() {
		return (DimensionAttributeCollection) super.getParent();
	}

	
	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		if (!isEmpty(this.getColumns())) {
			getColumns().writeXml(stax);
		}
		super.writeXmlOptionalValues(stax);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#validate()
	 */
	@Override
	protected void validate() {
		super.validate();
		this.getColumns().validate();
	}

}
