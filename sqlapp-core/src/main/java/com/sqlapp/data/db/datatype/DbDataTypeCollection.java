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

package com.sqlapp.data.db.datatype;

import static com.sqlapp.util.CommonUtils.enumMap;
import static com.sqlapp.util.CommonUtils.set;
import static com.sqlapp.util.CommonUtils.toInteger;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;

import com.sqlapp.data.schemas.properties.ArrayDimensionProperties;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;

/**
 * DBのデータ型のコレクション
 */
public class DbDataTypeCollection implements Serializable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3422158399668303563L;

	/**
	 * 推奨型のマップ
	 */
	private final Map<DataType, DataType> recommendMapping = enumMap(DataType.class);

	private final Map<DataType, DbDataType<?>> dataTypeMap = enumMap(DataType.class);
	private final Map<DataType, TreeMap<Long, DbDataType<?>>> dataLengthType = enumMap(DataType.class);
	/**
	 * Array Pattern Generator
	 */
	private transient Function<String, String> arrayPatternGenerator = new ArrayPatternGenerator();
	/**
	 * Array Dimension Handler
	 */
	private transient BiConsumer<Matcher, ArrayDimensionProperties<?>> arrayDimensionHandler = new ArrayDimensionHandler();
	/**
	 * 代替型マップ
	 */
	private final Map<DataType, DataType> surrogateMap = DataType.getSurrogateMap();

	static class ArrayPatternGenerator implements Function<String, String> {
		@Override
		public String apply(String t) {
			return "(?<dataTypeName>" + CommonUtils.trim(t) + ")"
					+ "\\s*ARRAY\\\\s*\\\\[\\\\s*([0-9]+){0,1}\\\\s*\\\\]";
		}
	}

	static class ArrayDimensionHandler implements BiConsumer<Matcher, ArrayDimensionProperties<?>> {
		@Override
		public void accept(Matcher matcher, ArrayDimensionProperties<?> column) {
			String val = matcher.group(matcher.groupCount());
			Integer intVal = toInteger(val);
			if (intVal != null) {
				column.setArrayDimension(1);
				column.setArrayDimensionUpperBound(intVal);
			}
		}
	}

	/**
	 * @param arrayPatternGenerator the arrayPatternGenerator to set
	 */
	public void setArrayPatternGenerator(Function<String, String> arrayPatternGenerator) {
		this.arrayPatternGenerator = arrayPatternGenerator;
	}

	/**
	 * @param arrayDimensionHandler the arrayDimensionHandler to set
	 */
	public void setArrayDimensionHandler(BiConsumer<Matcher, ArrayDimensionProperties<?>> arrayDimensionHandler) {
		this.arrayDimensionHandler = arrayDimensionHandler;
	}

	/**
	 * @return the arrayPatternGenerator
	 */
	protected Function<String, String> getArrayPatternGenerator() {
		return arrayPatternGenerator;
	}

	/**
	 * @return the arrayDimensionHandler
	 */
	protected BiConsumer<Matcher, ArrayDimensionProperties<?>> getArrayDimensionHandler() {
		return arrayDimensionHandler;
	}

	/**
	 * 推奨型のマッピング登録
	 * 
	 * @param baseType      元になる型名
	 * @param recommendType 推奨されるJDBCの型
	 */
	public void registerRecommend(DataType baseType, DataType recommendType) {
		if (recommendMapping.containsKey(baseType)) {
			recommendMapping.remove(baseType);
		}
		recommendMapping.put(baseType, recommendType);
	}

	/**
	 * 指定した型の取得
	 * 
	 * @param type 型
	 */
	public DbDataType<?> getDbTypeStrict(DataType type) {
		if (dataTypeMap.containsKey(type)) {
			DbDataType<?> dbType = dataTypeMap.get(type);
			return dbType;
		}
		return null;
	}

	/**
	 * 指定した最適型の取得
	 * 
	 * @param type 型
	 */
	public DbDataType<?> getDbType(DataType type) {
		Set<DataType> typeSet = set();
		return getDbType(type, typeSet);
	}

	/**
	 * 指定した最適DB型を取得します
	 * 
	 * @param type    型
	 * @param typeSet 再帰操作中の型履歴
	 */
	private DbDataType<?> getDbType(DataType type, Set<DataType> typeSet) {
		if (type == null) {
			return null;
		}
		if (typeSet.contains(type)) {
			return null;
		}
		DbDataType<?> dbType = dataTypeMap.get(type);
		if (dbType != null) {
			if (dbType.isDeprecated()) {
				return dbType.getDeprecatedSurrogateType();
			} else {
				return dbType;
			}
		}
		typeSet.add(type);
		DataType surrogate = type.getUpperSurrogate();
		DbDataType<?> result = getDbType(surrogate, typeSet);
		if (result != null) {
			return result;
		}
		surrogate = getSurrogateMap().get(type);
		result = getDbType(surrogate, typeSet);
		if (result != null) {
			return result;
		}
		return getDbType(surrogate, typeSet);
	}

	/**
	 * 指定した型、サイズのDB型の取得
	 * 
	 * @param type       JDBCの型
	 * @param columnSize カラムのサイズ
	 */
	public DbDataType<?> getDbTypeStrict(DataType type, Number columnSize) {
		if (columnSize == null) {
			return getDbTypeStrict(type);
		}
		return getDbTypeStrict(type, Long.valueOf(columnSize.longValue()));
	}

	/**
	 * 指定した型、サイズのDBタイプの取得
	 * 
	 * @param dataType   JDBCの型
	 * @param columnSize カラムのサイズ
	 */
	public DbDataType<?> getDbTypeStrict(DataType dataType, Long columnSize) {
		if (columnSize == null) {
			return getDbTypeStrict(dataType);
		}
		DbDataType<?> result = getDbTypeLengthInternal(dataType, columnSize);
		return result;
	}

	/**
	 * 指定した型、サイズの最適DB型の取得
	 * 
	 * @param dataType   JDBCの型
	 * @param columnSize カラムのサイズ
	 */
	public DbDataType<?> getDbType(DataType dataType, Number columnSize) {
		if (columnSize == null) {
			return getDbType(dataType);
		}
		return getDbType(dataType, columnSize.longValue());
	}

	/**
	 * 指定した型、サイズのDBタイプの取得
	 * 
	 * @param dataType   JDBCの型
	 * @param columnSize カラムのサイズ
	 */
	public DbDataType<?> getDbType(DataType dataType, Long columnSize) {
		Set<DataType> typeSet = set();
		DbDataType<?> dbDataType = null;
		if (columnSize == null || (columnSize.longValue() <= 0)) {
			dbDataType = this.getDbType(dataType);
			if (dbDataType != null) {
				if (dbDataType instanceof LengthProperties) {
					LengthProperties<?> lengthProperties = (LengthProperties<?>) dbDataType;
					if (columnSize != null && lengthProperties.getMaxLength() != null
							&& columnSize.compareTo(lengthProperties.getMaxLength()) > 0) {
						//
					} else {
						return dbDataType;
					}
				} else {
					return dbDataType;
				}
			}
		}
		dbDataType = getDbType(dataType, columnSize, typeSet);
		if (dbDataType == null) {
			dbDataType = this.getDbType(dataType);
		}
		return dbDataType;
	}

	/**
	 * 指定した型、サイズのDBタイプの取得
	 * 
	 * @param jdbcType   JDBCの型
	 * @param columnSize カラムのサイズ
	 */
	private DbDataType<?> getDbType(DataType type, Long columnSize, Set<DataType> typeSet) {
		if (type == null) {
			return null;
		}
		if (typeSet.contains(type)) {
			return null;
		}
		DataType surrogate = getSurrogateMap().get(type);
		if (columnSize != null && columnSize.longValue() >= 0) {
			DbDataType<?> dbDataType = getDbTypeLengthInternal(type, columnSize, typeSet);
			if (dbDataType != null) {
				return dbDataType;
			} else {
				// 指定サイズのDB型がない場合は、上位型、NATIONAL CHAR代替型、代替型から探して、それでもなければ最大桁数の型で妥協する
				dbDataType = getDbTypeLengthInternal(type.getUpperSurrogate(), columnSize);
				if (dbDataType != null) {
					return dbDataType;
				}
				dbDataType = getDbTypeLengthInternal(type.getNationalSurrogate(), columnSize, typeSet);
				if (dbDataType != null) {
					return dbDataType;
				}
				if (type.getUpperSurrogate() != null) {
					dbDataType = getDbType(type.getUpperSurrogate(), columnSize, typeSet);
					if (dbDataType != null) {
						return dbDataType;
					}
				}
				dbDataType = getDbType(surrogate, columnSize, typeSet);
				if (dbDataType != null) {
					return dbDataType;
				}
				return getMaxDbDataType(type, type.getUpperSurrogate(), surrogate);
			}
		} else {
			typeSet.add(type);
		}
		return getDbType(surrogate, typeSet);
	}

	private DbDataType<?> getDbTypeLengthInternal(DataType type, Long columnSize, Set<DataType> typeSet) {
		if (type == null) {
			return null;
		}
		if (typeSet.contains(type)) {
			return null;
		}
		typeSet.add(type);
		return getDbTypeLengthInternal(type, columnSize);
	}

	private DbDataType<?> getMaxDbDataType(DataType... types) {
		for (DataType type : types) {
			if (type == null) {
				continue;
			}
			TreeMap<Long, DbDataType<?>> dic = dataLengthType.get(type);
			if (dic == null) {
				continue;
			}
			Map.Entry<Long, DbDataType<?>> entry = dic.lastEntry();
			if (entry != null) {
				return entry.getValue();
			}
		}
		return null;
	}

	private DbDataType<?> getDbTypeLengthInternal(DataType type, Long columnSize) {
		if (dataLengthType.containsKey(type)) {
			TreeMap<Long, DbDataType<?>> dic = dataLengthType.get(type);
			DbDataType<?> last = null;
			if (dic.containsKey(columnSize)) {
				last = dic.get(columnSize);
				if (last.isDeprecated()) {
					return last.getDeprecatedSurrogateType();
				} else {
					return last;
				}
			}
			Map.Entry<Long, DbDataType<?>> entry = dic.ceilingEntry(columnSize);
			if (entry != null && entry.getKey() >= columnSize) {
				last = entry.getValue();
				if (last.isDeprecated()) {
					return last.getDeprecatedSurrogateType();
				} else {
					return last;
				}
			}
		}
		return null;
	}

	private Map<String, DbDataType<?>> productDataTypeDbTypeCache = CommonUtils.map();

	private Set<String> noMatchProductDataTypeDbTypeCache = CommonUtils.set();

	private DoubleKeyMap<String, Long, DbDataType<?>> productDataLengthTypeDbTypeCache = CommonUtils.doubleKeyMap();

	/**
	 * 製品固有のデータ型にマッチするデータ型の取得
	 * 
	 * @param productDataType 製品固有のデータ型
	 * @param length
	 * @param column
	 */
	public DbDataType<?> match(String productDataType, Long length, final DataTypeLengthProperties<?> column) {
		if (length == null) {
			if (noMatchProductDataTypeDbTypeCache.contains(productDataType)) {
				return null;
			}
			DbDataType<?> result = productDataTypeDbTypeCache.get(productDataType);
			if (result != null) {
				return result;
			}
			for (Map.Entry<DataType, TreeMap<Long, DbDataType<?>>> entry : dataLengthType.entrySet()) {
				TreeMap<Long, DbDataType<?>> childMap = entry.getValue();
				for (Map.Entry<Long, DbDataType<?>> childEntry : childMap.entrySet()) {
					DbDataType<?> dbDataType = childEntry.getValue();
					if (dbDataType.parseAndSet(productDataType, column)) {
						productDataTypeDbTypeCache.put(productDataType, dbDataType);
						return dbDataType;
					}
				}
			}
		} else {
			DbDataType<?> result = productDataLengthTypeDbTypeCache.get(productDataType, length);
			if (result != null) {
				return result;
			}
			for (Map.Entry<DataType, TreeMap<Long, DbDataType<?>>> entry : dataLengthType.entrySet()) {
				TreeMap<Long, DbDataType<?>> childMap = entry.getValue();
				for (Map.Entry<Long, DbDataType<?>> childEntry : childMap.entrySet()) {
					if (length != null && childEntry.getKey().compareTo(length) >= 0) {
						DbDataType<?> dbDataType = childEntry.getValue();
						if (dbDataType.parseAndSet(productDataType, column)) {
							productDataLengthTypeDbTypeCache.put(productDataType, length, dbDataType);
							productDataTypeDbTypeCache.put(productDataType, dbDataType);
							return dbDataType;
						}
						break;
					}
				}
			}
		}
		for (Map.Entry<DataType, DbDataType<?>> entry : dataTypeMap.entrySet()) {
			DbDataType<?> dbDataType = entry.getValue();
			if (dbDataType.parseAndSet(productDataType, column)) {
				productDataTypeDbTypeCache.put(productDataType, dbDataType);
				return dbDataType;
			}
		}
		noMatchProductDataTypeDbTypeCache.add(productDataType);
		return null;
	}

	/**
	 * CREATE TABLE時のカラムの定義情報
	 * 
	 * @param dbDataType
	 */
	public String getColumCreateDefinition(DbDataType<?> dbDataType) {
		return dbDataType.getCreateFormat();
	}

	/**
	 * データ型を最大長と共に登録します
	 * 
	 * @param dbDataType データ型
	 * @param maxlength  最大長
	 */
	protected void registerDataLength(DbDataType<?> dbDataType, Long maxlength) {
		TreeMap<Long, DbDataType<?>> sortDic = null;
		// nationalCharDataLengthType
		if (dataLengthType.containsKey(dbDataType.getDataType())) {
			sortDic = dataLengthType.get(dbDataType.getDataType());
		} else {
			sortDic = new TreeMap<Long, DbDataType<?>>();
			dbDataType.setParent(this);
			dataLengthType.put(dbDataType.getDataType(), sortDic);
		}
		if (sortDic.containsKey(maxlength)) {
			sortDic.remove(maxlength);
		}
		dbDataType.setParent(this);
		boolean lowerSize = false;
		for (Map.Entry<Long, DbDataType<?>> entry : sortDic.entrySet()) {
			if (maxlength.compareTo(entry.getKey()) >= 0) {
				lowerSize = true;
				break;
			}
		}
		sortDic.put(maxlength, dbDataType);
		if (lowerSize) {
			if (!dataTypeMap.containsKey(dbDataType.getDataType())) {
				register(dbDataType);
			}
		} else {
			register(dbDataType);
		}
	}

	/**
	 * データ型を登録します
	 * 
	 * @param dbDataType データ型
	 */
	protected DbDataType<?> register(DbDataType<?> dbDataType) {
		if (dataTypeMap.containsKey(dbDataType.getDataType())) {
			dataTypeMap.remove(dbDataType.getDataType());
		}
		dbDataType.setParent(this);
		dataTypeMap.put(dbDataType.getDataType(), dbDataType);
		return dbDataType;
	}

	/**
	 * ARRAY型を追加します
	 */
	public void addArray() {
		ArrayType type = new ArrayType();
		register(type);
	}

	/**
	 * CHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addChar(long maxLength, Consumer<CharType> cons) {
		final CharType type = new CharType();
		type.setMaxLength(maxLength);
		type.addFormats("CHARACTER\\s*\\(\\s*([0-9]+)\\s*\\)");
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * CHAR型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addChar(String dataTypeName, long maxLength, Consumer<CharType> cons) {
		final CharType type = new CharType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * CHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 */
	public void addChar(long maxLength) {
		addChar(maxLength, t -> {
		});
	}

	/**
	 * NCHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addNChar(long maxLength, Consumer<NCharType> cons) {
		NCharType type = new NCharType();
		type.setMaxLength(maxLength);
		type.addFormats("NATIONAL\\s+CHARACTER\\s*\\(\\s*([0-9]+)\\s*\\)");
		type.addFormats("NATIONAL\\s+CHAR\\s*\\(\\s*([0-9]+)\\s*\\)");
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * NCHAR型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addNChar(String dataTypeName, long maxLength, Consumer<NCharType> cons) {
		NCharType type = new NCharType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * NCHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 */
	public void addNChar(long maxLength) {
		addNChar(maxLength, t -> {
		});
	}

	/**
	 * NCHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 */
	public void addMChar(long maxLength) {
		MCharType type = new MCharType();
		type.setMaxLength(maxLength);
		registerDataLength(type, maxLength);
	}

	/**
	 * VARCHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 */
	public void addVarchar(long maxLength) {
		addVarchar(maxLength, type -> {
		});
	}

	/**
	 * VARCHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addVarchar(long maxLength, Consumer<VarcharType> cons) {
		VarcharType type = new VarcharType();
		type.setMaxLength(maxLength);
		type.addFormats("CHARACTER\\s*\\(\\s*([0-9]+)\\s*\\)\\s*VARYING");
		type.addFormats("CHAR\\s*\\(\\s*([0-9]+)\\s*\\)\\s*VARYING");
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * VARCHAR型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addVarchar(String dataTypeName, long maxLength, Consumer<VarcharType> cons) {
		VarcharType type = new VarcharType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * VARCHAR IGNORECASE型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addVarcharIgnoreCase(long maxLength, Consumer<VarcharIgnorecaseType> cons) {
		VarcharIgnorecaseType type = new VarcharIgnorecaseType();
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * ALPHANUM型を追加します
	 * 
	 * @param maxLength 最大長
	 */
	public void addAlphanum(long maxLength) {
		AlphanumType type = new AlphanumType();
		type.setMaxLength(maxLength);
		registerDataLength(type, maxLength);
	}

	/**
	 * NVARCHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 */
	public void addNVarchar(long maxLength) {
		addNVarchar(maxLength, type -> {
		});
	}

	/**
	 * NVARCHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addNVarchar(long maxLength, Consumer<NVarcharType> cons) {
		NVarcharType type = new NVarcharType();
		type.setMaxLength(maxLength);
		type.addFormats("NATIONAL\\s+CHARACTER\\s*\\(\\s*([0-9]+)\\s*\\)\\s*VARYING");
		type.addFormats("NATIONAL\\s+CHAR\\s*\\(\\s*([0-9]+)\\s*\\)\\s*VARYING");
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * MVARCHAR型を追加しますを追加します
	 * 
	 * @param maxLength 最大長
	 */
	public void addMVarchar(long maxLength) {
		MVarcharType type = new MVarcharType();
		type.setMaxLength(maxLength);
		registerDataLength(type, maxLength);
	}

	/**
	 * NVARCHAR型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 */
	public void addNVarchar(String dataTypeName, long maxLength) {
		addNVarchar(dataTypeName, maxLength, type -> {
		});
	}

	/**
	 * NVARCHAR型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addNVarchar(String dataTypeName, long maxLength, Consumer<NVarcharType> cons) {
		NVarcharType type = new NVarcharType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * LONGVARCHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 */
	public void addLongNVarchar(long maxLength) {
		addLongVarchar(maxLength, type -> {
		});
	}

	/**
	 * LONGNVARCHAR型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 */
	public void addLongNVarchar(String dataTypeName, long maxLength) {
		addLongNVarchar(dataTypeName, maxLength, type -> {
		});
	}

	/**
	 * LONGNVARCHAR型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addLongNVarchar(String dataTypeName, long maxLength, Consumer<LongNVarcharType> cons) {
		LongNVarcharType type = new LongNVarcharType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * SEARCHABLE_TEXT型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addSearchableText(String dataTypeName, long maxLength, Consumer<SearchableTextType> cons) {
		SearchableTextType type = new SearchableTextType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * SEARCHABLE_SHORTTEXT型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addSearchableShortText(String dataTypeName, long maxLength, Consumer<SearchableShortTextType> cons) {
		SearchableShortTextType type = new SearchableShortTextType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * LONGVARCHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 */
	public void addLongVarchar(long maxLength) {
		addLongVarchar(maxLength, type -> {
		});
	}

	/**
	 * LONGVARCHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addLongVarchar(long maxLength, Consumer<LongVarcharType> cons) {
		LongVarcharType type = new LongVarcharType();
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * LONGVARCHAR型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addLongVarchar(String dataTypeName, long maxLength, Consumer<LongVarcharType> cons) {
		LongVarcharType type = new LongVarcharType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * LONGVARCHAR型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 */
	public void addLongVarchar(String dataTypeName, long maxLength) {
		addLongVarchar(dataTypeName, maxLength, type -> {
		});
	}

	/**
	 * CLOB型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 */
	public void addClob(String dataTypeName, long maxLength) {
		addClob(dataTypeName, maxLength, type -> {
		});
	}

	/**
	 * CHAR型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addClob(long maxLength, Consumer<ClobType> cons) {
		final ClobType type = new ClobType();
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * CHAR型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addClob(String dataTypeName, long maxLength, Consumer<ClobType> cons) {
		final ClobType type = new ClobType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * CLOB型を追加します
	 * 
	 * @param maxLength 最大長
	 */
	public void addClob(long maxLength) {
		ClobType type = new ClobType();
		type.setMaxLength(maxLength);
		registerDataLength(type, maxLength);
	}

	/**
	 * NCLOB型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 */
	public void addNClob(String dataTypeName, long addNClob) {
		addNClob(dataTypeName, addNClob, type -> {
		});
	}

	/**
	 * NCLOB型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addNClob(String dataTypeName, long maxLength, Consumer<NClobType> cons) {
		final NClobType type = new NClobType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * BINARY型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addBinary(String dataTypeName, long maxLength, Consumer<BinaryType> cons) {
		BinaryType type = new BinaryType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * BINARY型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addBinary(long maxLength, Consumer<BinaryType> cons) {
		BinaryType type = new BinaryType();
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * VARBINARY型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addVarBinary(String dataTypeName, long maxLength, Consumer<VarBinaryType> cons) {
		VarBinaryType type = new VarBinaryType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * VARBINARY型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addVarBinary(long maxLength, Consumer<VarBinaryType> cons) {
		VarBinaryType type = new VarBinaryType();
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * LONGVARBINARY型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addLongVarBinary(long maxLength, Consumer<LongVarBinaryType> cons) {
		LongVarBinaryType type = new LongVarBinaryType();
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * LONGVARBINARY型
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addLongVarBinary(String dataTypeName, long maxLength, Consumer<LongVarBinaryType> cons) {
		LongVarBinaryType type = new LongVarBinaryType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * BLOB型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addBlob(long maxLength, Consumer<BlobType> cons) {
		BlobType type = new BlobType();
		type.setMaxLength(maxLength);
		registerDataLength(type, maxLength);
	}

	/**
	 * BLOB型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addBlob(long maxLength) {
		addBlob(maxLength, type -> {
		});
	}

	/**
	 * BLOB型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addBlob(String dataTypeName, long maxLength, Consumer<BlobType> cons) {
		BlobType type = new BlobType(dataTypeName);
		type.setDefaultLength(maxLength);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * BIT型を追加します
	 * 
	 * @param maxLength 最大長
	 * @param cons      型の初期化のConsumer
	 */
	public void addBit(long maxLength, Consumer<BitType> cons) {
		BitType type = new BitType();
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * BIT型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addBit(String dataTypeName, long maxLength, Consumer<BitType> cons) {
		BitType type = new BitType(dataTypeName);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * BOOLEAN型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addBoolean() {
		addBoolean(type -> {
		});
	}

	/**
	 * BOOLEAN型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addBoolean(Consumer<BooleanType> cons) {
		BooleanType type = new BooleanType();
		cons.accept(type);
		register(type);
	}

	/**
	 * BooleanType型を追加します
	 * 
	 * @param dataTypeName データ型名
	 */
	public void addBoolean(String dataTypeName) {
		addBoolean(dataTypeName, type -> {
		});
	}

	/**
	 * BooleanType型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addBoolean(String dataTypeName, Consumer<BooleanType> cons) {
		final BooleanType type = new BooleanType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * TINYINT型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addTinyInt(Consumer<TinyIntType> cons) {
		TinyIntType type = new TinyIntType();
		cons.accept(type);
		register(type);
	}

	/**
	 * TINYINT型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addTinyInt(String dataTypeName, Consumer<TinyIntType> cons) {
		TinyIntType type = new TinyIntType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * SMALLINT型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addSmallInt(Consumer<SmallIntType> cons) {
		SmallIntType type = new SmallIntType();
		cons.accept(type);
		register(type);
	}

	/**
	 * SMALLINT型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addSmallInt(String dataTypeName, Consumer<SmallIntType> cons) {
		SmallIntType type = new SmallIntType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * MEDIUNINT型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addMediumInt(Consumer<MediumIntType> cons) {
		MediumIntType type = new MediumIntType();
		cons.accept(type);
		register(type);
	}

	/**
	 * MEDIUNINT型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addMediumInt(String dataTypeName, Consumer<MediumIntType> cons) {
		MediumIntType type = new MediumIntType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * INT型を追加します
	 */
	public void addInt() {
		IntType type = new IntType();
		register(type);
	}

	/**
	 * NCHAR型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addInt(Consumer<IntType> cons) {
		IntType type = new IntType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INT型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addInt(String dataTypeName, Consumer<IntType> cons) {
		IntType type = new IntType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * BIGINT型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addBigInt(Consumer<BigIntType> cons) {
		BigIntType type = new BigIntType();
		cons.accept(type);
		register(type);
	}

	/**
	 * BIGINT型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addBigInt(String dataTypeName, Consumer<BigIntType> cons) {
		BigIntType type = new BigIntType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * HUGEINT型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addHugeIntType(Consumer<HugeIntType> cons) {
		HugeIntType type = new HugeIntType();
		cons.accept(type);
		register(type);
	}

	/**
	 * HUGEINT型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addHugeIntType(String dataTypeName, Consumer<HugeIntType> cons) {
		HugeIntType type = new HugeIntType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * 16bit整数型(IDENTITY)型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addSmallSerial(Consumer<SmallSerialType> cons) {
		SmallSerialType type = new SmallSerialType();
		cons.accept(type);
		register(type);
	}

	/**
	 * 16bit整数型(IDENTITY)型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addSmallSerial(String dataTypeName, Consumer<SmallSerialType> cons) {
		SmallSerialType type = new SmallSerialType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * 32bit整数型(IDENTITY)型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addSerial(Consumer<SerialType> cons) {
		SerialType type = new SerialType();
		cons.accept(type);
		register(type);
	}

	/**
	 * 32bit整数型(IDENTITY)型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addSerial(String dataTypeName, Consumer<SerialType> cons) {
		SerialType type = new SerialType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * 64bit整数型(IDENTITY)の追加
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addBigSerial(Consumer<BigSerialType> cons) {
		BigSerialType type = new BigSerialType();
		cons.accept(type);
		register(type);
	}

	/**
	 * 64bit整数型(IDENTITY)の追加
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addBigSerial(String dataTypeName, Consumer<BigSerialType> cons) {
		BigSerialType type = new BigSerialType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * UTINYINT型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addUTinyInt(Consumer<UTinyIntType> cons) {
		UTinyIntType type = new UTinyIntType();
		cons.accept(type);
		register(type);
	}

	/**
	 * UTINYINT型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addUTinyInt(String dataTypeName, Consumer<UTinyIntType> cons) {
		UTinyIntType type = new UTinyIntType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * USMALLINT型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addUSmallInt(Consumer<USmallIntType> cons) {
		USmallIntType type = new USmallIntType();
		cons.accept(type);
		register(type);
	}

	/**
	 * USMALLINT型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addUSmallInt(String dataTypeName, Consumer<USmallIntType> cons) {
		USmallIntType type = new USmallIntType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * UMEDIUMINT型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addUMediumInt(Consumer<UMediumIntType> cons) {
		UMediumIntType type = new UMediumIntType();
		cons.accept(type);
		register(type);
	}

	/**
	 * UINT32型を追加します
	 */
	public UIntType addUInt() {
		UIntType type = new UIntType();
		register(type);
		return type;
	}

	/**
	 * UINT32型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addUInt(String dataTypeName, Consumer<UIntType> cons) {
		UIntType type = new UIntType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * UINT64型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addUBigInt(Consumer<UBigIntType> cons) {
		UBigIntType type = new UBigIntType();
		cons.accept(type);
		register(type);
	}

	/**
	 * UINT64型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addUBigInt(String dataTypeName, Consumer<UBigIntType> cons) {
		UBigIntType type = new UBigIntType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * UUID型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addUUID(Consumer<UUIDType> cons) {
		UUIDType type = new UUIDType();
		cons.accept(type);
		register(type);
	}

	/**
	 * UUID型を追加します
	 * 
	 * @param dataTypeName データ型名
	 */
	public void addUUID(String dataTypeName) {
		addUUID(dataTypeName, type -> {
		});
	}

	/**
	 * UUID型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addUUID(String dataTypeName, Consumer<UUIDType> cons) {
		final UUIDType type = new UUIDType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * SQLXML型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addSqlXml(String dataTypeName, Consumer<SqlXmlType> cons) {
		SqlXmlType type = new SqlXmlType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * SQLXML型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param maxLength    最大長
	 * @param cons         型の初期化のConsumer
	 */
	public void addSqlXml(String dataTypeName, long maxLength, Consumer<SqlXmlType> cons) {
		SqlXmlType type = new SqlXmlType(dataTypeName);
		type.setMaxLength(maxLength);
		cons.accept(type);
		registerDataLength(type, maxLength);
	}

	/**
	 * REAL型を追加を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 * 
	 */
	public void addReal(Consumer<RealType> cons) {
		RealType type = new RealType();
		cons.accept(type);
		register(type);
	}

	/**
	 * REAL型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addReal(String dataTypeName, Consumer<RealType> cons) {
		RealType type = new RealType(dataTypeName);
		register(type);
		cons.accept(type);
	}

	/**
	 * DOUBLE型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 * 
	 */
	public void addDouble(Consumer<DoubleType> cons) {
		DoubleType type = new DoubleType();
		type.addFormats("DOUBLE\\s+PRECISION");
		cons.accept(type);
		register(type);
	}

	/**
	 * DOUBLE型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addDouble(String dataTypeName, Consumer<DoubleType> cons) {
		DoubleType type = new DoubleType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * FLOAT型を追加します
	 * 
	 * @param maxLength 最大長
	 */
	public void addFloat(long maxLength) {
		FloatType type = new FloatType();
		registerDataLength(type, maxLength);
	}

	/**
	 * DECIMALFLOAT型を追加します
	 * 
	 * @param maxLength 最大長
	 * 
	 */
	public void addDecimalFloat(long maxLength) {
		DecimalFloatType type = new DecimalFloatType();
		registerDataLength(type, maxLength);
	}

	/**
	 * DECIMALFLOAT型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * 
	 */
	public void addDecimalFloat(String dataTypeName) {
		DecimalFloatType type = new DecimalFloatType(dataTypeName);
		register(type);
	}

	/**
	 * DATE型を追加を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 * 
	 */
	public void addDate(Consumer<DateType> cons) {
		DateType type = new DateType();
		cons.accept(type);
		register(type);
	}

	/**
	 * SMALLDATETIME型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addSmallDateTime(String dataTypeName, Consumer<SmallDateTimeType> cons) {
		SmallDateTimeType type = new SmallDateTimeType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * SMALLDATETIME型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addSmallDateTime(Consumer<SmallDateTimeType> cons) {
		SmallDateTimeType type = new SmallDateTimeType();
		cons.accept(type);
		register(type);
	}

	/**
	 * DATETIME型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addDateTime(Consumer<DateTimeType> cons) {
		DateTimeType type = new DateTimeType();
		cons.accept(type);
		register(type);
	}

	/**
	 * DATETIME型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addDateTime(String dataTypeName, Consumer<DateTimeType> cons) {
		DateTimeType type = new DateTimeType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * TIME型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addTime(Consumer<TimeType> cons) {
		TimeType type = new TimeType();
		cons.accept(type);
		register(type);
	}

	/**
	 * TIME型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addTime(String dataTypeName, Consumer<TimeType> cons) {
		TimeType type = new TimeType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * TIME WITH TIME ZONE型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addTimeWithTimeZone(Consumer<TimeWithTimeZoneType> cons) {
		TimeWithTimeZoneType type = new TimeWithTimeZoneType();
		cons.accept(type);
		register(type);
	}

	/**
	 * TIME WITH TIME ZONE型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addTimeWithTimeZone(String dataTypeName, Consumer<TimeWithTimeZoneType> cons) {
		TimeWithTimeZoneType type = new TimeWithTimeZoneType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * TIMESTAMP型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addTimestamp(Consumer<TimestampType> cons) {
		TimestampType type = new TimestampType();
		cons.accept(type);
		register(type);
	}

	/**
	 * TIMESTAMP型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addTimestamp(String dataTypeName, Consumer<TimestampType> cons) {
		TimestampType type = new TimestampType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * TIMESTAMP(MySQL)型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addTimestampVersion(Consumer<TimestampVersionType> cons) {
		TimestampVersionType type = new TimestampVersionType();
		cons.accept(type);
		register(type);
	}

	/**
	 * TIMESTAMP(MySQL)型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addTimestampVersion(String dataTypeName, Consumer<TimestampVersionType> cons) {
		TimestampVersionType type = new TimestampVersionType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * TIMESTAMP WITH TIMEZONE型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addTimestampWithTimeZoneType(Consumer<TimestampWithTimeZoneType> cons) {
		TimestampWithTimeZoneType type = new TimestampWithTimeZoneType();
		cons.accept(type);
		register(type);
	}

	/**
	 * TIMESTAMP WITH TIMEZONE型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addTimestampWithTimeZoneType(String dataTypeName, Consumer<TimestampWithTimeZoneType> cons) {
		TimestampWithTimeZoneType type = new TimestampWithTimeZoneType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * 行バージョン型(SQLServer用)を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addRowVersion(Consumer<RowVersionType> cons) {
		RowVersionType type = new RowVersionType();
		cons.accept(type);
		register(type);
	}

	/**
	 * 行バージョン型(SQLServer用)を追加します
	 * 
	 * @param dataTypeName
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addRowVersion(String dataTypeName, Consumer<RowVersionType> cons) {
		RowVersionType type = new RowVersionType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * Decimal型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addDecimal(Consumer<DecimalType> cons) {
		DecimalType type = new DecimalType();
		cons.accept(type);
		register(type);
	}

	/**
	 * Decimal型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addDecimal(String dataTypeName, Consumer<DecimalType> cons) {
		DecimalType type = new DecimalType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * NUMERIC型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addNumeric(Consumer<NumericType> cons) {
		NumericType type = new NumericType();
		cons.accept(type);
		register(type);
	}

	/**
	 * NUMERIC型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addNumeric(String dataTypeName, Consumer<DecimalType> cons) {
		NumericType type = new NumericType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * SMALLMONEY型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addSmallMoney(String dataTypeName, Consumer<SmallMoneyType> cons) {
		SmallMoneyType type = new SmallMoneyType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * MONEY型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addMoney(String dataTypeName, Consumer<MoneyType> cons) {
		MoneyType type = new MoneyType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * DATALINK型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addDataLinkType(String dataTypeName, Consumer<DataLinkType> cons) {
		DataLinkType type = new DataLinkType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * INET型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addInetType(Consumer<InetType> cons) {
		InetType type = new InetType();
		cons.accept(type);
		register(type);
	}

	/**
	 * CIDR型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addCidrType(Consumer<CidrType> cons) {
		CidrType type = new CidrType();
		cons.accept(type);
		register(type);
	}

	/**
	 * MACADDR型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addMacAddrType(Consumer<MacAddrType> cons) {
		MacAddrType type = new MacAddrType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addInterval(Consumer<IntervalType> cons) {
		IntervalType type = new IntervalType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL YEAR型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalYear(Consumer<IntervalYearType> cons) {
		IntervalYearType type = new IntervalYearType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL MONTH型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalMonth(Consumer<IntervalMonthType> cons) {
		IntervalMonthType type = new IntervalMonthType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL DAY型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalDay(Consumer<IntervalDayType> cons) {
		IntervalDayType type = new IntervalDayType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL HOUR型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalHour(Consumer<IntervalHourType> cons) {
		IntervalHourType type = new IntervalHourType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL MINUTE型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalMinute(Consumer<IntervalMinuteType> cons) {
		IntervalMinuteType type = new IntervalMinuteType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL SECOND型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalSecond(Consumer<IntervalSecondType> cons) {
		IntervalSecondType type = new IntervalSecondType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL YEAR TO MONTH型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalYearToMonth(Consumer<IntervalYearToMonthType> cons) {
		IntervalYearToMonthType type = new IntervalYearToMonthType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL YEAR TO DAY型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalYearToDay(Consumer<IntervalYearToDayType> cons) {
		IntervalYearToDayType type = new IntervalYearToDayType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL DAY TO HOUR型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalDayToHour(Consumer<IntervalDayToHourType> cons) {
		IntervalDayToHourType type = new IntervalDayToHourType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL DAY TO MINUTE型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalDayToMinute(Consumer<IntervalDayToMinuteType> cons) {
		IntervalDayToMinuteType type = new IntervalDayToMinuteType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL DAY TO SECOND型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalDayToSecond(Consumer<IntervalDayToSecondType> cons) {
		IntervalDayToSecondType type = new IntervalDayToSecondType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL HOUR TO MINUTE型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalHourToMinute(Consumer<IntervalHourToMinuteType> cons) {
		IntervalHourToMinuteType type = new IntervalHourToMinuteType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL HOUR TO SECOND型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalHourToSecond(Consumer<IntervalHourToSecondType> cons) {
		IntervalHourToSecondType type = new IntervalHourToSecondType();
		cons.accept(type);
		register(type);
	}

	/**
	 * INTERVAL MINUTE TO SECOND型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addIntervalMinuteToSecond(Consumer<IntervalMinuteToSecondType> cons) {
		IntervalMinuteToSecondType type = new IntervalMinuteToSecondType();
		cons.accept(type);
		register(type);
	}

	/**
	 * GEOMETRY型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addGeometry(Consumer<GeometryType> cons) {
		GeometryType type = new GeometryType();
		cons.accept(type);
		register(type);
	}

	/**
	 * GEOMETRY型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addGeometry(String dataTypeName, Consumer<GeometryType> cons) {
		GeometryType type = new GeometryType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * GEOGRAPHY型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addGeography(Consumer<GeographyType> cons) {
		GeographyType type = new GeographyType();
		cons.accept(type);
		register(type);
	}

	/**
	 * ENUM型を追加します
	 */
	public void addEnum() {
		EnumType type = new EnumType();
		register(type);
	}

	/**
	 * SET型を追加します
	 */
	public void addSet() {
		SetType type = new SetType();
		register(type);
	}

	/**
	 * YES_OR_NO型を追加します
	 * 
	 */
	public void addYesOrNo() {
		YesOrNoType type = new YesOrNoType();
		register(type);
	}

	/**
	 * ROWID型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 * 
	 */
	public void addRowId(Consumer<RowIdType> cons) {
		RowIdType type = new RowIdType();
		cons.accept(type);
		register(type);
	}

	/**
	 * ROWID型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * @param cons         型の初期化のConsumer
	 */
	public void addRowId(String dataTypeName, Consumer<RowIdType> cons) {
		RowIdType type = new RowIdType(dataTypeName);
		cons.accept(type);
		register(type);
	}

	/**
	 * ANY_DATA型を追加します
	 * 
	 */
	public void addAnyData() {
		AnyDataType type = new AnyDataType();
		register(type);
	}

	/**
	 * ANY_DATA型を追加します
	 * 
	 * @param dataTypeName データ型名
	 * 
	 */
	public void addAnyData(String dataTypeName) {
		AnyDataType type = new AnyDataType(dataTypeName);
		register(type);
	}

	/**
	 * SQL_IDENTIFIER型を追加します
	 * 
	 * @param dataTypeName データ型名
	 */
	public void addSqlIdentifierType(String dataTypeName) {
		SqlIdentifierType type = new SqlIdentifierType(dataTypeName);
		register(type);
	}

	/**
	 * SQL_IDENTIFIER型を追加します
	 */
	public void addSqlIdentifierType() {
		SqlIdentifierType type = new SqlIdentifierType();
		register(type);
	}

	/**
	 * POINT型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addPointType(Consumer<PointType> cons) {
		PointType type = new PointType();
		cons.accept(type);
		register(type);
	}

	/**
	 * CIRCLE型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addCircleType(Consumer<CircleType> cons) {
		CircleType type = new CircleType();
		cons.accept(type);
		register(type);
	}

	/**
	 * LINE型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addLineType(Consumer<LineType> cons) {
		LineType type = new LineType();
		cons.accept(type);
		register(type);
	}

	/**
	 * BOX型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addBoxType(Consumer<BoxType> cons) {
		BoxType type = new BoxType();
		cons.accept(type);
		register(type);
	}

	/**
	 * LSEG型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addLsegType(Consumer<LsegType> cons) {
		LsegType type = new LsegType();
		cons.accept(type);
		register(type);
	}

	/**
	 * PATH型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addPathType(Consumer<PathType> cons) {
		PathType type = new PathType();
		cons.accept(type);
		register(type);
	}

	/**
	 * POLYGON型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addPolygonType(Consumer<PolygonType> cons) {
		PolygonType type = new PolygonType();
		cons.accept(type);
		register(type);
	}

	/**
	 * Json型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addJsonType(Consumer<JsonType> cons) {
		JsonType type = new JsonType();
		cons.accept(type);
		register(type);
	}

	/**
	 * Jsonb型を追加します
	 * 
	 * @param cons 型の初期化のConsumer
	 */
	public void addJsonbType(Consumer<JsonbType> cons) {
		JsonbType type = new JsonbType();
		cons.accept(type);
		register(type);
	}

	protected Map<DataType, DataType> getSurrogateMap() {
		return surrogateMap;
	}
}
