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
import static com.sqlapp.util.CommonUtils.linkedSet;

import java.util.Set;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.properties.IdentityLastValueProperty;
import com.sqlapp.data.schemas.properties.IdentityMaxValueProperty;
import com.sqlapp.data.schemas.properties.IdentityMinValueProperty;
import com.sqlapp.data.schemas.properties.IdentityOrderProperty;
import com.sqlapp.data.schemas.properties.IdentityProperty;
import com.sqlapp.data.schemas.properties.IdentityStartValueProperty;
import com.sqlapp.data.schemas.properties.IdentityStepProperty;
import com.sqlapp.data.schemas.properties.DataTypeSetProperties;
import com.sqlapp.data.schemas.properties.DefaultValueProperty;
import com.sqlapp.data.schemas.properties.FormulaPersistedProperty;
import com.sqlapp.data.schemas.properties.FormulaProperty;
import com.sqlapp.data.schemas.properties.IdentityCacheProperty;
import com.sqlapp.data.schemas.properties.IdentityCacheSizeProperty;
import com.sqlapp.data.schemas.properties.IdentityCycleProperty;
import com.sqlapp.data.schemas.properties.IdentityGenerationTypeProperty;
import com.sqlapp.data.schemas.properties.NotNullProperty;
import com.sqlapp.data.schemas.properties.StringUnitsProperty;
import com.sqlapp.data.schemas.properties.ValuesProperty;
import com.sqlapp.data.schemas.properties.complex.SequenceProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * AbstractColumn
 * 
 */
