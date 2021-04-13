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

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.properties.CompressionProperty;
import com.sqlapp.data.schemas.properties.CompressionTypeProperty;
import com.sqlapp.data.schemas.properties.HighValueInclusiveProperty;
import com.sqlapp.data.schemas.properties.HighValueProperty;
import com.sqlapp.data.schemas.properties.LowValueInclusiveProperty;
import com.sqlapp.data.schemas.properties.LowValueProperty;
import com.sqlapp.data.schemas.properties.complex.IndexTableSpaceProperty;
import com.sqlapp.data.schemas.properties.complex.LobTableSpaceProperty;
import com.sqlapp.data.schemas.properties.complex.TableSpaceProperty;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * パーティション
 * 
 * @author satoh
 * 
 */
public abstract class AbstractPartition<T extends AbstractPartition<T>> extends AbstractSchemaObject<T> implements 
	 CompressionProperty<T>
	,CompressionTypeProperty<T>
	,LowValueProperty<T>
	,HighValueProperty<T>
	, LowValueInclusiveProperty<T>,HighValueInclusiveProperty<T>
	, TableSpaceProperty<T>
	, IndexTableSpaceProperty<T>
	, LobTableSpaceProperty<T>
{
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3887238098717065317L;

	/**
	 * コンストラクタ
	 */
	public AbstractPartition() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param partitionName
	 */
	public AbstractPartition(final String partitionName) {
		super(partitionName);
	}

	/** 最小値 */
	private String lowValue = null;
	/** 最小値をこのパーティションに含むか */
	private boolean lowValueInclusive = (Boolean)SchemaProperties.LOW_VALUE_INCLUSIVE.getDefaultValue();
	/** 最大値 */
	private String highValue = null;
	/** 最大値をこのパーティションに含むか */
	private boolean highValueInclusive = (Boolean)SchemaProperties.HIGH_VALUE_INCLUSIVE.getDefaultValue();
	/** 圧縮 */
	private boolean compression = (Boolean)SchemaProperties.COMPRESSION.getDefaultValue();
	/** 圧縮タイプ */
	private String compressionType = null;
	/** テーブルスペース */
	@SuppressWarnings("unused")
	private final TableSpace tableSpace = null;
	/** LOBテーブルスペース */
	@SuppressWarnings("unused")
	private final TableSpace lobTableSpace = null;
	/** INDEXテーブルスペース */
	@SuppressWarnings("unused")
	private final TableSpace indexTableSpace = null;

	@Override
	protected void toStringDetail(final ToStringBuilder builder) {
		builder.add(SchemaProperties.LOW_VALUE, this.getLowValue());
		if (this.getLowValue()!=null){
			builder.add(SchemaProperties.LOW_VALUE_INCLUSIVE, this.isLowValueInclusive());
		}
		builder.add(SchemaProperties.HIGH_VALUE, this.getHighValue());
		if (this.getHighValue()!=null){
			builder.add(SchemaProperties.HIGH_VALUE_INCLUSIVE, this.isHighValueInclusive());
		}
		if (compression) {
			builder.add(SchemaProperties.COMPRESSION, this.compression);
			builder.add(SchemaProperties.COMPRESSION_TYPE, this.compressionType);
		}
		builder.add(SchemaProperties.TABLE_SPACE_NAME, this.getTableSpaceName());
		builder.add(SchemaProperties.LOB_TABLE_SPACE_NAME, this.getLobTableSpaceName());
		builder.add(SchemaProperties.INDEX_TABLE_SPACE_NAME, this.getIndexTableSpaceName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedDdlObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
		if (!(obj instanceof AbstractPartition)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		final T val = cast(obj);
		if (!equals(SchemaProperties.LOW_VALUE, val, equalsHandler)) {
			return false;
		}
		if (this.getLowValue()!=null||val.getLowValue()!=null){
			if (!equals(SchemaProperties.LOW_VALUE_INCLUSIVE, val, equalsHandler)) {
				return false;
			}
		}
		if (!equals(SchemaProperties.HIGH_VALUE, val, equalsHandler)) {
			return false;
		}
		if (this.getHighValue()!=null||val.getHighValue()!=null){
			if (!equals(SchemaProperties.HIGH_VALUE_INCLUSIVE, val, equalsHandler)) {
				return false;
			}
		}
		if (!equals(
				SchemaProperties.TABLE_SPACE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(
				SchemaProperties.LOB_TABLE_SPACE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(
				SchemaProperties.INDEX_TABLE_SPACE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.COMPRESSION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.COMPRESSION_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaObjectProperties.SUB_PARTITIONS, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void writeXmlOptionalAttributes(final StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.LOW_VALUE.getLabel(), this.getLowValue());
		if (this.getLowValue()!=null){
			stax.writeAttribute(SchemaProperties.LOW_VALUE_INCLUSIVE.getLabel(), this.isLowValueInclusive());
		}
		stax.writeAttribute(SchemaProperties.HIGH_VALUE.getLabel(), this.getHighValue());
		if (this.getHighValue()!=null){
			stax.writeAttribute(SchemaProperties.HIGH_VALUE_INCLUSIVE.getLabel(), this.isHighValueInclusive());
		}
		if (this.isCompression()) {
			stax.writeAttribute(SchemaProperties.COMPRESSION.getLabel(), this.isCompression());
			stax.writeAttribute(SchemaProperties.COMPRESSION_TYPE.getLabel(), this.getCompressionType());
		}
		stax.writeAttribute(SchemaProperties.TABLE_SPACE_NAME.getLabel(), this.getTableSpaceName());
		stax.writeAttribute(SchemaProperties.LOB_TABLE_SPACE_NAME.getLabel(), this.getLobTableSpaceName());
		stax.writeAttribute(SchemaProperties.INDEX_TABLE_SPACE_NAME.getLabel(), this.getIndexTableSpaceName());
	}

	@Override
	protected void writeXmlOptionalValues(final StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
	}

	/**
	 * プロパティのコピー
	 * 
	 * @param clone
	 */
	@Override
	protected void cloneProperties(final T clone) {
		super.cloneProperties(clone);
	}

	/**
	 * @return the lowValue
	 */
	@Override
	public String getLowValue() {
		return lowValue;
	}

	/**
	 * @param lowValue the lowValue to set
	 */
	@Override
	public T setLowValue(final String lowValue) {
		this.lowValue = lowValue;
		return instance();
	}

	/**
	 * @return the lowValueInclusive
	 */
	@Override
	public boolean isLowValueInclusive() {
		return lowValueInclusive;
	}

	/**
	 * @param lowValueInclusive the lowValueInclusive to set
	 */
	@Override
	public T setLowValueInclusive(final boolean lowValueInclusive) {
		this.lowValueInclusive = lowValueInclusive;
		return instance();
	}

	@Override
	public String getHighValue() {
		return highValue;
	}

	@Override
	public T setHighValue(final String highValue) {
		this.highValue = highValue;
		return instance();
	}

	/**
	 * @return the highValueInclusive
	 */
	@Override
	public boolean isHighValueInclusive() {
		return highValueInclusive;
	}

	/**
	 * @param highValueInclusive the highValueInclusive to set
	 */
	@Override
	public T setHighValueInclusive(final boolean highValueInclusive) {
		this.highValueInclusive = highValueInclusive;
		return instance();
	}

	@Override
	public boolean isCompression() {
		return compression;
	}

	@Override
	public T setCompression(final boolean compression) {
		this.compression = compression;
		return instance();
	}

	@Override
	public T setCompressionType(final String compressionType) {
		this.compressionType = compressionType;
		return instance();
	}

	@Override
	public String getCompressionType() {
		return compressionType;
	}
	
	/**
	 * @return the partitionInfo
	 */
	public Partitioning getPartitioning() {
		return this.getAncestor(Partitioning.class);
	}
	
	public Partition toPartition(){
		if (this instanceof Partition){
			return (Partition)this;
		}
		final Partition partition=new Partition();
		SchemaUtils.copySchemaProperties(this, partition);
		return partition;
	}

	public SubPartition toSubPartition(){
		if (this instanceof SubPartition){
			return (SubPartition)this;
		}
		final SubPartition partition=new SubPartition();
		SchemaUtils.copySchemaProperties(this, partition);
		return partition;
	}

}
