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

import com.sqlapp.data.schemas.properties.FunctionTypeProperty;
import com.sqlapp.data.schemas.properties.OnNullCallProperty;
import com.sqlapp.data.schemas.properties.StableProperty;
import com.sqlapp.data.schemas.properties.object.FunctionReturningProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * ファンクション
 * 
 * @author satoh
 * 
 */
public final class Function extends ArgumentRoutine<Function> implements
		HasParent<FunctionCollection>
	,FunctionTypeProperty<Function> 
	,StableProperty<Function> 
	,OnNullCallProperty<Function> 
	,FunctionReturningProperty<Function>
{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1587998127886007525L;
	/** FunctionType */
	private FunctionType functionType = FunctionType.Scalar;
	/** ON_NULL_CALL */
	private OnNullCall onNullCall = null;
	/** 決定性(Postgres専用) */
	private Boolean stable = null;
	
	/**
	 * 戻り値情報
	 */
	private FunctionReturning returning = new FunctionReturning(this);

	/**
	 * コンストラクタ
	 */
	public Function() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Function(String name) {
		super(name);
	}

	@Override
	protected Supplier<Function> newInstance(){
		return ()->new Function();
	}

	/**
	 * コンストラクタ
	 * 
	 * @param functionName
	 * @param specificName
	 */
	public Function(String functionName, String specificName) {
		super(functionName, specificName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.NamedTextObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof Function)) {
			return false;
		}
		Function val = (Function) obj;
		if (!equals(SchemaObjectProperties.FUNCTION_RETURNING, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.FUNCTION_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ON_NULL_CALL, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.STABLE, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	/**
	 * @return the returning
	 */
	@Override
	public FunctionReturning getReturning() {
		return returning;
	}

	/**
	 * @param returning
	 *            the returning to set
	 */
	@Override
	public Function setReturning(FunctionReturning returning) {
		if (returning != null) {
			returning.setParent(this);
		}
		if (this.returning != null) {
			this.returning.setParent(null);
		}
		this.returning = returning;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public FunctionCollection getParent() {
		return (FunctionCollection) super.getParent();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.Routine#writeXmlOptionalAttributes(com.sqlapp
	 * .util.StaxWriter)
	 */
	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		if (this.getFunctionType() != FunctionType.Scalar) {
			stax.writeAttribute(SchemaProperties.FUNCTION_TYPE.getLabel(), this.getFunctionType());
		}
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.STABLE.getLabel(), this.getStable());
		stax.writeAttribute(SchemaProperties.ON_NULL_CALL.getLabel(), this.getOnNullCall());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.Routine#writeXmlOptionalValues(com.sqlapp.util
	 * .StaxWriter)
	 */
	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		returning.writeXml(stax);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractNamedObject#toStringDetail(com.sqlapp
	 * .util.ToStringBuilder)
	 */
	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		super.toStringDetail(builder);
		builder.add(SchemaProperties.FUNCTION_TYPE, this.getFunctionType());
		builder.add(FunctionReturning.SIMPLE_NAME, this.getReturning());
		builder.add(SchemaProperties.ON_NULL_CALL, this.getOnNullCall());
		builder.add(SchemaProperties.STABLE, this.getStable());
	}

	/**
	 * @return the functionType
	 */
	@Override
	public FunctionType getFunctionType() {
		return functionType;
	}

	/**
	 * @param functionType
	 *            the functionType to set
	 */
	@Override
	public Function setFunctionType(FunctionType functionType) {
		this.functionType = functionType;
		return instance();
	}

	/**
	 * @return the nullInput
	 */
	@Override
	public OnNullCall getOnNullCall() {
		return onNullCall;
	}

	/**
	 * @param onNullCall
	 *            the onNullCall to set
	 */
	@Override
	public Function setOnNullCall(OnNullCall onNullCall) {
		this.onNullCall = onNullCall;
		return instance();
	}

	/**
	 * @return the stable
	 */
	@Override
	public Boolean getStable() {
		return stable;
	}

	/**
	 * @param stable the stable to set
	 */
	@Override
	public Function setStable(Boolean stable) {
		this.stable = stable;
		if (this.stable!=null&&this.stable.booleanValue()){
			this.setDeterministic(null);
		}
		return instance();
	}

	/**
	 * @param deterministic
	 *            the deterministic to set
	 */
	@Override
	public Function setDeterministic(Boolean deterministic) {
		super.setDeterministic(deterministic);
		if (this.getDeterministic()!=null){
			this.stable=null;
		}
		return instance();
	}
	
	@Override
	protected void validate(){
		super.validate();
		this.returning.validate();
	}
}
