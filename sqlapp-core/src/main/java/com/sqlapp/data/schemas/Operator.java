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

import com.sqlapp.data.schemas.properties.HashesProperty;
import com.sqlapp.data.schemas.properties.MergesProperty;
import com.sqlapp.data.schemas.properties.complex.CommutativeOperatorProperty;
import com.sqlapp.data.schemas.properties.complex.FunctionProperty;
import com.sqlapp.data.schemas.properties.complex.JoinFunctionProperty;
import com.sqlapp.data.schemas.properties.complex.NegationOperatorProperty;
import com.sqlapp.data.schemas.properties.complex.RestrictFunctionProperty;
import com.sqlapp.data.schemas.properties.object.OperatorBindingsProperty;
import com.sqlapp.data.schemas.properties.object.OperatorLeftArgumentProperty;
import com.sqlapp.data.schemas.properties.object.OperatorRightArgumentProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * オペレータ
 * 
 * @author satoh
 * 
 */
public final class Operator extends AbstractSchemaObject<Operator> implements
		HasParent<OperatorCollection> 
	, CommutativeOperatorProperty<Operator>
	, NegationOperatorProperty<Operator>
	, RestrictFunctionProperty<Operator>
	, JoinFunctionProperty<Operator>
	, HashesProperty<Operator>
	, MergesProperty<Operator>
	, FunctionProperty<Operator>
	, OperatorLeftArgumentProperty<Operator>
	, OperatorRightArgumentProperty<Operator>
	, OperatorBindingsProperty<Operator>
{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 4636603027166503574L;
	/**
	 * 交代演算子 for Postgres
	 */
	@SuppressWarnings("unused")
	private Operator commutativeOperator = null;
	/**
	 * 否定子 for Postgres
	 */
	@SuppressWarnings("unused")
	private Operator negationOperator = null;
	/**
	 * 制約選択評価関数 for Postgres
	 */
	@SuppressWarnings("unused")
	private Function restrictFunction = null;
	/**
	 * 結合選択評価関数 for Postgres
	 */
	@SuppressWarnings("unused")
	private Function joinFunction = null;
	/**
	 * この演算子がハッシュ結合をサポートできることを示します for Postgres
	 */
	private boolean hashes = (Boolean)SchemaProperties.HASHES.getDefaultValue();
	/**
	 * この演算子がマージ結合をサポートできることを示します for Postgres
	 */
	private boolean merges = (Boolean)SchemaProperties.MERGES.getDefaultValue();
	/**
	 * function(oracle) or procedure(postgres)
	 */
	@SuppressWarnings("unused")
	private Function function = null;
	/**
	 * この演算子の左辺の引数の型 for Postgres
	 */
	private OperatorArgument leftArgument = null;
	/**
	 * この演算子の右辺の引数の型 for Postgres
	 */
	private OperatorArgument rightArgument = null;
	/**
	 * バインディング for Oracle
	 */
	private OperatorBindingCollection bindings = new OperatorBindingCollection(this);

	/**
	 * コンストラクタ
	 */
	public Operator() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Operator(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<Operator> newInstance(){
		return ()->new Operator();
	}

	@Override
	public String getSpecificName() {
		if (this.leftArgument!=null||this.rightArgument!=null) {
			StringBuilder builder = new StringBuilder();
			if (getName()!=null){
				builder.append(getName());
			}
			builder.append("(");
			if (this.leftArgument == null) {
				builder.append(",");
				if (this.rightArgument != null) {
					builder.append(rightArgument.getDataTypeName());
				}
			} else {
				builder.append(leftArgument.getDataTypeName());
				builder.append(",");
				if (this.rightArgument != null) {
					builder.append(rightArgument.getDataTypeName());
				}
			}
			builder.append(")");
			return builder.toString();
		}
		return getName();
	}

	/**
	 * sepecificNameの再設定に合わせて、親をresetします
	 */
	protected void renewParent() {
		if (this.getParent() != null) {
			this.getParent().renew();
		}
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.FUNCTION_NAME, this.getFunctionName());
		builder.add(SchemaObjectProperties.OPERATOR_LEFT_ARGUMENT, this.getLeftArgument());
		builder.add(SchemaObjectProperties.OPERATOR_RIGHT_ARGUMENT, this.getRightArgument());
		//
		builder.add(SchemaProperties.COMMUTATIVE_OPERATOR_NAME, this.getCommutativeOperatorName());
		builder.add(SchemaProperties.NEGATION_OPERATOR_NAME, this.getNegationOperatorName());
		builder.add(SchemaProperties.RESTRICT_FUNCTION_NAME, this.getRestrictFunctionName());
		builder.add(SchemaProperties.JOIN_FUNCTION_NAME, this.getJoinFunctionName());
		if (this.isHashes()) {
			builder.add(SchemaProperties.HASHES, this.isHashes());
		}
		if (this.isMerges()) {
			builder.add(SchemaProperties.MERGES, this.isMerges());
		}
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
		if (!CommonUtils.eq(this.getSchemaName(), getFunctionSchemaName())){
			stax.writeAttribute(SchemaProperties.FUNCTION_NAME.getLabel(), this.getFunctionSchemaName());
		}
		stax.writeAttribute(SchemaProperties.FUNCTION_NAME.getLabel(), this.getFunctionName());
		//
		if (!CommonUtils.eq(this.getSchemaName(), getCommutativeOperatorSchemaName())){
			stax.writeAttribute(SchemaProperties.COMMUTATIVE_OPERATOR_SCHEMA_NAME.getLabel(), this.getCommutativeOperatorSchemaName());
		}
		stax.writeAttribute(SchemaProperties.COMMUTATIVE_OPERATOR_NAME.getLabel(), this.getCommutativeOperatorName());
		if (!CommonUtils.eq(this.getSchemaName(), this.getNegationOperatorSchemaName())){
			stax.writeAttribute(SchemaProperties.NEGATION_OPERATOR_SCHEMA_NAME.getLabel(), this.getNegationOperatorSchemaName());
		}
		stax.writeAttribute(SchemaProperties.NEGATION_OPERATOR_NAME.getLabel(), this.getNegationOperatorName());
		if (!CommonUtils.eq(this.getSchemaName(), this.getRestrictFunctionSchemaName())){
			stax.writeAttribute(SchemaProperties.NEGATION_OPERATOR_SCHEMA_NAME.getLabel(), this.getRestrictFunctionSchemaName());
		}
		stax.writeAttribute(SchemaProperties.RESTRICT_FUNCTION_NAME.getLabel(), this.getRestrictFunctionName());
		if (!CommonUtils.eq(this.getSchemaName(), this.getRestrictFunctionSchemaName())){
			stax.writeAttribute(SchemaProperties.JOIN_FUNCTION_SCHEMA_NAME.getLabel(), this.getJoinFunctionSchemaName());
		}
		stax.writeAttribute(SchemaProperties.JOIN_FUNCTION_NAME.getLabel(), this.getJoinFunctionName());
		if (this.isHashes()) {
			stax.writeAttribute(SchemaProperties.HASHES.getLabel(), this.isHashes());
		}
		if (this.isMerges()) {
			stax.writeAttribute(SchemaProperties.MERGES.getLabel(), this.isMerges());
		}
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(leftArgument)) {
			leftArgument.writeXml(SchemaObjectProperties.OPERATOR_LEFT_ARGUMENT.getLabel(), stax);
		}
		if (!isEmpty(rightArgument)) {
			rightArgument.writeXml(SchemaObjectProperties.OPERATOR_RIGHT_ARGUMENT.getLabel(), stax);
		}
		if (!isEmpty(this.getBindings())) {
			this.getBindings().writeXml(stax);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Operator)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Operator val = (Operator) obj;
		if (!equals(SchemaProperties.FUNCTION_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.FUNCTION_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.OPERATOR_BINDINGS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.OPERATOR_LEFT_ARGUMENT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.OPERATOR_RIGHT_ARGUMENT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.COMMUTATIVE_OPERATOR_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.COMMUTATIVE_OPERATOR_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.NEGATION_OPERATOR_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.NEGATION_OPERATOR_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.RESTRICT_FUNCTION_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.RESTRICT_FUNCTION_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.JOIN_FUNCTION_SCHEMA_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.JOIN_FUNCTION_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.HASHES, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.MERGES, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}
	
	protected Operator setBindings(OperatorBindingCollection bindings){
		this.bindings = bindings;
		if (bindings!=null){
			bindings.setParent(this);
		}
		return instance();
	}

	@Override
	public OperatorArgument getLeftArgument() {
		if (this.leftArgument != null) {
			this.leftArgument.setParent(this);
		}
		return leftArgument;
	}

	@Override
	public Operator setLeftArgument(OperatorArgument leftArgument) {
		if (this.leftArgument != null) {
			this.leftArgument.setParent(null);
		}
		this.leftArgument = leftArgument;
		if (this.leftArgument != null) {
			this.leftArgument.setDialect(this.getDialect());
			this.leftArgument.setParent(this);
		}
		renewParent();
		validate();
		return this;
	}

	@Override
	public Operator setLeftArgument(String leftArgument) {
		OperatorArgument argument = new OperatorArgument();
		argument.setParent(this);
		argument.setDialect(this.getDialect());
		argument.setDataTypeName(leftArgument);
		this.leftArgument = argument;
		validate();
		return this;
	}

	@Override
	public OperatorArgument getRightArgument() {
		if (this.rightArgument != null) {
			this.leftArgument.setParent(this);
		}
		return rightArgument;
	}

	@Override
	public Operator setRightArgument(OperatorArgument rightArgument) {
		if (this.rightArgument != null) {
			this.rightArgument.setParent(null);
		}
		this.rightArgument = rightArgument;
		if (this.rightArgument != null) {
			this.leftArgument.setDialect(this.getDialect());
			this.rightArgument.setParent(this);
		}
		renewParent();
		validate();
		return this;
	}

	@Override
	public Operator setRightArgument(String rightArgument) {
		OperatorArgument argument = new OperatorArgument();
		argument.setParent(this);
		argument.setDialect(this.getDialect());
		argument.setDataTypeName(rightArgument);
		this.rightArgument = argument;
		validate();
		return this;
	}

	/**
	 * @return the hashes
	 */
	public boolean isHashes() {
		return hashes;
	}

	/**
	 * @param hashes
	 *            the hashes to set
	 */
	public Operator setHashes(boolean hashes) {
		this.hashes = hashes;
		return this;
	}

	/**
	 * @return the merges
	 */
	public boolean isMerges() {
		return merges;
	}

	/**
	 * @param merges
	 *            the merges to set
	 */
	public Operator setMerges(boolean merges) {
		this.merges = merges;
		return this;
	}

	/**
	 * @return the bindings
	 */
	@Override
	public OperatorBindingCollection getBindings() {
		return bindings;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractDbObject#getParent()
	 */
	@Override
	public OperatorCollection getParent() {
		return (OperatorCollection) super.getParent();
	}
	
	@Override
	protected void validate(){
		this.getBindings().validate();
		if (this.leftArgument!=null){
			this.leftArgument.validate();
		}
		if (this.rightArgument!=null){
			this.rightArgument.validate();
		}
	}
}
