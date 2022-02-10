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

package com.sqlapp.data.db.datatype;

import static com.sqlapp.util.CommonUtils.enumMap;
import static com.sqlapp.util.CommonUtils.set;
import static com.sqlapp.util.CommonUtils.toInteger;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
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
	private transient Function<String,String> arrayPatternGenerator=new ArrayPatternGenerator();
	/**
	 * Array Dimension Handler
	 */
	private transient BiConsumer<Matcher,ArrayDimensionProperties<?>> arrayDimensionHandler=new ArrayDimensionHandler();
	/**
	 * 代替型マップ
	 */
	private final Map<DataType, DataType> surrogateMap = DataType.getSurrogateMap();
	
	static class ArrayPatternGenerator implements Function<String,String>{
		@Override
		public String apply(String t) {
			return "(?<dataTypeName>"+CommonUtils.trim(t)+")"+"\\s*ARRAY\\\\s*\\\\[\\\\s*([0-9]+){0,1}\\\\s*\\\\]";
		}
	}
	
	static class ArrayDimensionHandler implements BiConsumer<Matcher,ArrayDimensionProperties<?>>{
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
	 * @param baseType
	 *            元になる型名
	 * @param recommendType
	 *            推奨されるJDBCの型
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
	 * @param type
	 *            型
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
	 * @param type
	 *            型
	 */
	public DbDataType<?> getDbType(DataType type) {
		Set<DataType> typeSet = set();
		return getDbType(type, typeSet);
	}

	/**
	 * 指定した最適DB型を取得します
	 * 
	 * @param type
	 *            型
	 * @param typeSet
	 *            再帰操作中の型履歴
	 */
	private DbDataType<?> getDbType(DataType type, Set<DataType> typeSet) {
		if (type==null) {
			return null;
		}
		if (typeSet.contains(type)) {
			return null;
		}
		DbDataType<?> dbType = dataTypeMap.get(type);
		if (dbType!=null) {
			if (dbType.isDeprecated()) {
				return dbType.getDeprecatedSurrogateType();
			} else {
				return dbType;
			}
		}
		typeSet.add(type);
		DataType surrogate = type.getUpperSurrogate();
		DbDataType<?> result= getDbType(surrogate, typeSet);
		if (result!=null){
			return result;
		}
		surrogate = getSurrogateMap().get(type);
		result= getDbType(surrogate, typeSet);
		if (result!=null){
			return result;
		}
		return getDbType(surrogate, typeSet);
	}

	/**
	 * 指定した型、サイズのDB型の取得
	 * 
	 * @param type
	 *            JDBCの型
	 * @param columnSize
	 *            カラムのサイズ
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
	 * @param dataType
	 *            JDBCの型
	 * @param columnSize
	 *            カラムのサイズ
	 */
	public DbDataType<?> getDbTypeStrict(DataType dataType, Long columnSize) {
		if (columnSize == null) {
			return getDbTypeStrict(dataType);
		}
		DbDataType<?> result=getDbTypeLengthInternal(dataType, columnSize);
		return result;
	}


	/**
	 * 指定した型、サイズの最適DB型の取得
	 * 
	 * @param dataType
	 *            JDBCの型
	 * @param columnSize
	 *            カラムのサイズ
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
	 * @param dataType
	 *            JDBCの型
	 * @param columnSize
	 *            カラムのサイズ
	 */
	public DbDataType<?> getDbType(DataType dataType, Long columnSize) {
		Set<DataType> typeSet = set();
		DbDataType<?> dbDataType = null;
		if (columnSize == null || (columnSize.longValue() <= 0)) {
			dbDataType = this.getDbType(dataType);
			if (dbDataType != null) {
				if (dbDataType instanceof LengthProperties) {
					LengthProperties<?> lengthProperties=(LengthProperties<?>)dbDataType;
					if (columnSize!=null&&lengthProperties.getMaxLength()!=null&&columnSize.compareTo(lengthProperties.getMaxLength())>0) {
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
		if (dbDataType==null){
			dbDataType=this.getDbType(dataType);
		}
		return dbDataType;
	}

	/**
	 * 指定した型、サイズのDBタイプの取得
	 * 
	 * @param jdbcType
	 *            JDBCの型
	 * @param columnSize
	 *            カラムのサイズ
	 */
	private DbDataType<?> getDbType(DataType type, Long columnSize,
			Set<DataType> typeSet) {
		if (type==null) {
			return null;
		}
		if (typeSet.contains(type)) {
			return null;
		}
		DataType surrogate = getSurrogateMap().get(type);
		if (columnSize != null && columnSize.longValue() >= 0) {
			DbDataType<?> dbDataType=getDbTypeLengthInternal(type, columnSize, typeSet);
			if (dbDataType!=null){
				return dbDataType;
			} else{
				// 指定サイズのDB型がない場合は、上位型、NATIONAL CHAR代替型、代替型から探して、それでもなければ最大桁数の型で妥協する
				dbDataType=getDbTypeLengthInternal(type.getUpperSurrogate(), columnSize);
				if (dbDataType!=null){
					return dbDataType;
				}
				dbDataType=getDbTypeLengthInternal(type.getNationalSurrogate(), columnSize, typeSet);
				if (dbDataType!=null){
					return dbDataType;
				}
				if (type.getUpperSurrogate()!=null){
					dbDataType=getDbType(type.getUpperSurrogate(), columnSize, typeSet);
					if (dbDataType!=null){
						return dbDataType;
					}
				}
				dbDataType=getDbType(surrogate, columnSize, typeSet);
				if (dbDataType!=null){
					return dbDataType;
				}
				return getMaxDbDataType(type, type.getUpperSurrogate(), surrogate);
			}
		} else{
			typeSet.add(type);
		}
		return getDbType(surrogate, typeSet);
	}

	private DbDataType<?> getDbTypeLengthInternal(DataType type, Long columnSize, Set<DataType> typeSet){
		if (type==null) {
			return null;
		}
		if (typeSet.contains(type)) {
			return null;
		}
		typeSet.add(type);
		return getDbTypeLengthInternal(type, columnSize);
	}

	private DbDataType<?> getMaxDbDataType(DataType... types){
		for(DataType type:types){
			if (type==null){
				continue;
			}
			TreeMap<Long, DbDataType<?>> dic = dataLengthType.get(type);
			if (dic==null){
				continue;
			}
			Map.Entry<Long, DbDataType<?>> entry=dic.lastEntry();
			if (entry!=null){
				return entry.getValue();
			}
		}
		return null;
	}
	
	private DbDataType<?> getDbTypeLengthInternal(DataType type, Long columnSize){
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
			Map.Entry<Long, DbDataType<?>> entry=dic.ceilingEntry(columnSize);
			if (entry!=null&&entry.getKey() >= columnSize) {
				last=entry.getValue();
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

	private Set<String> noMatchProductDataTypeDbTypeCache=CommonUtils.set();

	private DoubleKeyMap<String, Long, DbDataType<?>> productDataLengthTypeDbTypeCache = CommonUtils.doubleKeyMap();

	/**
	 * 製品固有のデータ型にマッチするデータ型の取得
	 * 
	 * @param productDataType
	 *            製品固有のデータ型
	 * @param length
	 * @param column
	 */
	public DbDataType<?> match(String productDataType, Long length, final DataTypeLengthProperties<?> column) {
		if (length==null) {
			if (noMatchProductDataTypeDbTypeCache.contains(productDataType)){
				return null;
			}
			DbDataType<?> result=productDataTypeDbTypeCache.get(productDataType);
			if (result!=null) {
				return result;
			}
			for (Map.Entry<DataType, TreeMap<Long, DbDataType<?>>> entry : dataLengthType
					.entrySet()) {
				TreeMap<Long, DbDataType<?>> childMap = entry.getValue();
				for (Map.Entry<Long, DbDataType<?>> childEntry : childMap
						.entrySet()) {
					DbDataType<?> dbDataType = childEntry.getValue();
					if (dbDataType.parseAndSet(productDataType, column)) {
						productDataTypeDbTypeCache.put(productDataType, dbDataType);
						return dbDataType;
					}
				}
			}
		} else {
			DbDataType<?> result=productDataLengthTypeDbTypeCache.get(productDataType, length);
			if (result!=null) {
				return result;
			}
			for (Map.Entry<DataType, TreeMap<Long, DbDataType<?>>> entry : dataLengthType
					.entrySet()) {
				TreeMap<Long, DbDataType<?>> childMap = entry.getValue();
				for (Map.Entry<Long, DbDataType<?>> childEntry : childMap
						.entrySet()) {
					if (length!=null&&childEntry.getKey().compareTo(length)>=0) {
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
	 * 
	 * @param jdbcType
	 * @param dbDataType
	 * @param size
	 */
	protected void addDataLength(DbDataType<?> dbDataType, Long size) {
		TreeMap<Long, DbDataType<?>> sortDic = null;
		//nationalCharDataLengthType
		if (dataLengthType.containsKey(dbDataType.getDataType())) {
			sortDic = dataLengthType.get(dbDataType.getDataType());
		} else {
			sortDic = new TreeMap<Long, DbDataType<?>>();
			dbDataType.setParent(this);
			dataLengthType.put(dbDataType.getDataType(), sortDic);
		}
		if (sortDic.containsKey(size)) {
			sortDic.remove(size);
		}
		dbDataType.setParent(this);
		boolean lowerSize=false;
		for(Map.Entry<Long, DbDataType<?>> entry:sortDic.entrySet()) {
			if (size.compareTo(entry.getKey())>=0) {
				lowerSize=true;
				break;
			}
		}
		sortDic.put(size, dbDataType);
		if (lowerSize) {
			if (!dataTypeMap.containsKey(dbDataType.getDataType())) {
				add(dbDataType);
			}
		} else {
			add(dbDataType);
		}
	}

	protected DbDataType<?> add(DbDataType<?> dbDataType) {
		if (dataTypeMap.containsKey(dbDataType.getDataType())) {
			dataTypeMap.remove(dbDataType.getDataType());
		}
		dbDataType.setParent(this);
		dataTypeMap.put(dbDataType.getDataType(), dbDataType);
		return dbDataType;
	}

	/**
	 * ARRAY型の追加
	 */
	public ArrayType addArray() {
		ArrayType type = new ArrayType();
		add(type);
		return type;
	}

	/**
	 * CHAR型の追加
	 * 
	 * @param size
	 */
	public CharType addChar(long size) {
		CharType type = new CharType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * NCHAR型の追加
	 * 
	 * @param size
	 */
	public NCharType addNChar(long size) {
		NCharType type = new NCharType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * MCHAR型の追加
	 * 
	 * @param size
	 */
	public MCharType addMChar(long size) {
		MCharType type = new MCharType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * NCHAR型の追加
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public NCharType addNChar(String dataTypeName, long size) {
		NCharType type = new NCharType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * VARCHAR型の追加
	 * 
	 * @param size
	 */
	public VarcharType addVarchar(long size) {
		VarcharType type = new VarcharType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * VARCHAR IGNORECASE型の追加
	 * 
	 * @param size
	 */
	public VarcharIgnorecaseType addVarcharIgnoreCase(long size) {
		VarcharIgnorecaseType type = new VarcharIgnorecaseType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * ALPHANUM型を追加します
	 * 
	 * @param size
	 *            最大サイズ
	 */
	public AlphanumType addAlphanum(long size) {
		AlphanumType type = new AlphanumType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * NVARCHAR型の追加
	 * 
	 * @param size
	 */
	public NVarcharType addNVarchar(long size) {
		NVarcharType type = new NVarcharType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * MVARCHAR型の追加
	 * 
	 * @param size
	 */
	public MVarcharType addMVarchar(long size) {
		MVarcharType type = new MVarcharType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * VARCHAR型を追加します
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public VarcharType addVarchar(String dataTypeName, long size) {
		VarcharType type = new VarcharType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * NVARCHAR型の追加
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public NVarcharType addNVarchar(String dataTypeName, long size) {
		NVarcharType type = new NVarcharType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * SEARCHABLE_TEXT型の追加
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public SearchableTextType addSearchableText(String dataTypeName, long size) {
		SearchableTextType type = new SearchableTextType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * SEARCHABLE_SHORTTEXT型の追加
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public SearchableShortTextType addSearchableShortText(String dataTypeName,
			long size) {
		SearchableShortTextType type = new SearchableShortTextType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * LONGVARCHAR型の追加
	 * 
	 * @param size
	 */
	public LongVarcharType addLongVarchar(long size) {
		LongVarcharType type = new LongVarcharType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * LONGVARCHAR型の追加
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public LongVarcharType addLongVarchar(String dataTypeName, long size) {
		LongVarcharType type = new LongVarcharType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * LONGNVARCHAR型の追加
	 * 
	 * @param size
	 */
	public LongNVarcharType addLongNVarchar(long size) {
		LongNVarcharType type = new LongNVarcharType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * LONGNVARCHAR型の追加
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public LongNVarcharType addLongNVarchar(String dataTypeName, long size) {
		LongNVarcharType type = new LongNVarcharType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * CLOB型の追加
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public ClobType addClob(String dataTypeName, long size) {
		ClobType type = new ClobType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * CLOB型の追加
	 * 
	 * @param size
	 */
	public ClobType addClob(long size) {
		ClobType type = new ClobType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * NCLOB型の追加
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public NClobType addNClob(String dataTypeName, long size) {
		NClobType type = new NClobType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * LONGNVARCHAR型の追加
	 * 
	 * @param dataTypeName
	 * @param size
	 * @param createFormat
	 */
	public LongNVarcharType addLongNVarchar(String dataTypeName, long size,
			String createFormat) {
		LongNVarcharType type = new LongNVarcharType(dataTypeName);
		type.setMaxLength(size);
		type.setCreateFormat(createFormat);
		addDataLength(type, size);
		return type;
	}

	/**
	 * BINARY型
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public BinaryType addBinary(String dataTypeName, long size) {
		BinaryType type = new BinaryType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * BINARY型
	 * 
	 * @param size
	 */
	public BinaryType addBinary(long size) {
		BinaryType type = new BinaryType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * VARBINARY型
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public VarBinaryType addVarBinary(String dataTypeName, long size) {
		VarBinaryType type = new VarBinaryType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * VARBINARY型
	 * 
	 * @param size
	 */
	public VarBinaryType addVarBinary(long size) {
		VarBinaryType type = new VarBinaryType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * LONGVARBINARY型
	 * 
	 * @param size
	 */
	public LongVarBinaryType addLongVarBinary(long size) {
		LongVarBinaryType type = new LongVarBinaryType();
		addDataLength(type, size);
		return type;
	}

	/**
	 * LONGVARBINARY型
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public LongVarBinaryType addLongVarBinary(String dataTypeName, long size) {
		LongVarBinaryType type = new LongVarBinaryType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * BLOB型
	 * 
	 * @param size
	 */
	public BlobType addBlob(long size) {
		BlobType type = new BlobType();
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * BLOB型
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public BlobType addBlob(String dataTypeName, long size) {
		BlobType type = new BlobType(dataTypeName);
		type.setDefaultLength(size);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}

	/**
	 * BIT型の追加
	 * 
	 * @param dataTypeName
	 * @param createFormat
	 */
	public BitType addBit(String dataTypeName, String createFormat) {
		BitType type = new BitType(dataTypeName);
		type.setCreateFormat(createFormat);
		add(type);
		return type;
	}

	/**
	 * BIT型の追加
	 * 
	 * @param dataTypeName
	 */
	public BitType addBit(String dataTypeName) {
		BitType type = new BitType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * BIT型の追加
	 */
	public BitType addBit() {
		BitType type = new BitType();
		add(type);
		return type;
	}

	/**
	 * BOOLEAN型の追加
	 */
	public BooleanType addBoolean() {
		BooleanType type = new BooleanType();
		add(type);
		return type;
	}

	/**
	 * BOOLEAN型の追加
	 */
	public BooleanType addBoolean(String dataTypeName) {
		BooleanType type = new BooleanType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * BOOLEAN型の追加
	 * 
	 * @param dataTypeName
	 * @param createFormat
	 * @param defaultValueLiteral
	 */
	public BooleanType addBoolean(String dataTypeName, String createFormat,
			String defaultValueLiteral) {
		BooleanType type = new BooleanType(dataTypeName);
		type.setDefaultValueLiteral(defaultValueLiteral);
		add(type);
		return type;
	}

	/**
	 * TINYINT型の追加
	 */
	public TinyIntType addTinyInt() {
		TinyIntType type = new TinyIntType();
		add(type);
		return type;
	}

	/**
	 * TINYINT型の追加
	 * 
	 * @param dataTypeName
	 */
	public TinyIntType addTinyInt(String dataTypeName) {
		TinyIntType type = new TinyIntType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * SMALLINT型の追加
	 */
	public SmallIntType addSmallInt() {
		SmallIntType type = new SmallIntType();
		add(type);
		return type;
	}

	/**
	 * SMALLINT型の追加
	 * 
	 * @param dataTypeName
	 */
	public SmallIntType addSmallInt(String dataTypeName) {
		SmallIntType type = new SmallIntType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * MEDIUNINT型の追加
	 */
	public MediumIntType addMediumInt() {
		MediumIntType type = new MediumIntType();
		add(type);
		return type;
	}

	/**
	 * MEDIUNINT型の追加
	 */
	public MediumIntType addMediumInt(String dataTypeName) {
		MediumIntType type = new MediumIntType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * INT型の追加
	 */
	public IntType addInt() {
		IntType type = new IntType();
		add(type);
		return type;
	}

	/**
	 * INT型の追加
	 */
	public IntType addInt(String dataTypeName) {
		IntType type = new IntType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * BIGINT型の追加
	 */
	public BigIntType addBigInt() {
		BigIntType type = new BigIntType();
		add(type);
		return type;
	}

	/**
	 * BIGINT型の追加
	 */
	public BigIntType addBigInt(String dataTypeName) {
		BigIntType type = new BigIntType(dataTypeName);
		add(type);
		return type;
	}
	
	/**
	 * Add HUGEINT Type
	 */
	public HugeIntType addHugeIntType() {
		HugeIntType type = new HugeIntType();
		add(type);
		return type;
	}

	/**
	 * Add HUGEINT Type
	 */
	public HugeIntType addHugeIntType(String dataTypeName) {
		HugeIntType type = new HugeIntType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * 16bit整数型(IDENTITY)の追加
	 */
	public SmallSerialType addSmallSerial() {
		SmallSerialType type = new SmallSerialType();
		add(type);
		return type;
	}

	/**
	 * 16bit整数型(IDENTITY)の追加
	 */
	public SmallSerialType addSmallSerial(String dataTypeName) {
		SmallSerialType type = new SmallSerialType(dataTypeName);
		add(type);
		return type;
	}

	
	/**
	 * 32bit整数型(IDENTITY)の追加
	 */
	public SerialType addSerial() {
		SerialType type = new SerialType();
		add(type);
		return type;
	}

	/**
	 * 32bit整数型(IDENTITY)の追加
	 */
	public SerialType addSerial(String dataTypeName) {
		SerialType type = new SerialType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * 64bit整数型(IDENTITY)の追加
	 */
	public BigSerialType addBigSerial() {
		BigSerialType type = new BigSerialType();
		add(type);
		return type;
	}

	/**
	 * 64bit整数型(IDENTITY)の追加
	 */
	public BigSerialType addBigSerial(String dataTypeName) {
		BigSerialType type = new BigSerialType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * UTINYINT型の追加
	 */
	public UTinyIntType addUTinyInt() {
		UTinyIntType type = new UTinyIntType();
		add(type);
		return type;
	}

	/**
	 * UTINYINT型の追加
	 */
	public UTinyIntType addUTinyInt(String dataTypeName) {
		UTinyIntType type = new UTinyIntType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * USMALLINT型の追加
	 */
	public USmallIntType addUSmallInt() {
		USmallIntType type = new USmallIntType();
		add(type);
		return type;
	}

	/**
	 * Add USMALLINT Type
	 */
	public USmallIntType addUSmallInt(String dataTypeName) {
		USmallIntType type = new USmallIntType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * UMEDIUMINT型の追加
	 */
	public UMediumIntType addUMediumInt() {
		UMediumIntType type = new UMediumIntType();
		add(type);
		return type;
	}

	/**
	 * UINT32型の追加
	 */
	public UIntType addUInt() {
		UIntType type = new UIntType();
		add(type);
		return type;
	}

	/**
	 * UINT32型の追加
	 */
	public UIntType addUInt(String dataTypeName) {
		UIntType type = new UIntType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * UINT64型の追加
	 */
	public UBigIntType addUBigInt() {
		UBigIntType type = new UBigIntType();
		add(type);
		return type;
	}
	
	/**
	 * UINT64型の追加
	 */
	public UBigIntType addUBigInt(String dataTypeName) {
		UBigIntType type = new UBigIntType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * UUID型の追加
	 */
	public UUIDType addUUID() {
		UUIDType type = new UUIDType();
		add(type);
		return type;
	}

	/**
	 * UUID型の追加
	 * @param dataTypeName
	 */
	public UUIDType addUUID(String dataTypeName) {
		UUIDType type = new UUIDType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * SQLXML型の追加
	 * 
	 * @param dataTypeName
	 */
	public SqlXmlType addSqlXml(String dataTypeName) {
		SqlXmlType type = new SqlXmlType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * SQLXML型
	 * 
	 * @param dataTypeName
	 * @param size
	 */
	public SqlXmlType addSqlXml(String dataTypeName, long size) {
		SqlXmlType type = new SqlXmlType(dataTypeName);
		type.setMaxLength(size);
		addDataLength(type, size);
		return type;
	}
	
	/**
	 * REAL型の追加
	 * 
	 */
	public RealType addReal() {
		RealType type = new RealType();
		add(type);
		return type;
	}

	/**
	 * REAL型の追加
	 * 
	 * @param dataTypeName
	 */
	public RealType addReal(String dataTypeName) {
		RealType type = new RealType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * DOUBLE型の追加
	 * 
	 */
	public DoubleType addDouble() {
		DoubleType type = new DoubleType();
		add(type);
		return type;
	}

	/**
	 * DOUBLE型の追加
	 * 
	 * @param dataTypeName
	 */
	public DoubleType addDouble(String dataTypeName) {
		DoubleType type = new DoubleType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * FLOAT型の追加
	 * 
	 */
	public FloatType addFloat(long size) {
		FloatType type = new FloatType();
		addDataLength(type, size);
		return type;
	}

	/**
	 * DECIMALFLOAT型の追加
	 * 
	 */
	public DecimalFloatType addDecimalFloat(long size) {
		DecimalFloatType type = new DecimalFloatType();
		addDataLength(type, size);
		return type;
	}

	/**
	 * DECIMALFLOAT型を追加します
	 * 
	 */
	public DecimalFloatType addDecimalFloat(String dataTypeName) {
		DecimalFloatType type = new DecimalFloatType(dataTypeName);
		return type;
	}

	/**
	 * DATE型の追加
	 * 
	 */
	public DateType addDate() {
		DateType type = new DateType();
		add(type);
		return type;
	}

	/**
	 * SMALLDATETIME型を追加します
	 */
	public SmallDateTimeType addSmallDateTime(String dataTypeName) {
		SmallDateTimeType type = new SmallDateTimeType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * SMALLDATETIME型を追加します
	 */
	public SmallDateTimeType addSmallDateTime() {
		SmallDateTimeType type = new SmallDateTimeType();
		add(type);
		return type;
	}

	/**
	 * DATETIME型の追加
	 */
	public DateTimeType addDateTime() {
		DateTimeType type = new DateTimeType();
		add(type);
		return type;
	}

	/**
	 * DATETIME型の追加
	 */
	public DateTimeType addDateTime(String dataTypeName) {
		DateTimeType type = new DateTimeType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * TIME型の追加
	 */
	public TimeType addTime() {
		TimeType type = new TimeType();
		add(type);
		return type;
	}

	/**
	 * TIME型の追加
	 */
	public TimeType addTime(String dataTypeName) {
		TimeType type = new TimeType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * TIME WITH TIME ZONE型の追加
	 */
	public TimeWithTimeZoneType addTimeWithTimeZone() {
		TimeWithTimeZoneType type = new TimeWithTimeZoneType();
		add(type);
		return type;
	}

	/**
	 * TIME WITH TIME ZONE型の追加
	 */
	public TimeWithTimeZoneType addTimeWithTimeZone(String dataTypeName) {
		TimeWithTimeZoneType type = new TimeWithTimeZoneType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * TIMESTAMP型の追加
	 */
	public TimestampType addTimestamp() {
		TimestampType type = new TimestampType();
		add(type);
		return type;
	}

	/**
	 * TIMESTAMP型の追加
	 * 
	 * @param dataTypeName
	 */
	public TimestampType addTimestamp(String dataTypeName) {
		TimestampType type = new TimestampType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * TIMESTAMP(MySQL)型の追加
	 */
	public TimestampVersionType addTimestampVersion(String dataTypeName) {
		TimestampVersionType type = new TimestampVersionType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * TIMESTAMP(MySQL)型の追加
	 */
	public TimestampVersionType addTimestampVersion() {
		TimestampVersionType type = new TimestampVersionType();
		add(type);
		return type;
	}

	/**
	 * TIMESTAMP WITH TIMEZONE型の追加
	 * 
	 */
	public TimestampWithTimeZoneType addTimestampWithTimeZoneType() {
		TimestampWithTimeZoneType type = new TimestampWithTimeZoneType();
		add(type);
		return type;
	}

	/**
	 * TIMESTAMP WITH TIMEZONE型の追加
	 * 
	 * @param dataTypeName
	 */
	public TimestampWithTimeZoneType addTimestampWithTimeZoneType(String dataTypeName) {
		TimestampWithTimeZoneType type = new TimestampWithTimeZoneType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * 行バージョン型(SQLServer用)を追加します
	 * 
	 * @param dataTypeName
	 */
	public RowVersionType addRowVersion(String dataTypeName) {
		RowVersionType type = new RowVersionType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * 行バージョン型(SQLServer用)を追加します
	 * 
	 */
	public RowVersionType addRowVersion() {
		RowVersionType type = new RowVersionType();
		add(type);
		return type;
	}

	/**
	 * Decimal型の追加
	 */
	public DecimalType addDecimal() {
		DecimalType type = new DecimalType();
		add(type);
		return type;
	}

	/**
	 * Decimal型の追加
	 * 
	 * @param dataTypeName
	 *            型名
	 */
	public DecimalType addDecimal(String dataTypeName) {
		DecimalType type = new DecimalType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * NUMERIC型の追加
	 * 
	 */
	public NumericType addNumeric() {
		NumericType type = new NumericType();
		add(type);
		return type;
	}

	/**
	 * NUMERIC型の追加
	 * 
	 * @param dataTypeName
	 */
	public NumericType addNumeric(String dataTypeName) {
		NumericType type = new NumericType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * SMALLMONEY型の追加
	 * 
	 * @param dataTypeName
	 *            型名
	 */
	public SmallMoneyType addSmallMoney(String dataTypeName) {
		SmallMoneyType type = new SmallMoneyType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * MONEY型の追加
	 * 
	 * @param dataTypeName
	 *            型名
	 */
	public MoneyType addMoney(String dataTypeName) {
		MoneyType type = new MoneyType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * DATALINK型の追加
	 * 
	 * @param dataTypeName
	 */
	public DataLinkType addDataLinkType(String dataTypeName) {
		DataLinkType type = new DataLinkType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * INET型の追加
	 */
	public InetType addInetType() {
		InetType type = new InetType();
		add(type);
		return type;
	}

	/**
	 * CIDR型の追加
	 */
	public CidrType addCidrType() {
		CidrType type = new CidrType();
		add(type);
		return type;
	}

	/**
	 * MACADDR型の追加
	 */
	public MacAddrType addMacAddrType() {
		MacAddrType type = new MacAddrType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL型の追加
	 */
	public IntervalType addInterval() {
		IntervalType type = new IntervalType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL YEAR型の追加
	 */
	public IntervalYearType addIntervalYear() {
		IntervalYearType type = new IntervalYearType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL MONTH型の追加
	 */
	public IntervalMonthType addIntervalMonth() {
		IntervalMonthType type = new IntervalMonthType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL DAY型の追加
	 */
	public IntervalDayType addIntervalDay() {
		IntervalDayType type = new IntervalDayType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL HOUR型の追加
	 */
	public IntervalHourType addIntervalHour() {
		IntervalHourType type = new IntervalHourType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL MINUTE型の追加
	 */
	public IntervalMinuteType addIntervalMinute() {
		IntervalMinuteType type = new IntervalMinuteType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL SECOND型の追加
	 */
	public IntervalSecondType addIntervalSecond() {
		IntervalSecondType type = new IntervalSecondType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL YEAR TO MONTH型の追加
	 */
	public IntervalYearToMonthType addIntervalYearToMonth() {
		IntervalYearToMonthType type = new IntervalYearToMonthType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL YEAR TO DAY型の追加
	 */
	public IntervalYearToDayType addIntervalYearToDay() {
		IntervalYearToDayType type = new IntervalYearToDayType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL DAY TO HOUR型の追加
	 */
	public IntervalDayToHourType addIntervalDayToHour() {
		IntervalDayToHourType type = new IntervalDayToHourType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL DAY TO MINUTE型の追加
	 */
	public IntervalDayToMinuteType addIntervalDayToMinute() {
		IntervalDayToMinuteType type = new IntervalDayToMinuteType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL DAY TO SECOND型の追加
	 */
	public IntervalDayToSecondType addIntervalDayToSecond() {
		IntervalDayToSecondType type = new IntervalDayToSecondType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL HOUR TO MINUTE型の追加
	 */
	public IntervalHourToMinuteType addIntervalHourToMinute() {
		IntervalHourToMinuteType type = new IntervalHourToMinuteType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL HOUR TO SECOND型の追加
	 */
	public IntervalHourToSecondType addIntervalHourToSecond() {
		IntervalHourToSecondType type = new IntervalHourToSecondType();
		add(type);
		return type;
	}

	/**
	 * INTERVAL MINUTE TO SECOND型の追加
	 */
	public IntervalMinuteToSecondType addIntervalMinuteToSecond() {
		IntervalMinuteToSecondType type = new IntervalMinuteToSecondType();
		add(type);
		return type;
	}

	/**
	 * GEOMETRY型の追加
	 */
	public GeometryType addGeometry() {
		GeometryType type = new GeometryType();
		add(type);
		return type;
	}

	/**
	 * GEOMETRY型の追加
	 */
	public GeometryType addGeometry(String dataTypeName) {
		GeometryType type = new GeometryType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * GEOGRAPHY型の追加
	 */
	public GeographyType addGeography() {
		GeographyType type = new GeographyType();
		add(type);
		return type;
	}

	/**
	 * ENUM型の追加
	 */
	public EnumType addEnum() {
		EnumType type = new EnumType();
		add(type);
		return type;
	}

	/**
	 * SET型の追加
	 */
	public SetType addSet() {
		SetType type = new SetType();
		add(type);
		return type;
	}

	/**
	 * YES_OR_NO型を追加します
	 * 
	 */
	public YesOrNoType addYesOrNo() {
		YesOrNoType type = new YesOrNoType();
		add(type);
		return type;
	}

	/**
	 * ROWID型を追加します
	 * 
	 */
	public RowIdType addRowId() {
		RowIdType type = new RowIdType();
		add(type);
		return type;
	}

	/**
	 * ROWID型を追加します
	 * 
	 * @param dataTypeName
	 */
	public RowIdType addRowId(String dataTypeName) {
		RowIdType type = new RowIdType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * ANY_DATA型を追加します
	 * 
	 */
	public AnyDataType addAnyData() {
		AnyDataType type = new AnyDataType();
		add(type);
		return type;
	}

	/**
	 * ANY_DATA型を追加します
	 * 
	 */
	public AnyDataType addAnyData(String dataTypeName) {
		AnyDataType type = new AnyDataType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * SQL_IDENTIFIER型を追加します
	 * 
	 * @param dataTypeName
	 */
	public SqlIdentifierType addSqlIdentifierType(String dataTypeName) {
		SqlIdentifierType type = new SqlIdentifierType(dataTypeName);
		add(type);
		return type;
	}

	/**
	 * SQL_IDENTIFIER型を追加します
	 */
	public SqlIdentifierType addSqlIdentifierType() {
		SqlIdentifierType type = new SqlIdentifierType();
		add(type);
		return type;
	}

	/**
	 * POINT型を追加します
	 * 
	 */
	public PointType addPointType() {
		PointType type = new PointType();
		add(type);
		return type;
	}

	/**
	 * CIRCLE型を追加します
	 * 
	 */
	public CircleType addCircleType() {
		CircleType type = new CircleType();
		add(type);
		return type;
	}

	/**
	 * LINE型を追加します
	 * 
	 */
	public LineType addLineType() {
		LineType type = new LineType();
		add(type);
		return type;
	}

	/**
	 * BOX型を追加します
	 * 
	 */
	public BoxType addBoxType() {
		BoxType type = new BoxType();
		add(type);
		return type;
	}
	
	/**
	 * LSEG型を追加します
	 * 
	 */
	public LsegType addLsegType() {
		LsegType type = new LsegType();
		add(type);
		return type;
	}

	/**
	 * PATH型を追加します
	 * 
	 */
	public PathType addPathType() {
		PathType type = new PathType();
		add(type);
		return type;
	}

	/**
	 * POLYGON型を追加します
	 * 
	 */
	public PolygonType addPolygonType() {
		PolygonType type = new PolygonType();
		add(type);
		return type;
	}

	/**
	 * Json型を追加します
	 * 
	 */
	public JsonType addJsonType() {
		JsonType type = new JsonType();
		add(type);
		return type;
	}

	/**
	 * Jsonb型を追加します
	 * 
	 */
	public JsonbType addJsonbType() {
		JsonbType type = new JsonbType();
		add(type);
		return type;
	}

	protected Map<DataType, DataType> getSurrogateMap() {
		return surrogateMap;
	}
}