public abstract class AbstractColumn<T extends AbstractColumn<T>> extends
		AbstractSchemaObject<T> implements DataTypeSetProperties<T>,NotNullProperty<T>,
		ValuesProperty<T>, DefaultValueProperty<T>, IdentityProperty<T>
		, SequenceProperty<T>
		, IdentityStartValueProperty<T>
		, IdentityMaxValueProperty<T>
		, IdentityMinValueProperty<T>
		, IdentityStepProperty<T>
		, IdentityLastValueProperty<T>
		, IdentityCacheProperty<T>
		, IdentityCacheSizeProperty<T>
		, IdentityCycleProperty<T>
		, IdentityOrderProperty<T>
		, IdentityGenerationTypeProperty<T>
		, StringUnitsProperty<T>, FormulaProperty<T>, FormulaPersistedProperty<T> {
	/** serialVersionUID */
	private static final long serialVersionUID = 8775419796577781694L;

	protected AbstractColumn() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param columnName
	 */
	protected AbstractColumn(String columnName) {
		super(columnName);
	}

	private static final Sequence DEFAULT_VALUE_SEQUENCE=new Sequence();
	
	/** notNull */
	private boolean notNull = (Boolean)SchemaProperties.NOT_NULL.getDefaultValue();
	/** 最大長 */
	private Long length = null;
	/** 項目のOctet長 */
	private Long octetLength = null;
	/** カラムの文字列のセマンティックス */
	@SuppressWarnings("unused")
	private CharacterSemantics characterSemantics = (CharacterSemantics)SchemaProperties.CHARACTER_SEMANTICS.getDefaultValue();
	/** characterSetName */
	@SuppressWarnings("unused")
	private String characterSet = (String)SchemaProperties.CHARACTER_SET.getDefaultValue();
	/** collationName */
	@SuppressWarnings("unused")
	private String collation = (String)SchemaProperties.COLLATION.getDefaultValue();
	/** 自動採番 */
	private boolean identity = (Boolean)SchemaProperties.IDENTITY.getDefaultValue();
	/** 自動採番生成タイプ */
	private IdentityGenerationType identityGenerationType=(IdentityGenerationType)SchemaProperties.IDENTITY_GENERATION_TYPE.getDefaultValue();
	/** シーケンス */
	@SuppressWarnings("unused")
	private Sequence sequence = null;
	/** java.sql.Types(VARCHAR,CHAR…) */
	private DataType dataType = (DataType)SchemaProperties.DATA_TYPE.getDefaultValue();
	/** DB固有の型 */
	protected String dataTypeName = (String)SchemaProperties.DATA_TYPE_NAME.getDefaultValue();
	/** 小数点以下の桁数 */
	private Integer scale = null;
	/** DB設定上のデフォルト値 */
	private String defaultValue = null;
	/** 計算式 */
	private String formula = null;
	/** 計算式の保存 */
	private boolean formulaPersisted = (Boolean)SchemaProperties.FORMULA_PERSISTED.getDefaultValue();
	/** 配列型の場合の次元数(通常:0) */
	private int arrayDimension = (int)SchemaProperties.ARRAY_DIMENSION.getDefaultValue();
	/** 配列(1次元)の下限 */
	private int arrayDimensionLowerBound = (int)SchemaProperties.ARRAY_DIMENSION_LOWER_BOUND.getDefaultValue();
	/** 配列(1次元)の上限 */
	private int arrayDimensionUpperBound = (int)SchemaProperties.ARRAY_DIMENSION_UPPER_BOUND.getDefaultValue();
	/** ENUM、SET型の値のセット */
	private Set<String> values = linkedSet();
	/** DB2 String unit(eg. CODEUNITS16) */
	private String stringUnits = null;

	/**
	 * @param characterSet
	 *            the characterSet to set
	 */
	@Override
	@SuppressWarnings("unchecked")
	public T setCharacterSet(String characterSet) {
		if (CommonUtils.eq(this.getCharacterSet(), characterSet)){
			this.characterSet = null;
		} else{
			this.characterSet = characterSet;
		}
		return (T) this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.CollationName#setCollationName(java.lang.String)
	 */
	@Override
	public T setCollation(String value) {
		if (CommonUtils.eq(this.getCollation(), value)){
			this.collation = null;
		} else{
			this.collation = value;
		}
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.StringUnits#getStringUnits()
	 */
	@Override
	public String getStringUnits() {
		return stringUnits;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.StringUnits#setStringUnits(java.lang.String)
	 */
	@Override
	public T setStringUnits(String stringUnits) {
		this.stringUnits = stringUnits;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof AbstractColumn)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		@SuppressWarnings("unchecked")
		T val = (T) obj;
		if (!equals(SchemaProperties.DATA_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_TYPE_NAME, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getDataTypeName(), val.getDataTypeName()))) {
			return false;
		}
		if (this.getDataType()==null||this.getDataType().isFixedSize()){
			if (!equals(SchemaProperties.LENGTH, val, equalsHandler)) {
				return false;
			}
			if (!equals(SchemaProperties.OCTET_LENGTH, val, equalsHandler)) {
				return false;
			}
		}
		if (this.getDataType()==null||!this.getDataType().isFixedScale()){
			if (!equals(SchemaProperties.SCALE, val, equalsHandler)) {
				return false;
			}
		}
		if (!equals(SchemaProperties.CHARACTER_SEMANTICS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.CHARACTER_SET, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getCharacterSet(), val.getCharacterSet()))) {
			return false;
		}
		if (!equals(SchemaProperties.COLLATION, val, equalsHandler, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getCollation(),
						val.getCollation()))) {
			return false;
		}
		if (!equals(SchemaProperties.NOT_NULL, val, equalsHandler)) {
			return false;
		}
		boolean auto = equals(SchemaProperties.IDENTITY, val, equalsHandler);
		if (!auto) {
			return false;
		}
		if (auto) {
			if (!equals(SchemaProperties.IDENTITY_GENERATION_TYPE, val, equalsHandler)) {
				return false;
			}
			if (!equals(SchemaProperties.IDENTITY_START_VALUE, val, equalsHandler)) {
				return false;
			}
			if (!equals(SchemaProperties.IDENTITY_MAX_VALUE, val, equalsHandler)) {
				return false;
			}
			if (!equals(SchemaProperties.IDENTITY_MIN_VALUE, val, equalsHandler)) {
				return false;
			}
			if (!equals(SchemaProperties.IDENTITY_STEP, val, equalsHandler)) {
				return false;
			}
			if (!equals(SchemaProperties.IDENTITY_LAST_VALUE, val, equalsHandler)) {
				return false;
			}
			if (!equals(SchemaProperties.IDENTITY_CACHE, val, equalsHandler)) {
				return false;
			}
			if (!equals(SchemaProperties.IDENTITY_CACHE_SIZE, val, equalsHandler)) {
				return false;
			}
			if (!equals(SchemaProperties.IDENTITY_CYCLE, val, equalsHandler)) {
				return false;
			}
			if (!equals(SchemaProperties.IDENTITY_ORDER, val, equalsHandler)) {
				return false;
			}
		}
		if (!CommonUtils.eq(this.getSequenceSchemaName(), this.getSchemaName())||!CommonUtils.eq(val.getSequenceSchemaName(), val.getSchemaName())){
			if (!equals(SchemaProperties.SEQUENCE_SCHEMA_NAME, val, equalsHandler)) {
				return false;
			}
		}
		if (!equals(SchemaProperties.SEQUENCE_NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DEFAULT_VALUE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.FORMULA, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.FORMULA_PERSISTED, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ARRAY_DIMENSION, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ARRAY_DIMENSION_LOWER_BOUND, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.ARRAY_DIMENSION_UPPER_BOUND, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.VALUES, val, equalsHandler)) {
			return false;
		}
		if (!equals(
				SchemaProperties.STRING_UNITS, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getStringUnits(),
						val.getStringUnits()))) {
			return false;
		}
		return true;
	}

	@Override
	public boolean isNotNull() {
		return this.notNull;
	}

	@Override
	public T setNotNull(boolean notNull) {
		this.notNull=notNull;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.LengthProperties#getMaxLength()
	 */
	@Override
	public Long getLength() {
		return length;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.LengthProperties#setMaxLength(long)
	 */
	@Override
	public T setLength(long maxLength) {
		this.length = maxLength;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.LengthProperties#setMaxLength(java.lang.Number)
	 */
	@Override
	public T setLength(Number maxLength) {
		this.length = Converters.getDefault().convertObject(maxLength,
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
	public T setOctetLength(long octetLength) {
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
	public T setOctetLength(Number octetLength) {
		this.octetLength = Converters.getDefault().convertObject(octetLength,
				Long.class);
		return instance();
	}

	@Override
	public boolean isIdentity() {
		return identity;
	}

	@Override
	public T setIdentity(boolean value) {
		this.identity = value;
		return instance();
	}

	/**
	 * @return the generationType
	 */
	@Override
	public IdentityGenerationType getIdentityGenerationType() {
		return identityGenerationType;
	}

	@Override
	public T setIdentityGenerationType(IdentityGenerationType identityGenerationType) {
		this.identityGenerationType = identityGenerationType;
		return instance();
	}

	@Override
	public Long getIdentityStep() {
		if (this.getSequence() != null && getSequence().getIncrementBy() != null) {
			return getSequence().getIncrementBy().longValue();
		}
		return null;
	}

	@Override
	public T setIdentityStep(Number value) {
		if (this.getSequence() == null) {
			this.setSequence(new Sequence());
		}
		this.getSequence().setIncrementBy(value);
		return instance();
	}

	@Override
	public T setIdentityStep(long value) {
		if (this.getSequence() == null) {
			this.setSequence(new Sequence());
		}
		this.getSequence().setIncrementBy(value);
		return instance();
	}

	@Override
	public Long getIdentityLastValue() {
		if (this.getSequence() != null && this.getSequence().getLastValue() != null) {
			return this.getSequence().getLastValue().longValue();
		}
		return null;
	}

	@Override
	public T setIdentityLastValue(Number value) {
		if (this.getSequence() == null) {
			this.setSequence(new Sequence());
		}
		this.getSequence().setLastValue(value);
		return instance();
	}

	@Override
	public T setIdentityLastValue(long value) {
		if (this.getSequence() == null) {
			this.setSequence(new Sequence());
		}
		this.getSequence().setLastValue(value);
		return instance();
	}

	@Override
	public Long getIdentityStartValue() {
		if (this.getSequence() != null && this.getSequence().getStartValue() != null) {
			return this.getSequence().getStartValue().longValue();
		}
		return null;
	}

	@Override
	public T setIdentityStartValue(Number value) {
		if (this.getSequence() == null) {
			this.setSequence(new Sequence());
		}
		this.getSequence().setStartValue(value);
		return instance();
	}

	@Override
	public T setIdentityStartValue(long value) {
		if (this.getSequence() == null) {
			this.setSequence(new Sequence());
		}
		this.getSequence().setStartValue(value);
		return instance();
	}

	@Override
	public Long getIdentityMaxValue() {
		if (this.getSequence() != null && this.getSequence().getMaxValue() != null) {
			return this.getSequence().getMaxValue().longValue();
		}
		return null;
	}

	@Override
	public T setIdentityMaxValue(Number value) {
		if (this.getSequence() == null) {
			this.setSequence(new Sequence());
		}
		this.getSequence().setMaxValue(value);
		return instance();
	}

	@Override
	public T setIdentityMaxValue(long value) {
		if (this.getSequence() == null) {
			this.setSequence(new Sequence());
		}
		this.getSequence().setMaxValue(value);
		return instance();
	}

	@Override
	public Long getIdentityMinValue() {
		if (this.getSequence() != null && this.getSequence().getMinValue() != null) {
			return this.getSequence().getMinValue().longValue();
		}
		return null;
	}

	@Override
	public T setIdentityMinValue(Number value) {
		if (this.getSequence() == null) {
			this.setSequence(new Sequence());
		}
		this.getSequence().setMinValue(value);
		return instance();
	}

	@Override
	public T setIdentityMinValue(long value) {
		if (this.getSequence() == null) {
			this.setSequence(new Sequence());
		}
		this.getSequence().setMinValue(value);
		return instance();
	}

	@Override
	public boolean isIdentityCache() {
		if (this.getSequence() != null) {
			return this.getSequence().isCache();
		}
		return DEFAULT_VALUE_SEQUENCE.isCache();
	}

	@Override
	public T setIdentityCache(boolean value) {
		if (this.isIdentity()){
			this.getSequence().setCache(value);
		}
		return instance();
	}

	@Override
	public Integer getIdentityCacheSize() {
		if (this.getSequence() != null && this.getSequence().getCacheSize() != null) {
			return this.getSequence().getCacheSize().intValue();
		}
		return null;
	}

	@Override
	public T setIdentityCacheSize(Number value) {
		if (this.getSequence() == null) {
			this.setSequence(new Sequence());
		}
		this.getSequence().setCacheSize(value);
		return instance();
	}

	@Override
	public T setIdentityCacheSize(int value) {
		if (this.getSequence() == null) {
			this.setSequence(new Sequence());
		}
		this.getSequence().setStartValue(value);
		return instance();
	}
	
	@Override
	public boolean isIdentityCycle() {
		if (this.getSequence()!=null){
			return this.getSequence().isCycle();
		}
		return DEFAULT_VALUE_SEQUENCE.isCycle();
	}

	@Override
	public T setIdentityCycle(boolean value) {
		if (this.isIdentity()){
			this.getSequence().setCycle(value);
		}
		return instance();
	}

	@Override
	public boolean isIdentityOrder() {
		if (this.getSequence()!=null){
			return this.getSequence().isOrder();
		}
		return DEFAULT_VALUE_SEQUENCE.isOrder();
	}

	@Override
	public T setIdentityOrder(boolean value) {
		if (this.isIdentity()){
			this.getSequence().setOrder(value);
		}
		return instance();
	}

	@Override
	public String getSequenceSchemaName(){
		return getSequence()==null?null:getSequence().getSchemaName();
	}

	@Override
	public String getSequenceName(){
		return getSequence()==null?null:getSequence().getName();
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbTypeProperties#getDbTypeName()
	 */
	@Override
	public String getDataTypeName() {
		return dataTypeName;
	}

	@Override
	protected void validate() {
		this.setDataTypeName(this.getDataTypeName());
		if (this.getDataType() != null && this.getDialect() != null) {
			DbDataType<?> dbDataType = this.getDialect().getDbDataType(this);
			if (dbDataType != null && !dbDataType.isFixedScale()) {
				this.setScale(null);
			}
		}
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
	public T setScale(Number scale) {
		this.scale = Converters.getDefault()
				.convertObject(scale, Integer.class);
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.ScaleProperty#setScale(int)
	 */
	@Override
	public T setScale(int scale) {
		this.scale = scale;
		return instance();
	}

	@Override
	public String getDefaultValue() {
		return defaultValue;
	}

	@Override
	public T setDefaultValue(String defaultValue) {
		if (defaultValue != null) {
			this.defaultValue = CommonUtils.trim(defaultValue).intern();
		} else {
			this.defaultValue = null;
		}
		return instance();
	}

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	@Override
	protected void toStringDetail(ToStringBuilder builder) {
		builder.add(SchemaProperties.DATA_TYPE, this.dataType);
		builder.add(SchemaProperties.DATA_TYPE_NAME, this.dataTypeName);
		builder.add(SchemaProperties.NOT_NULL, this.isNotNull());
		if (this.getDataType()==null||this.getDataType().isFixedSize()){
			builder.add(SchemaProperties.LENGTH, this.getLength());
			builder.add(SchemaProperties.OCTET_LENGTH, this.getOctetLength());
		}
		if (this.getDataType()==null||!this.getDataType().isFixedScale()){
			builder.add(SchemaProperties.SCALE, this.getScale());
		}
		if (this.isIdentity()) {
			builder.add(SchemaProperties.IDENTITY, this.isIdentity());
		} else {
			builder.add(SchemaProperties.SEQUENCE_NAME, this.getSequenceName());
		}
		builder.add(SchemaProperties.DEFAULT_VALUE, this.getDefaultValue());
		builder.add(SchemaProperties.IDENTITY_GENERATION_TYPE, this.getIdentityGenerationType());
		builder.add(SchemaProperties.CHARACTER_SEMANTICS, this.getCharacterSemantics());
		builder.add(SchemaProperties.CHARACTER_SET, this.getCharacterSet());
		builder.add(SchemaProperties.COLLATION, this.getCollation());
		if (arrayDimension > 0) {
			builder.add(SchemaProperties.ARRAY_DIMENSION, this.arrayDimension);
			builder.add(SchemaProperties.ARRAY_DIMENSION_LOWER_BOUND, this.arrayDimensionLowerBound);
			builder.add(SchemaProperties.ARRAY_DIMENSION_UPPER_BOUND, this.arrayDimensionUpperBound);
		}
		builder.add(SchemaProperties.VALUES, this.getValues());
		builder.add(SchemaProperties.FORMULA, this.formula);
		if (!isEmpty(formula)) {
			builder.add(SchemaProperties.FORMULA_PERSISTED, this.formulaPersisted);
		}
		builder.add(SchemaProperties.STRING_UNITS, this.getStringUnits());
	}

	/**
	 * XML書き込みでオプション属性を書き込みます
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	protected void writeXmlOptionalAttributes(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalAttributes(stax);
		stax.writeAttribute(SchemaProperties.DATA_TYPE.getLabel(), this.getDataType());
		stax.writeAttribute(SchemaProperties.DATA_TYPE_NAME.getLabel(), this.getDataTypeName());
		if (this.getDataType()==null||this.getDataType().isFixedSize()){
			stax.writeAttribute(SchemaProperties.LENGTH.getLabel(), getLength());
			if (!CommonUtils.eq(this.getLength(), getOctetLength())) {
				stax.writeAttribute(SchemaProperties.OCTET_LENGTH.getLabel(), getOctetLength());
			}
		}
		if (this.getDataType()==null||!this.getDataType().isFixedScale()){
			stax.writeAttribute(SchemaProperties.SCALE.getLabel(), getScale());
		}
		if (this.isNotNull()) {
			stax.writeAttribute(SchemaProperties.NOT_NULL.getLabel(), this.isNotNull());
		}
		if (this.isIdentity()) {
			stax.writeAttribute(SchemaProperties.IDENTITY.getLabel(), this.isIdentity());
			stax.writeAttribute(SchemaProperties.IDENTITY_START_VALUE.getLabel(),
					this.getIdentityStartValue());
			stax.writeAttribute(SchemaProperties.IDENTITY_MAX_VALUE.getLabel(),
					this.getIdentityMaxValue());
			stax.writeAttribute(SchemaProperties.IDENTITY_MIN_VALUE.getLabel(),
					this.getIdentityMinValue());
			stax.writeAttribute(SchemaProperties.IDENTITY_STEP.getLabel(),
					this.getIdentityStep());
			stax.writeAttribute(SchemaProperties.IDENTITY_LAST_VALUE.getLabel(),
					this.getIdentityLastValue());
			stax.writeAttribute(SchemaProperties.IDENTITY_GENERATION_TYPE.getLabel(), this.getIdentityGenerationType());
			if (this.isIdentityCache()!=DEFAULT_VALUE_SEQUENCE.isCache()){
				stax.writeAttribute(SchemaProperties.IDENTITY_CACHE.getLabel(),
						this.isIdentityCache());
			}
			stax.writeAttribute(SchemaProperties.IDENTITY_CACHE_SIZE.getLabel(),
					this.getIdentityCacheSize());
			if (this.isIdentityCycle()!=DEFAULT_VALUE_SEQUENCE.isCycle()){
				stax.writeAttribute(SchemaProperties.IDENTITY_CYCLE.getLabel(),
						this.isIdentityCycle());
			}
			if (this.isIdentityOrder()!=DEFAULT_VALUE_SEQUENCE.isOrder()){
				stax.writeAttribute(SchemaProperties.IDENTITY_ORDER.getLabel(),
						this.isIdentityOrder());
			}
		} else{
			if (this.getSequenceName() != null) {
				if (!CommonUtils.eq(this.getSchemaName(), this.getSequenceSchemaName())){
					stax.writeAttribute(SchemaProperties.SEQUENCE_SCHEMA_NAME.getLabel(),
							this.getSequenceSchemaName());
				}
				stax.writeAttribute(SchemaProperties.SEQUENCE_NAME.getLabel(),
						this.getSequenceName());
			}
		}
		writeCharacterSemantics(stax);
		writeCharacterSet(stax);
		writeCollation(stax);
		writeStringUnits(stax);
		stax.writeAttribute(SchemaProperties.DEFAULT_VALUE.getLabel(), this.getDefaultValue());
		if (this.getArrayDimension() > 0) {
			stax.writeAttribute(SchemaProperties.ARRAY_DIMENSION.getLabel(), this.getArrayDimension());
			if (this.getArrayDimensionLowerBound() > 0) {
				stax.writeAttribute(SchemaProperties.ARRAY_DIMENSION_LOWER_BOUND.getLabel(),
						this.getArrayDimensionLowerBound());
			}
			if (this.getArrayDimensionUpperBound() > 0) {
				stax.writeAttribute(SchemaProperties.ARRAY_DIMENSION_UPPER_BOUND.getLabel(),
						this.getArrayDimensionUpperBound());
			}
		}
		stax.writeAttribute(SchemaProperties.FORMULA.getLabel(), this.getFormula());
		if (!isEmpty(this.getFormula())) {
			if (this.isFormulaPersisted()) {
				stax.writeAttribute(SchemaProperties.FORMULA_PERSISTED.getLabel(),
						this.isFormulaPersisted());
			}
		}
	}

	protected void writeCharacterSet(StaxWriter stax)
			throws XMLStreamException {
		String value=SchemaUtils.getParentCharacterSet(this);
		if (!CommonUtils.eqIgnoreCase(value,
				this.getCharacterSet())) {
			stax.writeAttribute(SchemaProperties.CHARACTER_SET.getLabel(), this.getCharacterSet());
		}
	}

	protected void writeCollation(StaxWriter stax)
			throws XMLStreamException {
		String value=SchemaUtils.getParentCollation(this);
		if (!CommonUtils.eqIgnoreCase(value,
				this.getCollation())) {
			stax.writeAttribute(SchemaProperties.COLLATION.getLabel(), this.getCollation());
		}
	}

	protected void writeCharacterSemantics(StaxWriter stax)
			throws XMLStreamException {
		CharacterSemantics value=SchemaUtils.getParentCharacterSemantics(this);
		if (!CommonUtils.eq(value,
				this.getCharacterSemantics())) {
			stax.writeAttribute(SchemaProperties.CHARACTER_SEMANTICS.getLabel(),
					this.getCharacterSemantics());
		}
	}
	
	protected void writeStringUnits(StaxWriter stax)
			throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.STRING_UNITS.getLabel(), this.getStringUnits());
	}

	/**
	 * XML書き込みでオプションの値を書き込みます
	 * 
	 * @param stax
	 * @throws XMLStreamException
	 */
	@Override
	protected void writeXmlOptionalValues(StaxWriter stax)
			throws XMLStreamException {
		super.writeXmlOptionalValues(stax);
		if (!isEmpty(this.getValues())) {
			stax.newLine();
			stax.indent();
			stax.writeElementValues(SchemaProperties.VALUES.getLabel(), this.getValues());
		}
	}

	@Override
	public String getFormula() {
		return formula;
	}

	@Override
	public T setFormula(String formula) {
		this.formula = formula;
		return instance();
	}

	@Override
	public boolean isFormulaPersisted() {
		return formulaPersisted;
	}

	@Override
	public T setFormulaPersisted(boolean formulaPersisted) {
		this.formulaPersisted = formulaPersisted;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DbTypeProperties#getDbType()
	 */
	@Override
	public DataType getDataType() {
		return dataType;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DbTypeProperties#setDbType(com.sqlapp.data.db
	 * .datatype.Types)
	 */
	@Override
	public T setDataType(DataType dbType) {
		this.dataType = dbType;
		return instance();
	}

	public int getArrayDimensionLowerBound() {
		return arrayDimensionLowerBound;
	}

	public T setArrayDimensionLowerBound(int arrayDimensionLowerBound) {
		this.arrayDimensionLowerBound = arrayDimensionLowerBound;
		return instance();
	}

	public int getArrayDimensionUpperBound() {
		return arrayDimensionUpperBound;
	}

	public T setArrayDimensionUpperBound(int arrayDimensionUpperBound) {
		this.arrayDimensionUpperBound = arrayDimensionUpperBound;
		return instance();
	}

	/**
	 * @return the arrayDimension
	 */
	public int getArrayDimension() {
		return arrayDimension;
	}

	/**
	 * @param arrayDimension
	 *            the arrayDimension to set
	 */
	public T setArrayDimension(int arrayDimension) {
		this.arrayDimension = arrayDimension;
		return instance();
	}

	/**
	 * @return the values
	 */
	public Set<String> getValues() {
		return values;
	}

	/**
	 * @param values
	 *            the values to set
	 */
	public T setValues(Set<String> values) {
		this.values = values;
		return instance();
	}

	@Override
	public T setCharacterSemantics(CharacterSemantics value) {
		if (CommonUtils.eq(this.getCharacterSemantics(), value)){
			this.characterSemantics = null;
		} else{
			this.characterSemantics = value;
		}
		return instance();
	}

}