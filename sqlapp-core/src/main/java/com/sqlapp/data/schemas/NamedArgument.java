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

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.DirectionProperty;
import com.sqlapp.data.schemas.properties.ReadonlyProperty;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * 名前付きの引数
 * 
 * @author satoh
 * 
 */
public class NamedArgument extends AbstractColumn<NamedArgument> implements
		HasParent<NamedArgumentCollection<?>>, ReadonlyProperty<NamedArgument>
	, DirectionProperty<NamedArgument>{

	/** serialVersionUID */
	private static final long serialVersionUID = -3927630688300024633L;

	public NamedArgument() {

	}

	public NamedArgument(String name) {
		super(name);
	}
	
	@Override
	protected Supplier<NamedArgument> newInstance(){
		return ()->new NamedArgument();
	}

	private Routine<?> routine;
	/**
	 * パラメタの入出力方向
	 */
	private ParameterDirection direction = ParameterDirection.Input;
	/** 読み込み専用 */
	private Boolean readonly = null;

	/**
	 * @param routine
	 *            the routine to set
	 */
	protected void setRoutine(Routine<?> routine) {
		this.routine = routine;
	}

	@SuppressWarnings("unchecked")
	public <S extends Routine<?>> S getRoutine() {
		if (this.getParent() != null && this.getParent().getParent() != null) {
			return (S) this.getParent().getParent();
		}
		return (S) this.routine;
	}

	/**
	 * Routine名を取得します
	 * 
	 */
	public String getRoutineName() {
		if (this.getRoutine()==null){
			return null;
		}
		return getRoutine().getName();
	}

	/**
	 * @return the direction
	 */
	public ParameterDirection getDirection() {
		return direction;
	}

	/**
	 * @param direction
	 *            the direction to set
	 */
	public NamedArgument setDirection(ParameterDirection direction) {
		this.direction = direction;
		return this;
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		super.toStringDetail(builder);
		builder.add(SchemaProperties.DIRECTION, this.getDirection());
		builder.add(SchemaProperties.READONLY, this.getReadonly());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof NamedArgument)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		NamedArgument val = (NamedArgument) obj;
		if (!equals(SchemaProperties.DIRECTION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.READONLY, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.DIRECTION.getLabel(), this.getDirection());
		stax.writeAttribute(SchemaProperties.READONLY.getLabel(), this.getReadonly());
	}

	private static final String SIMPLE_NAME = SchemaUtils
			.getSingularName(SchemaObjectProperties.ARGUMENTS.getLabel());

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractDbObjectCollection#getSimpleName()
	 */
	@Override
	protected String getSimpleName() {
		return SIMPLE_NAME;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public NamedArgumentCollection<?> getParent() {
		return (NamedArgumentCollection<?>) super.getParent();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.AbstractSchemaObject#setName(java.lang.String)
	 */
	@Override
	public NamedArgument setName(String name) {
		super.setName(name);
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.ReadOnlyProperty#getReadOnly()
	 */
	@Override
	public Boolean getReadonly() {
		return readonly;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.ReadOnlyProperty#setReadOnly(java.lang.Boolean)
	 */
	@Override
	public NamedArgument setReadonly(Boolean readOnly) {
		this.readonly = readOnly;
		return this;
	}

}
