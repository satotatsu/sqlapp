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

import static com.sqlapp.util.CommonUtils.cast;
import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.object.SubPartitionsProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * パーティション
 * 
 * @author satoh
 * 
 */
public final class Partition extends AbstractPartition<Partition> implements 
		HasParent<PartitionCollection>
	, SubPartitionsProperty<Partition>
{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3887238098717065317L;

	/**
	 * コンストラクタ
	 */
	public Partition() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param partitionName
	 */
	public Partition(String partitionName) {
		super(partitionName);
	}
	
	@Override
	protected Supplier<Partition> newInstance(){
		return ()->new Partition();
	}

	/** 子パーティション情報 */
	private SubPartitionCollection subPartitions = new SubPartitionCollection(this);

	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		super.toStringDetail(builder);
		if (!isEmpty(subPartitions)) {
			builder.add(SchemaObjectProperties.SUB_PARTITIONS, subPartitions);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedDdlObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof Partition)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		Partition val = cast(obj);
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
		if (!isEmpty(subPartitions)) {
			subPartitions.writeXml(stax);
		}
	}

	/**
	 * @return the subPartitions
	 */
	@Override
	public SubPartitionCollection getSubPartitions() {
		return subPartitions;
	}

	/**
	 * @param subPartitions
	 *            the subPartitions to set
	 */
	protected Partition setSubPartitions(SubPartitionCollection subPartitions) {
		if (subPartitions!=null){
			subPartitions.setParent(this);
		}
		this.subPartitions = subPartitions;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractSchemaObject#getParent()
	 */
	@Override
	public PartitionCollection getParent() {
		return (PartitionCollection) super.getParent();
	}

	@Override
	protected PartitionXmlReaderHandler getDbObjectXmlReaderHandler() {
		return new PartitionXmlReaderHandler();
	}
}
