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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.OperatorClassNameProperty;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.data.schemas.properties.SupportNumberProperty;
import com.sqlapp.data.schemas.properties.complex.FunctionProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * FunctionFamily
 * 
 * @author satoh
 * 
 */
public class FunctionFamily extends AbstractDbObject<FunctionFamily> implements
		HasParent<FunctionFamilyCollection>
	, FunctionProperty<FunctionFamily>
	, SchemaNameProperty<FunctionFamily>
	, OperatorClassNameProperty<FunctionFamily>
	, SupportNumberProperty<FunctionFamily>
	{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5692492571670996675L;

	public FunctionFamily() {

	}

	@Override
	protected Supplier<FunctionFamily> newInstance(){
		return ()->new FunctionFamily();
	}
	/**
	 * schemaName
	 */
	private String schemaName = null;
	/**
	 * OperatorClassName
	 */
	private String operatorClassName = null;
	/** サポートプロシージャ番号 */
	private int supportNumber = 0;
	/**
	 * 関数名
	 */
	@SuppressWarnings("unused")
	private Function function = null;

	/**
	 * @param operatorClassName
	 *            the operatorClassName to set
	 */
	@Override
	public FunctionFamily setOperatorClassName(String operatorClassName) {
		this.operatorClassName = operatorClassName;
		return this;
	}

	private static final Pattern FUNCTION_NAME_PATTERN = Pattern
			.compile("([^(]+)(\\(.*\\)){0,1}");

	/**
	 * @param functionName
	 *            the functionName to set
	 */
	@Override
	public FunctionFamily setFunctionName(String functionName) {
		if (isEmpty(functionName)) {
			this.function = null;
			return this;
		}
		Matcher mathcer = FUNCTION_NAME_PATTERN.matcher(functionName);
		if (mathcer.matches()) {
			this.setFunction(new Function(mathcer.group(1)));
			this.getFunction().setSpecificName(functionName);
		}
		return this;
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.SUPPORT_NUMBER.getLabel(), this.getSupportNumber());
		if (this.getFunction() != null
				&& !eq(this.getSchemaName(), this.getFunction().getSchemaName())) {
			stax.writeAttribute(SchemaProperties.FUNCTION_SCHEMA_NAME.getLabel(), this.getFunctionSchemaName());
		}
		stax.writeAttribute(SchemaProperties.FUNCTION_NAME.getLabel(), this.getFunctionName());
	}

	@Override
	protected void toString(ToStringBuilder builder) {
		super.toString(builder);
		builder.add(SchemaProperties.SUPPORT_NUMBER, this.getSupportNumber());
		if (!isEmpty(this.getFunctionName())) {
			builder.add(SchemaProperties.FUNCTION_NAME, this.getFunctionName());
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
		if (!(obj instanceof FunctionFamily)) {
			return false;
		}
		FunctionFamily val = (FunctionFamily) obj;
		if (!equals(SchemaProperties.SUPPORT_NUMBER, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.FUNCTION_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.FUNCTION_NAME, val,equalsHandler)) {
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
	public FunctionFamilyCollection getParent() {
		return (FunctionFamilyCollection) super.getParent();
	}

	@Override
	public String getFunctionName() {
		if (this.getFunction() != null) {
			return getFunction().getSpecificName();
		}
		return null;
	}

	/**
	 * @return the schemaName
	 */
	@Override
	public String getSchemaName() {
		OperatorClass parent=this.getAncestor(OperatorClass.class);
		if (parent!=null){
			return parent.getSchemaName();
		}
		return this.schemaName;
	}

	@Override
	public FunctionFamily setSchemaName(String schemaName) {
		this.schemaName=schemaName;
		return instance();
	}

	@Override
	public String getOperatorClassName() {
		OperatorClass parent=this.getAncestor(OperatorClass.class);
		if (parent!=null){
			return parent.getName();
		}
		return this.operatorClassName;
	}

	/**
	 * @return the supportNumber
	 */
	public int getSupportNumber() {
		return supportNumber;
	}

	/**
	 * @param supportNumber
	 *            the supportNumber to set
	 */
	public FunctionFamily setSupportNumber(int supportNumber) {
		this.supportNumber = supportNumber;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(FunctionFamily o) {
		if (getSupportNumber() > o.getSupportNumber()) {
			return 1;
		} else if (getSupportNumber() < o.getSupportNumber()) {
			return -1;
		} else {
			return 0;
		}
	}

	/**
	 * like用のハンドラー
	 */
	private final static EqualsHandler LIKE_EQUALS_HANDLER = new IncludeFilterEqualsHandler(
			SchemaProperties.FUNCTION_NAME.getLabel());

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObject#like(com.sqlapp.data.schemas
	 * .AbstractDbObject)
	 */
	@Override
	public boolean like(Object obj) {
		if (!super.like(obj)) {
			return false;
		}
		if (!equals(obj, LIKE_EQUALS_HANDLER)) {
			return false;
		}
		return true;
	}

}
