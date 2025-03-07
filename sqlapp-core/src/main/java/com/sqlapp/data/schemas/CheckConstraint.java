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

import java.util.List;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.ExpressionProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * チェック制約クラス
 * 
 * @author satoh
 * 
 */
public class CheckConstraint extends AbstractColumnConstraint<CheckConstraint> 
	implements ExpressionProperty<CheckConstraint>{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3296514394919641856L;
	/** 制約式 */
	private String expression = null;

	/**
	 * コンストラクタ
	 */
	protected CheckConstraint() {
	}

	@Override
	protected Supplier<Constraint> newInstance(){
		return ()->new CheckConstraint();
	}
	
	@Override
	public CheckConstraint clone(){
		return (CheckConstraint)super.clone();
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 *            制約名
	 * @param expression
	 *            チェック制約式
	 * @param columns
	 *            テーブルのカラム
	 */
	public CheckConstraint(final String constraintName,
			final String expression, final Column... columns) {
		super(constraintName, columns);
		this.expression = expression;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param constraintName
	 *            制約名
	 * @param expression
	 *            チェック制約式
	 * @param columns
	 *            テーブルのカラム
	 */
	public CheckConstraint(final String constraintName,
			final String expression, final List<Column> columns) {
		super(constraintName, columns);
		this.expression = expression;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.Constraint#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof CheckConstraint)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		CheckConstraint val = (CheckConstraint) obj;
		if (!equals(SchemaProperties.EXPRESSION, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		super.toStringDetail(builder);
		builder.add(SchemaProperties.EXPRESSION, this.expression);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.EXPRESSION, this);
	}

	@Override
	public String getExpression() {
		return expression;
	}

	@Override
	public CheckConstraint setExpression(final String expression) {
		this.expression = expression;
		return this;
	}

	private static EqualsHandler EQUALS_HANDLER=new IncludeFilterEqualsHandler(
			SchemaProperties.EXPRESSION.getLabel());

	@Override
	public boolean like(Object obj) {
		if (!(obj instanceof CheckConstraint)){
			return false;
		}
		CheckConstraint con=(CheckConstraint)obj;
		if (!CommonUtils.eq(this.getName(), con.getName())){
			if (this.getParent()!=null&&con.getParent()!=null){
				if (this.getParent().contains(con.getName())||con.getParent().contains(this.getName())){
					return false;
				}
			}
		}
		if (equals(obj, EQUALS_HANDLER)) {
			return true;
		}
		return false;
	}

	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbCommonObject#toStringSimple()
	 */
	@Override
	public String toStringSimple() {
		ToStringBuilder builder = new ToStringBuilder(this.getSimpleName());
		if (this.getParent()==null){
			builder.add(SchemaProperties.CATALOG_NAME, this.getCatalogName());
			builder.add(SchemaProperties.SCHEMA_NAME, this.getSchemaName());
		}
		builder.add(SchemaProperties.NAME, this.getName());
		builder.add(SchemaProperties.EXPRESSION, this.getExpression());
		return builder.toString();
	}
	
	@Override
	protected CheckConstraint instance() {
		return this;
	}
	
	@Override
	public CheckConstraint setEnable(boolean bool){
		super.setEnable(bool);
		return instance();
	}
	
	@Override
	public CheckConstraint setDeferrability(Deferrability deferrability) {
		super.setDeferrability(deferrability);
		return this;
	}
	
	@Override
	public CheckConstraint setDeferrability(String deferrability) {
		super.setDeferrability(deferrability);
		return this;
	}
}
