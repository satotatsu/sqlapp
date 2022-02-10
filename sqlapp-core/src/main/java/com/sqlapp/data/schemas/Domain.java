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
import static com.sqlapp.util.CommonUtils.trim;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.CheckProperty;
import com.sqlapp.data.schemas.properties.DeferrabilityProperty;
import com.sqlapp.data.schemas.properties.OnUpdateProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Domain
 * 
 */
public final class Domain extends AbstractColumn<Domain> implements
		HasParent<DomainCollection>,OnUpdateProperty<Domain>,DeferrabilityProperty<Domain>
		,CheckProperty<Domain>{
	/** serialVersionUID */
	private static final long serialVersionUID = 1L;

	public Domain() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public Domain(String name) {
		super(name);
	}

	@Override
	protected Supplier<Domain> newInstance(){
		return ()->new Domain();
	}

	/** on update */
	private String onUpdate = null;
	/** 制約式 */
	private String check = null;
	/** 制約のチェックの遅延 */
	private Deferrability deferrability=null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractColumn#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof Domain)) {
			return false;
		}
		Domain val = (Domain) obj;
		if (!equals(SchemaProperties.CHECK, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ON_UPDATE, val, equalsHandler)) {
			return false;
		}
		if (!isEmpty(this.getCheck())) {
			if (!equals(SchemaProperties.DEFERRABILITY, val, equalsHandler)) {
				return false;
			}
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		if (!isEmpty(getCheck())) {
			builder.add(SchemaProperties.CHECK, this.getCheck());
		}
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.CHECK.getLabel(), this.getCheck());
		stax.writeAttribute(SchemaProperties.ON_UPDATE.getLabel(), this.getOnUpdate());
		if (!isEmpty(this.getCheck())) {
			stax.writeAttribute(SchemaProperties.DEFERRABILITY.getLabel(), this.getDeferrability());
		}
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
	}

	public String getCheck() {
		return check;
	}

	public Domain setCheck(String check) {
		this.check = trim(check);
		return instance();
	}

	@Override
	public Deferrability getDeferrability() {
		return deferrability;
	}

	@Override
	public Domain setDeferrability(Deferrability deferrability) {
		this.deferrability = deferrability;
		return instance();
	}

	@Override
	public String getOnUpdate() {
		return onUpdate;
	}

	@Override
	public Domain setOnUpdate(String onUpdate) {
		this.onUpdate=onUpdate;
		return instance();
	}

	@Override
	public DomainCollection getParent() {
		return (DomainCollection) super.getParent();
	}

}