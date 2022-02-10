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

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.properties.BoundaryValueOnRightProperty;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;
import com.sqlapp.data.schemas.properties.PartitionFunctionValuesProperty;
import com.sqlapp.data.schemas.properties.PrecisionProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.HashCodeBuilder;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;
import com.sqlapp.util.UniqueList;

/**
 * パーティション関数に対応したオブジェクト(SQLServer2005以降専用)
 * 
 * @author satoh
 * 
 */
public final class PartitionFunction extends
		AbstractNamedObject<PartitionFunction> implements
		HasParent<PartitionFunctionCollection>,
		DataTypeLengthProperties<PartitionFunction>
	,PrecisionProperty<PartitionFunction>
	,BoundaryValueOnRightProperty<PartitionFunction>
	,PartitionFunctionValuesProperty<PartitionFunction>{
	/**  serialVersionUID */
	private static final long serialVersionUID = 1L;
	/**
	 * java.sql.Types(VARCHAR,CHAR…)
	 */
	@SuppressWarnings("unused")
	private final DataType dataType = null;
	/**
	 * DB固有の型
	 */
	@SuppressWarnings("unused")
	private final String dataTypeName = null;
	/** 境界値を右に含む */
	private boolean boundaryValueOnRight = (Boolean)SchemaProperties.BOUNDARY_VALUE_ON_RIGHT.getDefaultValue();
	/** 最大長 */
	private Long maxLength = (Long)SchemaProperties.LENGTH.getDefaultValue();
	/** 項目のOctet長 */
	private Long octetLength = (Long)SchemaProperties.OCTET_LENGTH.getDefaultValue();
	/**
	 * 数値ベースの場合は、パラメータの有効桁数。それ以外の場合は0
	 */
	private Integer precision =(Integer)SchemaProperties.PRECISION.getDefaultValue();
	/** 小数点以下の桁数 */
	private Integer scale = (Integer)SchemaProperties.SCALE.getDefaultValue();
	/**
	 * パーティションの境界値のセット
	 */
	private PatitionFunctionValues values = new PatitionFunctionValues(this);

	public PartitionFunction() {
	}

	public PartitionFunction(final String functrionName) {
		super(functrionName);
	}

	@Override
	protected Supplier<PartitionFunction> newInstance(){
		return ()->new PartitionFunction();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj, final EqualsHandler equalsHandler) {
		if (!(obj instanceof PartitionFunction)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		final PartitionFunction val = (PartitionFunction) obj;
		if (!equals(SchemaProperties.DATA_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_TYPE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.LENGTH, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.OCTET_LENGTH, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PRECISION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SCALE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.BOUNDARY_VALUE_ON_RIGHT, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.PARTITION_FUNCTION_VALUES, val, equalsHandler)) {
			return false;
		}
		return equalsHandler.equalsResult(this, obj);
	}

	@Override
	protected void toStringDetail(final ToStringBuilder builder) {
		builder.add(SchemaProperties.DATA_TYPE, this.getDataType());
		builder.add(SchemaProperties.DATA_TYPE_NAME, this.getDataTypeName());
		builder.add(SchemaProperties.LENGTH, this.getLength());
		builder.add(SchemaProperties.OCTET_LENGTH, this.getOctetLength());
		builder.add(SchemaProperties.PRECISION, this.getPrecision());
		builder.add(SchemaProperties.SCALE, this.getScale());
		builder.add(SchemaProperties.BOUNDARY_VALUE_ON_RIGHT, this.isBoundaryValueOnRight());
		builder.add(SchemaProperties.VALUES, this.getValues());
	}

	@Override
	protected void writeXmlOptionalAttributes(final StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.DATA_TYPE.getLabel(), this.getDataType());
		stax.writeAttribute(SchemaProperties.DATA_TYPE_NAME.getLabel(), this.getDataTypeName());
		stax.writeAttribute(SchemaProperties.LENGTH.getLabel(), this.getLength());
		if (this.getOctetLength()!=null&&this.getOctetLength() > 0) {
			stax.writeAttribute(SchemaProperties.OCTET_LENGTH.getLabel(), getOctetLength());
		}
		if (this.getPrecision()!=null&&this.getPrecision().intValue() > 0) {
			stax.writeAttribute(SchemaProperties.PRECISION.getLabel(), getPrecision());
		}
		if (getScale()!=null&& getScale().intValue() != 0) {
			stax.writeAttribute(SchemaProperties.SCALE.getLabel(), getScale());
		}
		stax.writeAttribute(SchemaProperties.BOUNDARY_VALUE_ON_RIGHT.getLabel(),
				this.isBoundaryValueOnRight());
	}

	@Override
	protected void writeXmlOptionalValues(final StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		this.getValues().writeXml(stax);
	}

	/**
	 * @return the boundaryValueOnRight
	 */
	@Override
	public boolean isBoundaryValueOnRight() {
		return boundaryValueOnRight;
	}

	/**
	 * @param boundaryValueOnRight
	 *            the boundaryValueOnRight to set
	 */
	@Override
	public PartitionFunction setBoundaryValueOnRight(
			final boolean boundaryValueOnRight) {
		this.boundaryValueOnRight = boundaryValueOnRight;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.LengthProperties#getMaxLength()
	 */
	@Override
	public Long getLength() {
		return maxLength;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.LengthProperties#setMaxLength(long)
	 */
	@Override
	public PartitionFunction setLength(final long maxLength) {
		this.maxLength = maxLength;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.LengthProperties#setMaxLength(java.lang.Number)
	 */
	@Override
	public PartitionFunction setLength(final Number maxLength) {
		this.maxLength = Converters.getDefault().convertObject(maxLength,
				Long.class);
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.LengthProperties#getOctetLength()
	 */
	@Override
	public Long getOctetLength() {
		return octetLength;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.LengthProperties#setOctetLength(long)
	 */
	@Override
	public PartitionFunction setOctetLength(final long octetLength) {
		this.octetLength = octetLength;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.LengthProperties#setOctetLength(java.lang.Number)
	 */
	@Override
	public PartitionFunction setOctetLength(final Number octetLength) {
		this.octetLength = Converters.getDefault().convertObject(octetLength,
				Long.class);
		return instance();
	}

	/**
	 * @return the precision
	 */
	@Override
	public Integer getPrecision() {
		return precision;
	}

	/**
	 * @param precision
	 *            the precision to set
	 */
	@Override
	public PartitionFunction setPrecision(final int precision) {
		this.precision = precision;
		return instance();
	}

	@Override
	public PartitionFunction setPrecision(final Number precision) {
		if (precision==null){
			this.precision=null;
		} else{
			this.precision=precision.intValue();
		}
		return instance();
	}
	
	/**
	 * @param scale
	 *            the scale to set
	 */
	@Override
	public PartitionFunction setScale(final int scale) {
		this.scale = scale;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.ScaleProperty#getScale()
	 */
	@Override
	public Integer getScale() {
		return scale;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.ScaleProperty#setScale(java.lang.Number)
	 */
	@Override
	public PartitionFunction setScale(final Number scale) {
		this.scale = Converters.getDefault()
				.convertObject(scale, Integer.class);
		return instance();
	}

	/**
	 * パーティション分割数を返します
	 * 
	 */
	public int getFanout() {
		return this.values.size();
	}

	/**
	 * 境界値を追加します
	 * 
	 * @param val
	 */
	public PartitionFunction addValues(final String val) {
		values.add(val);
		return instance();
	}

	/**
	 * パーティションの境界値のセットを取得します
	 * 
	 */
	@Override
	public PatitionFunctionValues getValues() {
		return values;
	}


	@Override
	public PartitionFunction setValues(final PatitionFunctionValues values) {
		this.values=values;
		if (values!=null){
			values.setPartitionFunction(this);
		}
		return instance();
	}

	@Override
	public PartitionFunction addValue(final String val) {
		if (this.getValues()==null){
			setValues(new PatitionFunctionValues());
		}
		return instance();
	}

	/**
	 * パーティションの境界値の文字列のセットを適切なオブジェクトに変換して追加します
	 * 
	 */
	protected void addStringValues(final Set<String> values) {
		this.values.addAll(values);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractNamedObject#getParent()
	 */
	@Override
	public PartitionFunctionCollection getParent() {
		return (PartitionFunctionCollection) super.getParent();
	}

	public static class PatitionFunctionValues extends UniqueList<String>{
		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 1L;
		private PartitionFunction partitionFunction;

		public PatitionFunctionValues(){
		}

		private PatitionFunctionValues(final PartitionFunction partitionFunction){
			this.partitionFunction=partitionFunction;
		}
		
		private void setPartitionFunction(final PartitionFunction partitionFunction){
			this.partitionFunction=partitionFunction;
		}
		
		public void addAll(final String...args){
			this.addAll(CommonUtils.list(args));
		}
		
		protected void writeXml(final StaxWriter stax)
				throws XMLStreamException {
			if (this.size()>0) {
				stax.newLine();
				stax.indent();
				final Set<String> set=CommonUtils.linkedSet(this);
				stax.writeElementValues(SchemaProperties.VALUES.getLabel(), set);
			}
		}
		
		@Override
		public boolean equals(final Object obj){
			if (!super.equals(obj)){
				return false;
			}
			if (!(this instanceof PatitionFunctionValues)){
				return false;
			}
			return true;
		}
		
		@Override
		protected void validate(){
			super.validate();
			Collections.sort(this.inner, new Comparator<String>(){
				@Override
				public int compare(final String o1, final String o2) {
					if(partitionFunction!=null&&partitionFunction.getDataType()!=null){
						final Class<?> clazz=partitionFunction.getDataType().getDefaultClass();
						final Object conv1=Converters.getDefault().convertObject(o1, clazz);
						final Object conv2=Converters.getDefault().convertObject(o2, clazz);
						return CommonUtils.compare(conv1, conv2);
					}
					return CommonUtils.compare(o1, o2);
				}
			});
		}
		
		
		@Override
		public int hashCode() {
			final HashCodeBuilder builder=new HashCodeBuilder(super.hashCode());
			builder.append(partitionFunction.hashCode());
			return builder.hashCode();
		}

		
		@Override
		public PatitionFunctionValues clone(){
			final PatitionFunctionValues clone=(PatitionFunctionValues)super.clone();
			return clone;
		}
	}

}
