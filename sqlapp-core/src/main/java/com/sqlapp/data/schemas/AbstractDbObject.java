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

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.CatalogNameProperty;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.HashCodeBuilder;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * DBオブジェクト共通の抽象クラス
 * 
 * @author satoh
 * 
 */
public abstract class AbstractDbObject<T extends AbstractDbObject<T>> extends AbstractBaseDbObject<T>
	implements CatalogNameProperty<T>
	{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 210101856540540272L;
	/** カタログ名 */
	private String catalogName = null;

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	protected AbstractDbObject() {
	}

	@SuppressWarnings("unchecked")
	protected T instance() {
		return (T) this;
	}

	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		if (!(obj instanceof AbstractDbObject)) {
			return false;
		}
		@SuppressWarnings("unchecked")
		T val = (T) obj;
		if (!equalsCatalogName(val, equalsHandler)) {
			return false;
		}
		return true;
	}

	protected boolean equalsCatalogName(T val, EqualsHandler equalsHandler) {
		if (this instanceof HasParent) {
			HasParent<?> hasParent = (HasParent<?>) this;
			HasParent<?> valHasParent = (HasParent<?>) val;
			if (hasParent.getParent() == null
					&& valHasParent.getParent() == null) {
				if (!equals(SchemaProperties.CATALOG_NAME, val, equalsHandler)) {
					return false;
				}
			}
		} else {
			if (!equals(SchemaProperties.CATALOG_NAME, val, equalsHandler, EqualsUtils.getEqualsSupplier(this.catalogName, val.getCatalogName()))) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected void toString(ToStringBuilder builder) {
		builder.add(SchemaProperties.CATALOG_NAME, getCatalogName());
		builder.add(SchemaProperties.ID, getId());
		builder.add(SchemaProperties.SPECIFICS, getSpecifics());
		builder.add(SchemaProperties.STATISTICS, getStatistics());
		builder.add(SchemaProperties.CREATED_AT, getCreatedAt());
		builder.add(SchemaProperties.LAST_ALTERED_AT, getLastAlteredAt());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		HashCodeBuilder builder = new HashCodeBuilder();
		builder.append(this.getCatalogName());
		builder.append(this.getId());
		builder.append(this.getCreatedAt());
		builder.append(this.getLastAlteredAt());
		builder.append(this.getDialect());
		return builder.hashCode();
	}

	/**
	 * 共通属性要素のXMLへの書き込み
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	protected void writeCommonNameAttribute(StaxWriter stax)
			throws XMLStreamException {
		writeCatalogNameAttribute(stax);
	}

	protected void writeCatalogNameAttribute(StaxWriter stax)
			throws XMLStreamException {
		if (this instanceof HasParent) {
			HasParent<?> hasParent = (HasParent<?>) this;
			if (hasParent.getParent() == null) {
				stax.writeAttribute(SchemaProperties.CATALOG_NAME.getLabel(), this.getCatalogName());
			}
		} else {
			stax.writeAttribute(SchemaProperties.CATALOG_NAME.getLabel(), this.getCatalogName());
		}
	}

	@Override
	public String getCatalogName() {
		if (this instanceof HasParent) {
			CatalogNameProperty<?> catalogNameProperty=this.getAncestor(o->o instanceof CatalogNameProperty);
			if (catalogNameProperty!=null) {
				return catalogNameProperty.getCatalogName();
			}
			Catalog catalog=this.getAncestor(p->p instanceof Catalog);
			if (catalog!=null){
				return catalog.getName();
			}
		}
		return catalogName;
	}

	@Override
	public T setCatalogName(String catalogName) {
		this.catalogName = catalogName;
		return instance();
	}

}
