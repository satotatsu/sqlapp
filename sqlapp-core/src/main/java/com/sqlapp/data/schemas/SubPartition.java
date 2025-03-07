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

import static com.sqlapp.util.CommonUtils.cast;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * パーティション
 * 
 * @author satoh
 * 
 */
public final class SubPartition extends AbstractPartition<SubPartition> implements 
		HasParent<SubPartitionCollection>
	{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3887238098717065317L;

	/**
	 * コンストラクタ
	 */
	public SubPartition() {
	}
	

	/**
	 * コンストラクタ
	 * 
	 * @param name
	 */
	public SubPartition(String name) {
		super(name);
	}

	@Override
	protected Supplier<SubPartition> newInstance(){
		return ()->new SubPartition();
	}

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		super.toStringDetail(builder);
	}

	@Override
	protected String getSimpleName() {
		return "subPartition";
	}

	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedDdlObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof SubPartition)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		SubPartition val = cast(obj);
		if (!equals(SchemaObjectProperties.SUB_PARTITIONS, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
	}

	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public SubPartitionCollection getParent() {
		return (SubPartitionCollection) super.getParent();
	}

	@Override
	protected SubPartitionXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new SubPartitionXmlReaderHandler();
	}
}
