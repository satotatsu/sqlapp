/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.datatype.util;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.AbstractLengthType;
import com.sqlapp.data.db.datatype.AbstractNoSizeType;
import com.sqlapp.data.db.datatype.AbstractPrecisionType;
import com.sqlapp.data.db.datatype.AbstractScaleType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.LengthProperties;
import com.sqlapp.data.db.datatype.PrecisionProperties;
import com.sqlapp.data.db.datatype.ScaleProperties;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.DbInfo;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.properties.DataTypeNameProperty;
import com.sqlapp.util.CommonUtils;

/**
 * 型情報
 */
public class TypeInformation {

	private DbDataType<?> dbDataType;

	private Optional<DataType> dataType = Optional.empty();

	private Optional<String> dataTypeName = Optional.empty();

	private Optional<Long> length = Optional.empty();

	private boolean setDefaultLength = false;

	private Optional<Long> octetLength = Optional.empty();

	private Optional<Integer> scale = Optional.empty();

	private Optional<Boolean> identity = Optional.empty();

	private Optional<CharacterSemantics> characterSemantics = Optional.empty();

	private OptionalInt arrayDimension = OptionalInt.empty();

	private Optional<List<String>> values = Optional.empty();

	private Optional<DbInfo> specifics = Optional.empty();

	public TypeInformation() {
	}

	public void setDbDataType(DbDataType<?> dbDataType) {
		this.dbDataType = dbDataType;
		if (this.getDataTypeName().isEmpty()) {
			this.setDataTypeName(dbDataType.getTypeName());
		}
		if (!this.dbDataType.isFixedLength() && !this.dbDataType.isFixedPrecision()) {
			this.setLength(null);
		}
		this.setDataType(dbDataType.getDataType());
	}

	public DbDataType<?> getDbDataType() {
		return this.dbDataType;
	}

	/**
	 * Length Overかを判定します
	 * 
	 * @return Lengthチェック結果
	 */
	public boolean isLengthOver() {
		if (this.getLength().isEmpty()) {
			return false;
		}
		Long val = this.getLength().get();
		if (val == null) {
			return false;
		}
		return isLengthOver(val);
	}

