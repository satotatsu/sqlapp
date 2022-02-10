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

import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.isEmpty;
import java.util.List;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.properties.DataTypeSetProperties;
import com.sqlapp.data.schemas.properties.DefinitionProperty;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.properties.object.FunctionReturningReferenceTableProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.EqualsUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.ToStringBuilder;

/**
 * Function戻り値
 * 
 * @author satoh
 * 
 */
public class FunctionReturning extends AbstractDbObject<FunctionReturning> implements
		HasParent<Function>, DataTypeSetProperties<FunctionReturning>,
		NameProperty<FunctionReturning>
	, DefinitionProperty<FunctionReturning>
	, FunctionReturningReferenceTableProperty<FunctionReturning>
	{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 5048992706883015188L;
	/** 変数名 */
	private String name = null;
	/** 最大長 */
	private Long maxLength = null;
	/** 項目のOctet長 */
	private Long octetLength = null;
	/** カラムの文字列のセマンティックス */
	private CharacterSemantics characterSemantics = null;
	/** java.sql.Types(VARCHAR,CHAR…) */
	private DataType dataType = null;
	/** DB固有の型 */
	@SuppressWarnings("unused")
	private String dataTypeName = null;
	/** 小数点以下の桁数 */
	private Integer scale = null;
	/** 配列型の場合の次元数(通常:0) */
	private int arrayDimension = 0;
	/** 配列(1次元)の下限 */
	private int arrayLowerBound = 0;
	/** 配列(1次元)の上限 */
	private int arrayUpperBound = 0;
	/** characterSet */
	private String characterSet = null;
	/** collation */
	private String collation = null;
	/** 定義(DDLなど) */
	private List<String> definition = null;
	/** テーブル定義 */
	private FunctionReturningReferenceTable table=null;
	
	public FunctionReturning(Function function) {
		super.setParent(function);
	}

	protected FunctionReturning() {
	}

	@Override
	protected Supplier<FunctionReturning> newInstance(){
		return ()->new FunctionReturning();
	}
	
	public Function getParent() {
		return (Function) super.getParent();
	}

	protected static final String SIMPLE_NAME = "returning";

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.NameProperty#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.NameProperty#setName(java.lang.String)
	 */
	@Override
	public FunctionReturning setName(String name) {
		this.name = name;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.AbstractDbObject#getSimpleName()
	 */
	@Override
	protected String getSimpleName() {
		return SIMPLE_NAME;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.CharacterSetProperty#getCharacterSet()
	 */
	@Override
	public String getCharacterSet() {
		return characterSet;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.CharacterSetProperty#setCharacterSet(java.lang
	 * .String)
	 */
	@Override
	public FunctionReturning setCharacterSet(String characterSetName) {
		this.characterSet = characterSetName;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.CollationName#getCollationName()
	 */
	@Override
	public String getCollation() {
		return collation;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.CollationName#setCollationName(java.lang.String)
	 */
	@Override
	public FunctionReturning setCollation(String collationName) {
		this.collation = collationName;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.dataset.AbstractNamedObject#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj, EqualsHandler equalsHandler) {
		if (!(obj instanceof FunctionReturning)) {
			return false;
		}
		if (!super.equals(obj, equalsHandler)) {
			return false;
		}
		FunctionReturning val = (FunctionReturning) obj;
		if (!equals(SchemaProperties.NAME, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_TYPE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DATA_TYPE_NAME, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getDataTypeName(), val.getDataTypeName()))) {
			return false;
		}
		if (!equals(SchemaProperties.LENGTH, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.OCTET_LENGTH, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.CHARACTER_SEMANTICS, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.SCALE, val, equalsHandler)) {
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
		if (!equals(SchemaProperties.CHARACTER_SET, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getCharacterSet(), val.getCharacterSet()))) {
			return false;
		}
		if (!equals(SchemaProperties.COLLATION, val, equalsHandler
				, EqualsUtils.getEqualsIgnoreCaseSupplier(this.getCollation(), val.getCollation()))) {
			return false;
		}
		if (!equals(SchemaObjectProperties.FUNCTION_RETURNING_REFERENCE_TABLE, val, equalsHandler)) {
			return false;
		}
		if (!equals(SchemaProperties.DEFINITION, val, equalsHandler)) {
			return false;
		}
		return true;
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
	public FunctionReturning setLength(long maxLength) {
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
	public FunctionReturning setLength(Number maxLength) {
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
		if (octetLength != null && this.getLength() != null
				&& this.getLength().longValue() > 0) {
			return this.getLength();
		}
		return octetLength;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.LengthProperties#setOctetLength(long)
	 */
	@Override
	public FunctionReturning setOctetLength(long octetLength) {
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
	public FunctionReturning setOctetLength(Number octetLength) {
		this.octetLength = Converters.getDefault().convertObject(octetLength,
				Long.class);
		return instance();
	}

	
	/**
	 * @return the dialect
	 */
	public Dialect getDialect() {
		if (this.getParent() == null) {
			return null;
		}
		return this.getParent().getDialect();
	}

	@Override
	protected void validate() {
		this.setDataTypeName(this.getDataTypeName());
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
	public FunctionReturning setScale(Number scale) {
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
	public FunctionReturning setScale(int scale) {
		this.scale = scale;
		return instance();
	}

	/**
	 * toString()で子クラスで追加したプロパティの設定
	 * 
	 * @param builder
	 */
	@Override
	protected void toString(ToStringBuilder builder) {
		builder.add(SchemaProperties.NAME.getLabel(), this.getName());
		builder.add(SchemaProperties.DATA_TYPE, this.getDataType());
		builder.add(SchemaProperties.DATA_TYPE_NAME, this.getDataTypeName());
		builder.add(SchemaProperties.LENGTH, this.maxLength);
		builder.add(SchemaProperties.SCALE, this.getScale());
		builder.add(SchemaProperties.OCTET_LENGTH, this.getOctetLength());
		builder.add(SchemaProperties.CHARACTER_SEMANTICS.getLabel(), this.getCharacterSemantics());
		builder.add(SchemaProperties.CHARACTER_SET.getLabel(), this.getCharacterSet());
		builder.add(SchemaProperties.COLLATION.getLabel(), this.getCollation());
		if (arrayDimension > 0) {
			builder.add(SchemaProperties.ARRAY_DIMENSION, this.arrayDimension);
			builder.add(SchemaProperties.ARRAY_DIMENSION_LOWER_BOUND, this.arrayLowerBound);
			builder.add(SchemaProperties.ARRAY_DIMENSION_UPPER_BOUND, this.arrayUpperBound);
		}
		builder.add(SchemaProperties.DEFINITION, this.getDefinition());
		super.toString(builder);
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
		stax.writeAttribute(SchemaProperties.NAME.getLabel(), this.getName());
		stax.writeAttribute(SchemaProperties.DATA_TYPE.getLabel(), this.getDataType());
		stax.writeAttribute(SchemaProperties.DATA_TYPE_NAME.getLabel(), this.getDataTypeName());
		stax.writeAttribute(SchemaProperties.LENGTH.getLabel(), getLength());
		if (!eq(this.getLength(), getOctetLength())) {
			stax.writeAttribute(SchemaProperties.OCTET_LENGTH.getLabel(), getOctetLength());
		}
		stax.writeAttribute(SchemaProperties.SCALE.getLabel(), getScale());
		writeCharacterSemantics(stax);
		writeCharacterSetName(stax);
		writeCollationName(stax);
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
	}

	protected void writeCharacterSemantics(StaxWriter stax)
			throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.CHARACTER_SEMANTICS.getLabel(), this.getCharacterSemantics());
	}

	protected void writeCharacterSetName(StaxWriter stax)
			throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.CHARACTER_SET.getLabel(), this.getCharacterSet());
	}

	protected void writeCollationName(StaxWriter stax)
			throws XMLStreamException {
		stax.writeAttribute(SchemaProperties.COLLATION.getLabel(), this.getCollation());
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
		if (this.getTable()!=null){
			stax.addIndentLevel(1);
			this.getTable().writeXml(stax);
			stax.addIndentLevel(-1);
		}
		if (!isEmpty(this.getDefinition())) {
			stax.newLine();
			stax.indent();
			stax.writeCData(SchemaProperties.DEFINITION.getLabel(), listToString(this.getDefinition()));
		}
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
	public FunctionReturning setDataType(DataType dbType) {
		this.dataType = dbType;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.ArrayDimensionProperties#getArrayLowerBound()
	 */
	@Override
	public int getArrayDimensionLowerBound() {
		return arrayLowerBound;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.ArrayDimensionProperties#setArrayLowerBound(int)
	 */
	@Override
	public FunctionReturning setArrayDimensionLowerBound(int arrayLowerBound) {
		this.arrayLowerBound = arrayLowerBound;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.ArrayDimensionProperties#getArrayUpperBound()
	 */
	@Override
	public int getArrayDimensionUpperBound() {
		return arrayUpperBound;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.ArrayDimensionProperties#setArrayUpperBound(int)
	 */
	@Override
	public FunctionReturning setArrayDimensionUpperBound(int arrayUpperBound) {
		this.arrayUpperBound = arrayUpperBound;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.ArrayDimensionProperties#getArrayDimension()
	 */
	@Override
	public int getArrayDimension() {
		return arrayDimension;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.ArrayDimensionProperties#setArrayDimension(int)
	 */
	@Override
	public FunctionReturning setArrayDimension(int arrayDimension) {
		this.arrayDimension = arrayDimension;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.CharacterSemanticsProperty#getCharacterSemantics
	 * ()
	 */
	@Override
	public CharacterSemantics getCharacterSemantics() {
		return characterSemantics;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.CharacterSemanticsProperty#setCharacterSemantics
	 * (com.sqlapp.data.schemas.CharacterSemantics)
	 */
	@Override
	public FunctionReturning setCharacterSemantics(
			CharacterSemantics characterSemantics) {
		this.characterSemantics = characterSemantics;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.DefinitionProperty#getDefinition()
	 */
	@Override
	public List<String> getDefinition() {
		if (definition == null) {
			this.definition = CommonUtils.list();
		}
		return this.definition;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DefinitionProperty#setDefinition(java.util.List)
	 */
	@Override
	public FunctionReturning setDefinition(List<String> definition) {
		this.definition = definition;
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.DefinitionProperty#setDefinition(java.lang.String
	 * )
	 */
	@Override
	public FunctionReturning setDefinition(String definition) {
		this.definition = CommonUtils.splitLine(definition);
		return instance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(FunctionReturning o) {
		return this.getParent().compareTo(o.getParent());
	}

	@Override
	public FunctionReturningReferenceTable getTable() {
		if (this.table!=null){
			this.table.setDialect(this.getDialect());
		}
		return this.table;
	}

	@Override
	public FunctionReturning setTable(FunctionReturningReferenceTable value) {
		if (value!=null){
			this.table=value.clone();
			this.table.getTable().getRows().clear();
			this.table.setParent(this);
		} else{
			this.table=value;
		}
		return instance();
	}

	public FunctionReturning toTable(){
		if (this.getTable()==null){
			this.table=new FunctionReturningReferenceTable(this);
		}
		this.setDataTypeName(null);
		this.setDataType((DataType)null);
		return instance();
	}
	
}