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

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.properties.DataTypeProperties;
import com.sqlapp.data.schemas.properties.DefaultProperty;
import com.sqlapp.data.schemas.properties.IndexTypeProperty;
import com.sqlapp.data.schemas.properties.object.FunctionFamiliesProperty;
import com.sqlapp.data.schemas.properties.object.OperatorFamiliesProperty;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * オペレータクラス(for Postgres)
 * 
 * @author satoh
 * 
 */
public final class OperatorClass extends AbstractSchemaObject<OperatorClass>
		implements HasParent<OperatorClassCollection>
	, DataTypeProperties<OperatorClass>
	, DefaultProperty<OperatorClass>
	, OperatorFamiliesProperty<OperatorClass>
	, FunctionFamiliesProperty<OperatorClass>
	, IndexTypeProperty<OperatorClass> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -7288255390207070306L;
	/**
	 * java.sql.Types(VARCHAR,CHAR…)
	 */
	private DataType dataType = null;
	/**
	 * DB固有の型
	 */
	private String dataTypeName = null;
	/**
	 * インデックスタイプ
	 */
	private IndexType indexType = null;
	/**
	 * 演算子クラスのデフォルトである場合はtrue
	 */
	private boolean _default = (Boolean)SchemaProperties.DEFAULT.getDefaultValue();
	/**
	 * OperatorFamilyCollection
	 */
	private OperatorFamilyCollection operatorFamilies = new OperatorFamilyCollection(
			this);
	/**
	 * FunctionFamilyCollection
	 */
	private FunctionFamilyCollection functionFamilies = new FunctionFamilyCollection(
			this);

	/**
	 * コンストラクタ
	 */
	protected OperatorClass() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public OperatorClass(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<OperatorClass> newInstance(){
		return ()->new OperatorClass();
	}

	/**
	 * @return the operatorFamilies
	 */
	@Override
	public OperatorFamilyCollection getOperatorFamilies() {
		return operatorFamilies;
	}

	/**
	 * @return the operatorFamilies
	 */
	@Override
	public FunctionFamilyCollection getFunctionFamilies() {
		return functionFamilies;
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbTypeProperties#setDbType(com.sqlapp.data.db
	 * .datatype.Types)
	 */
	@Override
	public OperatorClass setDataType(DataType dbType) {
		this.dataType = dbType;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbTypeProperties#getDbTypeName()
	 */
	@Override
	public String getDataTypeName() {
		return dataTypeName;
	}

	/**
	 * @return the indexType
	 */
	@Override
	public IndexType getIndexType() {
		return indexType;
	}

	/**
	 * @param indexType
	 *            the indexType to set
	 */
	@Override
	public OperatorClass setIndexType(IndexType indexType) {
		this.indexType = indexType;
		return this;
	}

	/**
	 * @return the default
	 */
	@Override
	public boolean isDefault() {
		return _default;
	}

	/**
	 * @param _default the default to set
	 */
	@Override
	public OperatorClass setDefault(boolean _default) {
		this._default = _default;
		return this;
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.DATA_TYPE, this.getDataType());
		builder.add(SchemaProperties.DATA_TYPE_NAME, this.getDataTypeName());
		builder.add(SchemaProperties.INDEX_TYPE, this.getIndexType());
		builder.add(SchemaProperties.DEFAULT, this.isDefault());
		builder.add(SchemaObjectProperties.OPERATOR_FAMILIES, this.getOperatorFamilies());
		builder.add(SchemaObjectProperties.FUNCTION_FAMILIES, this.getFunctionFamilies());
	}

	/**
	 * 名称のXMLへの書き込み
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	protected void writeName(StaxWriter stax) throws XMLStreamException {
		stax.writeAttribute("name", getName());
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.DATA_TYPE.getLabel(), this.getDataType());
		stax.writeAttribute(SchemaProperties.DATA_TYPE_NAME.getLabel(), this.getDataTypeName());
		stax.writeAttribute(SchemaProperties.INDEX_TYPE.getLabel(), this.getIndexType());
		stax.writeAttribute(SchemaProperties.DEFAULT.getLabel(), this.isDefault());
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(operatorFamilies)) {
			operatorFamilies.writeXml(stax);
		}
		if (!isEmpty(functionFamilies)) {
			functionFamilies.writeXml(stax);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof OperatorClass)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		OperatorClass val = (OperatorClass) obj;
		if (!equals(SchemaProperties.DATA_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_TYPE_NAME
				, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getDataTypeName(), val.getDataTypeName()))) {
			return false;
		}
		if (!equals(SchemaProperties.INDEX_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DEFAULT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.OPERATOR_FAMILIES, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.FUNCTION_FAMILIES, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/**
	 * @param operatorFamilies
	 *            the operatorFamilies to set
	 */
	protected OperatorClass setOperatorFamilies(
			OperatorFamilyCollection operatorFamilies) {
		this.operatorFamilies = operatorFamilies;
		return this;
	}

	/**
	 * @param functionFamilies
	 *            the functionFamilies to set
	 */
	protected OperatorClass setFunctionFamilies(
			FunctionFamilyCollection functionFamilies) {
		this.functionFamilies = functionFamilies;
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractDbObject#getParent()
	 */
	@Override
	public OperatorClassCollection getParent() {
		return (OperatorClassCollection) super.getParent();
	}

	@Override
	protected void validate(){
		if (this.getDialect() != null) {
			String dataTypeName=this.getDataTypeName();
			this.setDataTypeName(dataTypeName);
		}
		this.operatorFamilies.validate();
		this.functionFamilies.validate();
	}
}
