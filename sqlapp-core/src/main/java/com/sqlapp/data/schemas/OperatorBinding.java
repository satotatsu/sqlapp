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

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.properties.DataTypeProperties;
import com.sqlapp.data.schemas.properties.PropertyProperty;
import com.sqlapp.data.schemas.properties.TypeSchemaNameProperty;
import com.sqlapp.data.schemas.properties.complex.ImplementationTypeProperty;
import com.sqlapp.data.schemas.properties.object.OperatorBindingArgumentsProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * OperatorBinding
 * 
 * @author satoh
 * 
 */
public class OperatorBinding extends AbstractDbObject<OperatorBinding>
		implements HasParent<OperatorBindingCollection>,
		DataTypeProperties<OperatorBinding>
		,TypeSchemaNameProperty<OperatorBinding>
		,ImplementationTypeProperty<OperatorBinding>
		,OperatorBindingArgumentsProperty<OperatorBinding>
		,PropertyProperty<OperatorBinding>
{

	/** serialVersionUID */
	private static final long serialVersionUID = 8186027542736225676L;

	public OperatorBinding() {
	}

	public OperatorBinding(String dataTypeName) {
		this.setDataTypeName(dataTypeName);
	}

	@Override
	protected Supplier<OperatorBinding> newInstance(){
		return ()->new OperatorBinding();
	}
	
	private OperatorBindingArgumentCollection arguments = new OperatorBindingArgumentCollection(this);
	/**
	 * 戻り値の型のスキーマ for Oracle
	 */
	private String typeSchemaName = null;
	/**
	 * java.sql.Types(VARCHAR,CHAR…)
	 */
	private DataType dataType = null;
	/**
	 * DB固有の型
	 */
	@SuppressWarnings("unused")
	private String dataTypeName = null;
	/**
	 * WITH INDEX CONTEXTまたはSCAN CONTEXTで作成された場合 for Oracle
	 */
	@SuppressWarnings("unused")
	private Type implementationType = null;
	/**
	 * 演算子バインディングのプロパティ WITH INDEX CONTEXT COMPUTE ANCILLARY DATA ANCILLARY TO
	 * WITH COLUMN CONTEXT WITH INDEX, COLUMN CONTEXT COMPUTE ANCILLARY DATA,
	 * WITH COLUMN CONTEXT for Oracle
	 */
	private String property = null;

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.TYPE_SCHEMA_NAME.getLabel(), this.getTypeSchemaName());
		stax.writeAttribute(SchemaProperties.DATA_TYPE.getLabel(), this.getDataType());
		stax.writeAttribute(SchemaProperties.DATA_TYPE_NAME.getLabel(), this.getDataTypeName());
		stax.writeAttribute(SchemaProperties.IMPLEMENTATION_TYPE_SCHEMA_NAME.getLabel(), this.getImplementationTypeSchemaName());
		stax.writeAttribute(SchemaProperties.IMPLEMENTATION_TYPE_NAME.getLabel(), this.getImplementationTypeName());
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		if (!isEmpty(this.getArguments())) {
			this.getArguments().writeXml(stax);
		}
		super.writeXmlOptionalValues(stax);
	}
	
	@Override
	protected String getSimpleName() {
		return "binding";
	}

	protected OperatorBinding setArguments(OperatorBindingArgumentCollection arguments){
		this.arguments = arguments;
		if (arguments!=null){
			arguments.setParent(this);
		}
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
		builder.add(SchemaProperties.PROPERTY, this.getProperty());
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
		if (!(obj instanceof OperatorBinding)) {
			return false;
		}
		OperatorBinding val = (OperatorBinding) obj;
		if (!equals(SchemaProperties.TYPE_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_TYPE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.ARGUMENTS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.IMPLEMENTATION_TYPE_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.IMPLEMENTATION_TYPE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PROPERTY, val, equalsHandler)) {
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
	public OperatorBindingCollection getParent() {
		return (OperatorBindingCollection) super.getParent();
	}

	@Override
	public int compareTo(OperatorBinding o) {
		return 0;
	}

	/**
	 * @return the typeSchemaName
	 */
	@Override
	public String getTypeSchemaName() {
		return typeSchemaName;
	}

	/**
	 * @param typeSchemaName the typeSchemaName to set
	 */
	@Override
	public OperatorBinding setTypeSchemaName(String typeSchemaName) {
		this.typeSchemaName = typeSchemaName;
		return instance();
	}

	/**
	 * @return the dataType
	 */
	@Override
	public DataType getDataType() {
		return dataType;
	}

	/**
	 * @param dataType the dataType to set
	 */
	@Override
	public OperatorBinding setDataType(DataType dataType) {
		this.dataType = dataType;
		return instance();
	}

	/**
	 * @return the property
	 */
	public String getProperty() {
		return property;
	}

	/**
	 * @param property the property to set
	 */
	public OperatorBinding setProperty(String property) {
		this.property = property;
		return instance();
	}

	/**
	 * @return the arguments
	 */
	@Override
	public OperatorBindingArgumentCollection getArguments() {
		return arguments;
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
		if (!(obj instanceof OperatorBinding)) {
			return false;
		}
		OperatorBinding cst = (OperatorBinding) obj;
		if (!CommonUtils.eq(this.getOrdinal(), cst.getOrdinal())) {
			return false;
		}
		return true;
	}

	@Override
	protected void validate(){
		super.validate();
	}
}
