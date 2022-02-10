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
import com.sqlapp.data.schemas.properties.StrategyNumberProperty;
import com.sqlapp.data.schemas.properties.complex.OperatorProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * OperatorFamily
 * 
 * @author satoh
 * 
 */
public class OperatorFamily extends AbstractDbObject<OperatorFamily> implements
		HasParent<OperatorFamilyCollection>
	, SchemaNameProperty<OperatorFamily>
	, OperatorClassNameProperty<OperatorFamily>
	, OperatorProperty<OperatorFamily>
	, StrategyNumberProperty<OperatorFamily>
	{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3913177606695063831L;

	public OperatorFamily() {

	}

	@Override
	protected Supplier<OperatorFamily> newInstance(){
		return ()->new OperatorFamily();
	}

	/**
	 * schemaName
	 */
	private String schemaName = null;
	/**
	 * OperatorClassName
	 */
	private String operatorClassName = null;
	
	private Operator operator;
	
	/** 演算子の戦略番号 */
	private int strategyNumber = 0;

	@Override
	public OperatorFamily setOperatorClassName(String operatorClassName) {
		this.operatorClassName = operatorClassName;
		return instance();
	}

	private static final Pattern FUNCTION_NAME_PATTERN = Pattern
			.compile("([^(]+)(\\(.*\\)){0,1}");

	/**
	 * @param value
	 *            the operatorName to set
	 */
	@Override
	public OperatorFamily setOperatorName(String value) {
		if (isEmpty(value)) {
			this.operator = null;
			return this;
		}
		Matcher mathcer = FUNCTION_NAME_PATTERN.matcher(value);
		if (mathcer.matches()) {
			this.setOperator(new Operator(mathcer.group(1)));
			this.getOperator().setSpecificName(value);
		}
		return this;
	}
	
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractDbObject#writeXmlOptionalAttributes(com
	 * .sqlapp.util.StaxWriter)
	 */
	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.STRATEGY_NUMBER.getLabel(), this.getStrategyNumber());
		if (this.getOperator() != null
				&& !eq(this.getSchemaName(), this.getOperator().getSchemaName())) {
			stax.writeAttribute(SchemaProperties.OPERATOR_SCHEMA_NAME.getLabel(), this.getOperator().getSchemaName());
		}
		stax.writeAttribute(SchemaProperties.OPERATOR_NAME.getLabel(), this.getOperatorName());
	}

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	@Override
	protected void toString(ToStringBuilder builder) {
		builder.add(SchemaProperties.STRATEGY_NUMBER, this.getStrategyNumber());
		builder.add(SchemaProperties.OPERATOR_NAME, this.getOperatorName());
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
		if (!(obj instanceof OperatorFamily)) {
			return false;
		}
		OperatorFamily val = (OperatorFamily) obj;
		if (!equals(SchemaProperties.OPERATOR_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.OPERATOR_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.STRATEGY_NUMBER, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
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
		if (!(obj instanceof OperatorFamily)) {
			return false;
		}
		OperatorFamily cst = (OperatorFamily) obj;
		if (!CommonUtils.eq(this.getOperatorName(), cst.getOperatorName())) {
			return false;
		}
		if (!CommonUtils.eq(this.getStrategyNumber(), cst.getStrategyNumber())) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractDbObject#getParent()
	 */
	@Override
	public OperatorFamilyCollection getParent() {
		return (OperatorFamilyCollection) super.getParent();
	}

	public Operator getOperator() {
		return operator;
	}

	/**
	 * @return the schemaName
	 */
	@Override
	public String getSchemaName() {
		OperatorClass oc = this.getAncestor(OperatorClass.class);
		if (oc != null) {
			return oc.getSchemaName();
		}
		return this.schemaName;
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
	 * @return the strategyNumber
	 */
	public int getStrategyNumber() {
		return strategyNumber;
	}

	/**
	 * @param strategyNumber
	 *            the strategyNumber to set
	 */
	public OperatorFamily setStrategyNumber(int strategyNumber) {
		this.strategyNumber = strategyNumber;
		return this;
	}

	@Override
	public int compareTo(OperatorFamily o) {
		if (getStrategyNumber() > o.getStrategyNumber()) {
			return 1;
		} else if (getStrategyNumber() < o.getStrategyNumber()) {
			return -1;
		} else {
			return 0;
		}
	}

	@Override
	public OperatorFamily setSchemaName(String schemaName) {
		this.schemaName=schemaName;
		return instance();
	}

}