	/**
	 * Length Overかを判定します
	 * 
	 * @return Lengthチェック結果
	 */
	public boolean isLengthOver(Long val) {
		if (this.dbDataType == null) {
			return false;
		}
		if (val == null) {
			return false;
		}
		if (dbDataType instanceof LengthProperties) {
			final LengthProperties<?> sp = (LengthProperties<?>) dbDataType;
			if (sp.getMaxLength() == null) {
				return false;
			}
			if (sp.getMaxLength() != null && sp.getMaxLength().longValue() < val) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the setDefaultLength
	 */
	public boolean isSetDefaultLength() {
		return setDefaultLength;
	}

	/**
	 * @param setDefaultLength the setDefaultLength to set
	 */
	public void setSetDefaultLength(boolean setDefaultLength) {
		this.setDefaultLength = setDefaultLength;
	}

	/**
	 * dataTypeを取得します
	 * 
	 * @return dataType
	 */
	public Optional<DataType> getDataType() {
		return dataType;
	}

	/**
	 * dataTypeを設定します
	 * 
	 * @param value dataType
	 */
	public void setDataType(DataType value) {
		dataType = Optional.of(value);
	}

	/**
	 * CharacterSemanticsを取得します
	 * 
	 * @return CharacterSemantics
	 */
	public Optional<CharacterSemantics> getCharacterSemantics() {
		return characterSemantics;
	}

	/**
	 * CharacterSemanticsを設定します
	 * 
	 * @param value CharacterSemantics
	 */
	public void setCharacterSemantics(CharacterSemantics value) {
		characterSemantics = Optional.ofNullable(value);
	}

	/**
	 * identityを設定します
	 * 
	 * @param value identity
	 */
	public void setIdentity(boolean value) {
		identity = Optional.ofNullable(value);
	}

	/**
	 * identityを取得します
	 * 
	 * @return identity
	 */
	public Optional<Boolean> getIdentity() {
		return identity;
	}

	/**
	 * CharacterSemanticsを設定します
	 * 
	 * @param value CharacterSemantics
	 */
	public void setCharacterSemantics(String value) {
		characterSemantics = Optional.ofNullable(CharacterSemantics.parse(value));
	}

	/**
	 * dataTypeNameを取得します
	 * 
	 * @return dataTypeName
	 */
	public Optional<String> getDataTypeName() {
		return dataTypeName;
	}

	/**
	 * dataTypeNameを設定します
	 * 
	 * @param value dataTypeName
	 */
	public void setDataTypeName(String value) {
		dataTypeName = Optional.ofNullable(value);
	}

	private boolean matchDataTypeName(final DataType dataType, final String dataTypeName) {
		if (dataType == null) {
			return false;
		}
		if (dataTypeName == null) {
			return false;
		}
		if (dataType.matchName(dataTypeName)) {
			return true;
		}
		if ((dataType == DataType.VARCHAR) && CommonUtils.eqIgnoreCase("VARCHAR2", dataTypeName)) {
			return true;// Oracleむかつく
		}
		if ((dataType == DataType.NVARCHAR) && CommonUtils.eqIgnoreCase("NVARCHAR2", dataTypeName)) {
			return true;// Oracleむかつく
		}
		return false;
	}

	/**
	 * Lengthを取得します
	 * 
	 * @return Optional<Long>
	 */
	public Optional<Long> getLength() {
		return length;
	}

	/**
	 * lengthを設定します
	 * 
	 * @param value length
	 */
	public void setLength(Object value) {
		Long val = Converters.getDefault().convertObject(value, Long.class);
		length = Optional.ofNullable(val);
	}

	/**
	 * lengthを設定します
	 * 
	 * @param value length
	 * @param multi K OR M OR G
	 */
	public void setLength(String value, String multi) {
		Long val = Converters.getDefault().convertObject(value, Long.class);
		if (CommonUtils.isEmpty(multi)) {
			length = Optional.ofNullable(getCaluculatedLength(val));
		} else {
			if ("K".equalsIgnoreCase(multi)) {
				length = Optional.of(getCaluculatedLength(val.longValue() * CommonUtils.LEN_1KB));
			} else if ("M".equalsIgnoreCase(multi)) {
				length = Optional.of(getCaluculatedLength(val.longValue() * CommonUtils.LEN_1MB));
			} else if ("G".equalsIgnoreCase(multi)) {
				length = Optional.of(getCaluculatedLength(val.longValue() * CommonUtils.LEN_1GB));
			}
		}
	}

	private Long getCaluculatedLength(Long val) {
		if (dbDataType == null) {
			return val;
		}
		if (dbDataType instanceof AbstractNoSizeType) {
			return null;
		}
		Optional<Integer> defPre = getDefaultPrecision();
		if (defPre.isPresent()) {
			if (val == null) {
				return Converters.getDefault().convertObject(defPre.get(), Long.class);
			} else {
				return Converters.getDefault().convertObject(defPre.get(), Long.class);
			}
		}
		Optional<Long> defLen = getDefaultLength();
		if (defLen.isPresent()) {
			if (val == null) {
				return defLen.get();
			} else {
				if (defLen.get().longValue() < val) {
					return defLen.get();
				}
			}
		}
		return val;
	}

	public Optional<Integer> getDefaultPrecision() {
		if (!(dbDataType instanceof PrecisionProperties)) {
			return Optional.empty();
		}
		final PrecisionProperties<?> sp = (PrecisionProperties<?>) dbDataType;
		if (sp.getDefaultPrecision() != null) {
			return Optional.of(sp.getDefaultPrecision());
		}
		return Optional.empty();
	}

	public Optional<Long> getDefaultLength() {
		if (!(dbDataType instanceof LengthProperties)) {
			return Optional.empty();
		}
		final LengthProperties<?> sp = (LengthProperties<?>) dbDataType;
		if (sp.getDefaultLength() != null) {
			return Optional.of(sp.getDefaultLength());
		}
		return Optional.empty();
	}

	public Optional<Integer> getDefaultScale() {
		if (!(dbDataType instanceof ScaleProperties)) {
			return Optional.empty();
		}
		final ScaleProperties<?> sp = (ScaleProperties<?>) dbDataType;
		if (sp.getDefaultScale() != null) {
			return Optional.of(sp.getDefaultScale());
		}
		return Optional.empty();
	}

	/**
	 * octetLengthを取得します
	 * 
	 * @return Optional<Long>
	 */
	public Optional<Long> getOctetLength() {
		return octetLength;
	}

	/**
	 * octetLengthを設定します
	 * 
	 * @param value octetLength
	 */
	public void setOctetLength(Object value) {
		Long val = Converters.getDefault().convertObject(value, Long.class);
		octetLength = Optional.ofNullable(val);
	}

	/**
	 * scaleを取得します
	 * 
	 * @return scale
	 */
	public Optional<Integer> getScale() {
		return scale;
	}

	/**
	 * scaleを設定します
	 * 
	 * @param value scale
	 */
	public void setScale(Object value) {
		Integer val = Converters.getDefault().convertObject(value, Integer.class);
		scale = Optional.ofNullable(getCaluculatedScale(val));
	}

	private Integer getCaluculatedScale(Integer val) {
		if (dbDataType == null) {
			return val;
		}
		if (dbDataType instanceof AbstractNoSizeType) {
			return null;
		}
		if (!(dbDataType instanceof ScaleProperties)) {
			return val;
		}
		final ScaleProperties<?> sp = (ScaleProperties<?>) dbDataType;
		if (val == null) {
			return sp.getDefaultScale();
		}
		if (sp.getMaxScale() != null && sp.getMaxScale().intValue() < val) {
			return sp.getMaxScale();
		}
		return val;
	}

	/**
	 * valuesを設定します
	 * 
	 * @param value values
	 */
	public void setValues(List<String> value) {
		values = Optional.of(value);
	}

	/**
	 * Lengthを取得します
	 * 
	 * @return values
	 */
	public Optional<List<String>> getValues() {
		return values;
	}

	/**
	 * arrayDimentionを取得します
	 * 
	 * @return arrayDimention
	 */
	public OptionalInt getArrayDimension() {
		return arrayDimension;
	}

	/**
	 * arrayDimentionを設定します
	 * 
	 * @param value arrayDimention
	 */
	public void setArrayDimension(int value) {
		arrayDimension = OptionalInt.of(value);
	}

	public Optional<DbInfo> getSpecifics() {
		return specifics;
	}

	public void setSpecifics(String key, String value) {
		DbInfo info;
		if (this.getSpecifics().isEmpty()) {
			info = new DbInfo();
			this.specifics = Optional.of(info);
		} else {
			info = this.getSpecifics().get();
		}
		info.put(key, value);
	}

	public void set(DataTypeNameProperty<?> column) {
		if (this.getDataType().isPresent()) {
			SchemaProperties.DATA_TYPE.setValue(column, this.getDataType().get());
			if (this.getDataTypeName().isPresent()) {
				if (matchDataTypeName(this.getDataType().get(), this.getDataTypeName().get())) {
					SchemaProperties.DATA_TYPE_NAME.setValue(column, null);
				} else {
					SchemaProperties.DATA_TYPE_NAME.setValue(column, this.getDataTypeName().get());
				}
			}
		} else {
			if (this.getDataTypeName().isPresent()) {
				SchemaProperties.DATA_TYPE_NAME.setValue(column, this.getDataTypeName().get());
			}
		}
		if (dbDataType instanceof AbstractNoSizeType) {
			SchemaProperties.OCTET_LENGTH.setValue(column, null);
			SchemaProperties.LENGTH.setValue(column, null);
			SchemaProperties.SCALE.setValue(column, null);
		} else if (dbDataType instanceof AbstractLengthType) {
			setLength(column);
			SchemaProperties.SCALE.setValue(column, null);
		} else if (dbDataType instanceof AbstractPrecisionType) {
			setLength(column);
			SchemaProperties.SCALE.setValue(column, null);
		} else if (dbDataType instanceof AbstractScaleType) {
			setLength(column);
			SchemaProperties.OCTET_LENGTH.setValue(column, null);
			SchemaProperties.LENGTH.setValue(column, null);
		} else {
			setLength(column);
		}
		if (this.getIdentity().isPresent()) {
			SchemaProperties.IDENTITY.setValue(column, this.getIdentity().get());
		}
		if (this.getCharacterSemantics().isPresent()) {
			SchemaProperties.CHARACTER_SEMANTICS.setValue(column, this.getCharacterSemantics().get());
		}
		if (this.getArrayDimension().isPresent()) {
			SchemaProperties.ARRAY_DIMENSION.setValue(column, this.getArrayDimension().getAsInt());
		}
		if (this.getValues().isPresent()) {
			SchemaProperties.VALUES.setValue(column, this.getValues().get());
		}
		if (this.getSpecifics().isPresent()) {
			DbInfo dbInfo = (DbInfo) SchemaProperties.SPECIFICS.getValue(column);
			dbInfo.putAll(this.getSpecifics().get());
		}
	}

	private void setLength(DataTypeNameProperty<?> column) {
		if (this.getOctetLength().isPresent()) {
			SchemaProperties.OCTET_LENGTH.setValue(column, this.getOctetLength().get());
		}
		if (this.getLength().isPresent()) {
			SchemaProperties.LENGTH.setValue(column, this.getLength().get());
		} else {
			Optional<Integer> defPre = getDefaultPrecision();
			if (defPre.isPresent()) {
				SchemaProperties.LENGTH.setValue(column, defPre.get());
			}
			Optional<Long> defLen = getDefaultLength();
			if (defLen.isPresent()) {
				SchemaProperties.LENGTH.setValue(column, defLen.get());
			}
		}
		if (this.getScale().isPresent()) {
			SchemaProperties.SCALE.setValue(column, this.getScale().get());
		} else {
			Optional<Integer> defPre = getDefaultScale();
			if (defPre.isPresent()) {
				SchemaProperties.SCALE.setValue(column, defPre.get());
			}
		}
	}
}
