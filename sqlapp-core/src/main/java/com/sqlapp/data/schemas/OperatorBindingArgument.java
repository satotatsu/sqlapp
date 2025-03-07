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

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.properties.DataTypeProperties;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Operator Binding Argument
 * 
 * @author satoh
 * 
 */
public class OperatorBindingArgument extends AbstractDbObject<OperatorBindingArgument>
		implements HasParent<OperatorBindingArgumentCollection>,
		DataTypeProperties<OperatorBindingArgument> {

	/** serialVersionUID */
	private static final long serialVersionUID = 8186027542736225676L;

	public OperatorBindingArgument() {
	}

	public OperatorBindingArgument(String dataTypeName) {
		this.setDataTypeName(dataTypeName);
	}

	@Override
	protected Supplier<OperatorBindingArgument> newInstance(){
		return ()->new OperatorBindingArgument();
	}
	
	/**
	 * java.sql.Types(VARCHAR,CHAR…)
	 */
	private DataType dataType = null;
	/**
	 * DB固有の型
	 */
	@SuppressWarnings("unused")
	private String dataTypeName = null;

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.DATA_TYPE.getLabel(), this.getDataType());
		stax.writeAttribute(SchemaProperties.DATA_TYPE_NAME.getLabel(), this.getDataTypeName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbTypeProperties#getDbType()
	 */
	@Override
	public DataType getDataType() {
		return dataType;
	}

	/**
	 * @param dataType the dataType to set
	 */
	public OperatorBindingArgument setDataType(DataType dataType) {
		this.dataType = dataType;
		return instance();
	}

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	@Override
	protected void toString(ToStringBuilder builder) {
		builder.add(SchemaProperties.DATA_TYPE, this.getDataType());
		builder.add(SchemaProperties.DATA_TYPE_NAME, this.getDataTypeName());
		super.toString(builder);
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
		if (!(obj instanceof OperatorBindingArgument)) {
			return false;
		}
		OperatorBindingArgument val = (OperatorBindingArgument) obj;
		if (!equals(SchemaProperties.DATA_TYPE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_TYPE, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractDbObject#getParent()
	 */
	@Override
	public OperatorBindingArgumentCollection getParent() {
		return (OperatorBindingArgumentCollection) super.getParent();
	}

	@Override
	public int compareTo(OperatorBindingArgument o) {
		return 0;
	}

	@Override
	protected String getSimpleName() {
		return "argument";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObject#like(com.sqlapp.data.schemas
	 * .AbstractDbObject)
	 */
	@Override
	public boolean like(Object obj) {
		if (!(obj instanceof OperatorBindingArgument)) {
			return false;
		}
		OperatorBindingArgument cst = (OperatorBindingArgument) obj;
		if (!CommonUtils.eq(this.getOrdinal(), cst.getOrdinal())) {
			return false;
		}
		return true;
	}

	@Override
	protected void validate(){
		this.setDataTypeName(this.getDataTypeName());
	}
}
