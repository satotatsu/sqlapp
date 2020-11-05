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

import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.map;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.ArrayDimensionProperties;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;
import com.sqlapp.util.function.TriConsumer;

/**
 * DBのカラムの型情報
 * 
 * @author 竜夫
 * 
 * @param <T>
 */
public abstract class DbDataType<T extends DbDataType<? super T>> implements
		Serializable, Cloneable {
	public static final String LENGTH_REPLACE = "{l}";
	public static final String PRECISION_REPLACE = "{p}";
	public static final String SCALE_REPLACE = "{s}";
	public static final String VALUESET_REPLACE = "{v}";
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -3972508957738381730L;

	/**
	 * コンストラクタ
	 */
	public DbDataType() {
	}

	/**
	 * コンストラクタ
	 * 
	 * @param dataTypeName
	 */
	public DbDataType(String dataTypeName) {
		this.dataTypeName = dataTypeName;
	}

	/**
	 * 作成時のフォーマット正規表現
	 */
	protected List<Pattern> formatList = list();

	protected List<Pattern> arrayFormatList = list();

	/**
	 * 特定のサイズで代替として設定するタイプ
	 */
	protected Map<Long, DataType> sizeSarrogatedType = map();

	private TriConsumer<DbDataType<?>, Matcher, DataTypeLengthProperties<?>> parseAndSetConsumer=(own, m,column)->{};
	
	protected void parseAndSet(Matcher matcher,
			DataTypeLengthProperties<?> column) {
		parseAndSetConsumer.accept(this, matcher, column);
	}

	public T setParseAndSet(TriConsumer<DbDataType<?>, Matcher, DataTypeLengthProperties<?>> parseAndSetConsumer) {
		this.parseAndSetConsumer=parseAndSetConsumer;
		return instance();
	}

	@FunctionalInterface
	public static interface DbDataTypeBiConsumer{
		void accept(DbDataType<?> dbDataType, Matcher matcher , DataTypeLengthProperties<?> column);
	}
	
	
	/**
	 * 初期化
	 * 
	 * @param typeName
	 */
	protected void initialize(String dataTypeName) {
		setTypeName(dataTypeName);
		setCreateFormat(dataTypeName);
	}
	
	/**
	 * 特定のサイズで代替として設定するための型の設定
	 * 
	 * @param size
	 *            サイズ
	 * @param type
	 */
	public T setSizeSarrogation(Long size, DataType type) {
		sizeSarrogatedType.put(size, type);
		return instance();
	}

	public T setSizeSarrogation(long size, DataType type) {
		sizeSarrogatedType.put(size, type);
		return instance();
	}

	/**
	 * 特定のサイズで代替として設定する型の取得
	 * 
	 * @param size
	 */
	public DataType getSizeSarrogation(Long size) {
		return sizeSarrogatedType.get(size);
	}

	/**
	 * 特定のサイズで代替として設定する型の取得
	 * 
	 * @param size
	 */
	public DataType getSizeSarrogation(long size) {
		return sizeSarrogatedType.get(size);
	}

	/**
	 * 所属するDB型コレクション
	 */
	private DbDataTypeCollection parent = null;
	/**
	 * java.sql.typesの共通型
	 */
	private java.sql.JDBCType jdbcType = java.sql.JDBCType.OTHER;
	/**
	 * DBの共通型
	 */
	private DataType dataType = null;
	/**
	 * プロバイダ固有のデータ型名
	 */
	private String dataTypeName = null;
	/**
	 * この列をDDL文に追加する方法を示す書式文字列 例: TIMESTAMP({0} WITH LOCAL TIME ZONE)
	 */
	private String createFormat = null;
	/**
	 * このデータ型の列を作成するために指定されたパラメータ
	 */
	private String createParameters = null;

	/**
	 * オクテットサイズ
	 */
	private Long octetSize = null;
	/**
	 * データ型のjavaクラス
	 */
	private Class<?> type = null;
	/**
	 * このデータ型が自動増加可能かどうかを示すブール値
	 */
	private boolean autoIncrementable = false;
	/**
	 * このデータ型で大/小文字が区別されるかどうかを示すブール値
	 */
	private boolean caseSensitive = false;
	/**
	 * このデータ型に固定長の要素が含まれるかどうかを示すブール値
	 */
	private boolean fixedLength = false;
	/**
	 * このデータ型に精度の要素が含まれるかどうかを示すブール値
	 */
	private boolean fixedPrecision = false;
	/**
	 * このデータ型にスケールが固定の要素が含まれるかどうかを示すブール値
	 */
	private boolean fixedScale = false;
	/**
	 * このデータ型がNULL値可能かどうかを示すブール値
	 */
	private boolean nullable = false;
	/**
	 * データ型をLIKE条件を除く任意の演算子とともにWHERE句で使用できるかどうかを示すブール値
	 */
	private boolean searchable = false;
	/**
	 * このデータ型をLIKE条件とともに使用できるかどうかを示すブール値
	 */
	private boolean searchableWithLike = false;
	/**
	 * このデータ型がunsignedかどうかを示すブール値
	 */
	private boolean unsigned = false;

	/**
	 * 行が変更されるたび、また列の値が以前のすべての値と異なるたびに、データベースがデータ型を更新するかどうかを示すブール値
	 */
	private boolean concurrencyType = false;
	/**
	 * このデータ型をリテラルとして表現できるかどうかを示すブール値
	 */
	private boolean literalSupported = false;
	/**
	 * 指定リテラルの接頭辞 例: TO_TIMESTAMP_TZ('
	 */
	private String literalPrefix = null;
	/**
	 * 指定リテラルの接尾辞 例: ','YYYY-MM-DD HH24:MI:SS.FF')
	 */
	private String literalSuffix = null;
	/**
	 * デフォルト値の構文
	 */
	private String defaultValueLiteral = null;
	/**
	 * JDBCの型ハンドラ
	 */
	private JdbcTypeHandler jdbcTypeHandler = null;
	/**
	 * 配列化可能か
	 */
	private boolean supportsArray = true;
	/**
	 * 非推奨時の代替型
	 */
	private DbDataType<?> deprecatedSurrogateType = null;
	/**
	 * 代替型
	 */
	private DbDataType<?> surrogateType = null;
	/**
	 * DB特化型か?
	 */
	private boolean dbSpecificType = false;
	/**
	 * 文字列の場合のCharset
	 */
	private String charset = null;
	/**
	 * 型コンバーター
	 */
	private Converter<?> converter = null;
	/**
	 * SQL TEXT型コンバーター
	 */
	private Converter<?> sqlTextConverter = null;
	/**
	 * システム内部型
	 */
	private boolean systemInternalType = false;
	/**
	 * 
	 */
	private Set<CharacterSemantics> supportCharacterSemantics=CommonUtils.set();
	
	/**
	 * @return the converter
	 */
	public Converter<?> getConverter() {
		return converter;
	}

	/**
	 * @return the supportCharacterSemantics
	 */
	public Set<CharacterSemantics> getSupportCharacterSemantics() {
		return supportCharacterSemantics;
	}

	/**
	 * @param supportCharacterSemantics the supportCharacterSemantics to set
	 */
	public T setSupportCharacterSemantics(Set<CharacterSemantics> supportCharacterSemantics) {
		this.supportCharacterSemantics = supportCharacterSemantics;
		return instance();
	}

	/**
	 * @param supportCharacterSemantics the supportCharacterSemantics to set
	 */
	public T setSupportCharacterSemantics(CharacterSemantics... supportCharacterSemantics) {
		if (supportCharacterSemantics!=null){
			this.supportCharacterSemantics = CommonUtils.linkedSet(supportCharacterSemantics);
		} else{
			this.supportCharacterSemantics=Collections.emptySet();
		}
		return instance();
	}

	/**
	 * @param converter
	 *            the converter to set
	 */
	public T setConverter(Converter<?> converter) {
		this.converter = converter;
		return instance();
	}

	/**
	 * @return the sqlTextConverter
	 */
	public Converter<?> getSqlTextConverter() {
		if (this.sqlTextConverter==null){
			return this.getConverter();
		}
		return sqlTextConverter;
	}

	/**
	 * @param sqlTextConverter the sqlTextConverter to set
	 */
	public T setSqlTextConverter(Converter<?> sqlTextConverter) {
		this.sqlTextConverter = sqlTextConverter;
		return instance();
	}

	public String getTypeName() {
		return dataTypeName;
	}

	protected T setTypeName(String dataTypeName) {
		this.dataTypeName = dataTypeName;
		if (this.createFormat == null) {
			this.createFormat=dataTypeName;
		}
		return this.instance();
	}

	public String getCreateFormat() {
		return createFormat;
	}

	public T setCreateFormat(String createFormat) {
		this.createFormat = createFormat;
		return this.instance();
	}

	public String getCreateParameters() {
		return createParameters;
	}

	public T setCreateParameters(String createParameters) {
		this.createParameters = createParameters;
		return this.instance();
	}

	public Class<?> getType() {
		return type;
	}

	public T setType(Class<?> clazz) {
		this.type = clazz;
		return this.instance();
	}

	public boolean isAutoIncrementable() {
		return autoIncrementable;
	}

	public T setAutoIncrementable(boolean autoIncrementable) {
		this.autoIncrementable = autoIncrementable;
		return this.instance();
	}

	public boolean isCaseSensitive() {
		return caseSensitive;
	}

	public T setCaseSensitive(boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
		return this.instance();
	}

	public boolean isFixedLength() {
		return fixedLength;
	}

	public T setFixedLength(boolean fixedLength) {
		this.fixedLength = fixedLength;
		return this.instance();
	}

	public boolean isFixedPrecision() {
		return fixedPrecision;
	}

	public T setFixedPrecision(boolean fixedPrecision) {
		this.fixedPrecision = fixedPrecision;
		return this.instance();
	}

	/**
	 * @return the fixedScale
	 */
	public boolean isFixedScale() {
		return fixedScale;
	}

	/**
	 * @param fixedScale
	 *            the fixedScale to set
	 */
	public T setFixedScale(boolean fixedScale) {
		this.fixedScale = fixedScale;
		return this.instance();
	}

	public boolean isNullable() {
		return nullable;
	}

	public T setNullable(boolean nullable) {
		this.nullable = nullable;
		return this.instance();
	}

	public boolean isSearchable() {
		return searchable;
	}

	public T setSearchable(boolean searchable) {
		this.searchable = searchable;
		return this.instance();
	}

	public boolean isSearchableWithLike() {
		return searchableWithLike;
	}

	public T setSearchableWithLike(boolean searchableWithLike) {
		this.searchableWithLike = searchableWithLike;
		return this.instance();
	}

	public boolean isUnsigned() {
		return unsigned;
	}

	public T setUnsigned(boolean unsigned) {
		this.unsigned = unsigned;
		return this.instance();
	}

	public boolean isConcurrencyType() {
		return concurrencyType;
	}

	public T setConcurrencyType(boolean concurrencyType) {
		this.concurrencyType = concurrencyType;
		return this.instance();
	}

	public boolean isLiteralSupported() {
		return literalSupported;
	}

	public T setLiteralSupported(boolean literalSupported) {
		this.literalSupported = literalSupported;
		return this.instance();
	}

	public String getLiteralPrefix() {
		return literalPrefix;
	}

	public T setLiteralPrefix(String literalPrefix) {
		this.literalPrefix = literalPrefix;
		return this.instance();
	}
	
	public String withLiteral(String value) {
		if (value==null) {
			return null;
		}
		if (this.getDataType().isCharacter()||this.getDataType().isBinary()||this.getDataType().isDateTime()) {
			return this.getLiteralPrefix()+value.replace("'", "''")+this.getLiteralSuffix();
		}
		return value;
	}

	public String getLiteralSuffix() {
		return literalSuffix;
	}

	public T setLiteralSuffix(String literalSuffix) {
		this.literalSuffix = literalSuffix;
		return this.instance();
	}

	public String getDefaultValueLiteral() {
		return defaultValueLiteral;
	}

	/**
	 * 指定リテラルの接頭辞と指定リテラルの接尾辞を同時に設定するメソッド
	 * 
	 * @param literalPrefix
	 *            指定リテラルの接頭辞
	 * @param literalSuffix
	 *            指定リテラルの接尾辞
	 */
	public T setLiteral(String literalPrefix, String literalSuffix) {
		this.literalPrefix = literalPrefix;
		this.literalSuffix = literalSuffix;
		return this.instance();
	}

	public T setDefaultValueLiteral(String defaultValueLiteral) {
		this.defaultValueLiteral = defaultValueLiteral;
		return this.instance();
	}

	@Override
	public int hashCode() {
		return (this.getDataType().toString().hashCode() ^ this.getTypeName().hashCode());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof DbDataType)) {
			return false;
		}
		DbDataType<?> objValue = (DbDataType<?>) obj;
		if (!eq(objValue.getCreateFormat(), this.getCreateFormat())) {
			return false;
		}
		if (!eq(objValue.getCreateParameters(), this.getCreateParameters())) {
			return false;
		}
		if (!eq(objValue.getType(), this.getType())) {
			return false;
		}
		if (!eq(objValue.getDefaultValueLiteral(),
				this.getDefaultValueLiteral())) {
			return false;
		}
		if (!eq(objValue.getDataType(), this.getDataType())) {
			return false;
		}
		if (!eq(objValue.getLiteralPrefix(), this.getLiteralPrefix())) {
			return false;
		}
		if (!eq(objValue.getLiteralSuffix(), this.getLiteralSuffix())) {
			return false;
		}
		if (!eq(objValue.getOctetSize(), this.getOctetSize())) {
			return false;
		}
		if (!eq(objValue.getTypeName(), this.getTypeName())) {
			return false;
		}
		if (!eq(objValue.getCharset(), this.getCharset())) {
			return false;
		}
		if (!eq(objValue.isAutoIncrementable(), this.isAutoIncrementable())) {
			return false;
		}
		if (!eq(objValue.isCaseSensitive(), this.isCaseSensitive())) {
			return false;
		}
		if (!eq(objValue.isConcurrencyType(), this.isConcurrencyType())) {
			return false;
		}
		if (!eq(objValue.isFixedLength(), this.isFixedLength())) {
			return false;
		}
		if (!eq(objValue.isFixedPrecision(), this.isFixedPrecision())) {
			return false;
		}
		if (!eq(objValue.isLiteralSupported(), this.isLiteralSupported())) {
			return false;
		}
		if (!eq(objValue.isNullable(), this.isNullable())) {
			return false;
		}
		if (!eq(objValue.isSearchable(), this.isSearchable())) {
			return false;
		}
		if (!eq(objValue.isSearchableWithLike(), this.isSearchableWithLike())) {
			return false;
		}
		if (!eq(objValue.isUnsigned(), this.isUnsigned())) {
			return false;
		}
		if (!eq(objValue.isDeprecated(), this.isDeprecated())) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public T clone() {
		try {
			return (T) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * CREATE TABLE時のカラムの定義情報
	 * 
	 * @param sizeOrPrecision
	 * @param scale
	 */
	public String getColumCreateDefinition(Long sizeOrPrecision, Integer scale) {
		String result = getCreateFormat();
		if (sizeOrPrecision != null) {
			result = result.replace(LENGTH_REPLACE, sizeOrPrecision.toString());
			result = result.replace(PRECISION_REPLACE,
					sizeOrPrecision.toString());
		}
		if (scale != null) {
			result = result.replace(SCALE_REPLACE, scale.toString());
		}
		return result;
	}

	protected void setLiteralSupport() {
		if ((getLiteralPrefix() == null) && (getLiteralSuffix() == null)) {
			setLiteralSupported(false);
		}
		setLiteralSupported(true);
	}

	public DataType getDataType() {
		return dataType;
	}

	@SuppressWarnings("unchecked")
	protected T setDataType(DataType dataType) {
		this.dataType = dataType;
		this.jdbcType = dataType.getJdbcType();
		this.setTypeName(dataType.getTypeName());
		this.setJdbcTypeHandler(new DefaultJdbcTypeHandler(getDataType()));
		this.setAutoIncrementable(dataType.isAutoIncrementable());
		if (dataType.getDefaultClass() != null) {
			this.setType(dataType.getDefaultClass());
		}
		if(this.getConverter()==null){
			this.setConverter(Converters.getDefault().getConverter(dataType.getDefaultClass()));
		}
		if (!dataType.isFixedSize()) {
			for (String alias : dataType.getAliasNames()) {
				this.addFormats(alias);
			}
		}
		return (T) (this);
	}

	public JdbcTypeHandler getJdbcTypeHandler() {
		return jdbcTypeHandler;
	}

	public T setJdbcTypeHandler(JdbcTypeHandler jdbcTypeHandler) {
		this.jdbcTypeHandler = jdbcTypeHandler;
		return this.instance();
	}

	/**
	 * Jdbcタイプハンドラーを設定します
	 * 
	 * @param converter
	 */
	public T setJdbcTypeHandler(Converter<?> converter) {
		this.setJdbcTypeHandler(new DefaultJdbcTypeHandler(this.getDataType()
				.getJdbcType(), converter));
		return this.instance();
	}

	/**
	 * Jdbcタイプハンドラーを設定します
	 * 
	 */
	public T setJdbcTypeHandler(Converter<?> statementConverter,
			Converter<?> resultSetconverter) {
		this.setJdbcTypeHandler(new DefaultJdbcTypeHandler(this.getDataType()
				.getJdbcType(), statementConverter, resultSetconverter));
		return this.instance();
	}

	public Long getOctetSize() {
		return octetSize;
	}

	public T setOctetSize(Long octetSize) {
		this.octetSize = octetSize;
		return this.instance();
	}

	public T setOctetSize(int octetSize) {
		this.octetSize = Long.valueOf(octetSize);
		return this.instance();
	}

	/**
	 * マッチした要素からIntegerを返す
	 * 
	 * @param matcher
	 * @param count
	 */
	protected static Long getLong(Matcher matcher, int count) {
		if (matcher.groupCount() < count) {
			return null;
		}
		String group = matcher.group(count);
		try {
			Long size = Long.valueOf(group);
			return size;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * JDBCでVARCHARとして扱う
	 * 
	 */
	public T setAsVarcharType() {
		throw new UnsupportedOperationException(this.getClass().getName()
				+ "#setAsVarcharType() does not support.");
	}

	/**
	 * JDBCでBINARYとして扱う
	 * 
	 */
	public T setAsBinaryType() {
		throw new UnsupportedOperationException(this.getClass().getName()
				+ "#setAsBinaryType() does not support.");
	}

	public boolean isDeprecated() {
		return deprecatedSurrogateType != null;
	}

	/**
	 * 非推奨型として代替型の取得
	 * 
	 */
	public DbDataType<?> getDeprecatedSurrogateType() {
		return deprecatedSurrogateType;
	}

	/**
	 * 非推奨型として代替型を設定します
	 * 
	 * @param surrogateType
	 */
	public DbDataType<?> setDeprecated(DbDataType<?> surrogateType) {
		if (getParent() == null) {
			throw new NullPointerException(this.getClass().getName()
					+ "#getDbTypeCollection() is null.");
		}
		Map<DataType, DataType> map = getParent().getSurrogateMap();
		for (Map.Entry<DataType, DataType> entry : map.entrySet()) {
			DataType key = entry.getKey();
			DataType val = entry.getValue();
			if (this.getDataType().equals(val)) {
				map.put(key, this.getDataType().getSurrogate());
			}
		}
		this.deprecatedSurrogateType = surrogateType;
		return this.instance();
	}

	/**
	 * 値セットを含む型の作成フォーマット
	 * 
	 * @param start
	 * @param end
	 */
	public static String getCreateValueSetFormat(String start, String end) {
		return start + VALUESET_REPLACE + end;
	}

	/**
	 * 桁、精度のフォーマットの追加
	 * 
	 * @param dataTypeName
	 */
	public T addPrecisionScaleFormat(String dataTypeName) {
		throw new UnsupportedOperationException(this.getClass().getName()
				+ "#addPrecisionScaleFormat(String dataTypeName) does not support.");
	}

	/**
	 * 桁のフォーマットの追加
	 * 
	 * @param dataTypeName
	 */
	public T addPrecisionFormat(String dataTypeName) {
		throw new UnsupportedOperationException(this.getClass().getName()
				+ "#addPrecisionFormat(String dataTypeName) does not support.");
	}

	/**
	 * 桁のフォーマットの追加
	 * 
	 * @param dataTypeName
	 */
	public T addScaleFormat(String dataTypeName) {
		throw new UnsupportedOperationException(this.getClass().getName()
				+ "#addScaleFormat(String dataTypeName) does not support.");
	}

	/**
	 * サイズフォーマットの追加
	 * 
	 * @param dataTypeName
	 */
	public T addSizeFormat(String dataTypeName) {
		throw new UnsupportedOperationException(this.getClass().getName()
				+ "#addSizeFormat(String dataTypeName) does not support.");
	}

	protected DbDataTypeCollection getParent() {
		if (this.parent==null) {
			this.parent=new DbDataTypeCollection();
		}
		return parent;
	}

	protected T setParent(DbDataTypeCollection parent) {
		this.parent = parent;
		return instance();
	}

	@SuppressWarnings("unchecked")
	protected T instance() {
		return (T) (this);
	}

	public boolean isSupportsArray() {
		return supportsArray;
	}

	public T setSupportsArray(boolean supportsArray) {
		this.supportsArray = supportsArray;
		return instance();
	}

	public String getCharset() {
		return charset;
	}

	public T setCharset(String charset) {
		this.charset = charset;
		return instance();
	}

	public boolean isDbSpecificType() {
		return dbSpecificType;
	}

	public T setDbSpecificType(boolean dbSpecificType) {
		this.dbSpecificType = dbSpecificType;
		return instance();
	}

	public DbDataType<?> getSurrogateType() {
		return surrogateType;
	}

	/**
	 * 代替型の設定
	 * 
	 * @param surrogateType
	 */
	public T setSurrogateType(DbDataType<?> surrogateType) {
		this.surrogateType = surrogateType;
		return instance();
	}

	/**
	 * @return the jdbcType
	 */
	public java.sql.JDBCType getJdbcType() {
		return jdbcType;
	}

	/**
	 * @param jdbcType
	 *            the jdbcType to set
	 */
	public T setJdbcType(java.sql.JDBCType jdbcType) {
		this.jdbcType = jdbcType;
		return instance();
	}

	/**
	 * 型定義の正規表現の追加
	 * 
	 * @param formats
	 */
	public T addFormats(String... formats) {
		for (String format : formats) {
			Pattern pattern = Pattern.compile(format.replace(" ", "\\s+"),
					Pattern.CASE_INSENSITIVE);
			if (!formatList.contains(pattern)) {
				formatList.add(pattern);
			}
		}
		return instance();
	}

	/**
	 * @return the systemInternalType
	 */
	public boolean isSystemInternalType() {
		return systemInternalType;
	}

	/**
	 * @param systemInternalType
	 *            the systemInternalType to set
	 */
	public T setSystemInternalType(boolean systemInternalType) {
		this.systemInternalType = systemInternalType;
		return instance();
	}

	/**
	 * 配列型のフォーマットリスト
	 * 
	 */
	private List<Pattern> getArrayFormatList() {
		if (!this.isSupportsArray()) {
			return arrayFormatList;
		}
		if (formatList.size() == arrayFormatList.size()) {
			return arrayFormatList;
		}
		for (Pattern pattern : formatList) {
			Pattern arrayPattern = Pattern.compile(this.getParent().getArrayPatternGenerator().apply(pattern.pattern()), Pattern.CASE_INSENSITIVE);
			arrayFormatList.add(arrayPattern);
		}
		return arrayFormatList;
	}

	/**
	 * 型定義の正規表現の設定
	 * 
	 * @param formats
	 */
	public T setFormats(String... formats) {
		formatList.clear();
		return addFormats(formats);
	}

	protected Column parse(Matcher matcher) {
		Column column = new Column();
		parseAndSet(matcher, column);
		return column;
	}

	private Map<String, Matcher> arrayPatternCache=CommonUtils.map();

	private Set<String> arrayNoMatchPatternCache=CommonUtils.set();

	private Map<String, Matcher> patternCache=CommonUtils.map();

	private Set<String> noMatchPatternCache=CommonUtils.set();

	/**
	 * 指定したDB製品固有の型定義からColumnに値を設定します
	 * 
	 * @param productDataType
	 * @param column
	 */
	public boolean parseAndSet(String productDataType,
			DataTypeLengthProperties<?> column) {
		Matcher matcher=getArrayPatternMatcher(productDataType);
		if (matcher!=null) {
			parseAndSet(matcher, column);
			column.setDataType(this.getDataType());
			String dataTypeName=column.getDataTypeName();
			if (!CommonUtils.eq(dataTypeName, this.getTypeName())) {
				SchemaUtils.setDataTypeNameInternal(this.getTypeName(), column);
			}
			if (column instanceof ArrayDimensionProperties) {
				this.getParent().getArrayDimensionHandler().accept(matcher, (ArrayDimensionProperties<?>)column);
			}
			return true;
		}
		matcher=getPatternMatcher(productDataType);
		if (matcher!=null) {
			parseAndSet(matcher, column);
			column.setDataType(this.getDataType());
			String dataTypeName=column.getDataTypeName();
			if (!CommonUtils.eq(dataTypeName, this.getTypeName())) {
				SchemaUtils.setDataTypeNameInternal(this.getTypeName(), column);
			}
			return true;
		}
		return false;
	}
	
	public boolean matchLength(DataTypeLengthProperties<?> column) {
		if (column.getLength()!=null) {
			return false;
		}
		return true;
	}

	private Matcher getArrayPatternMatcher(String productDataType){
		Matcher matcher=arrayPatternCache.get(productDataType);
		if (matcher!=null){
			return matcher;
		}
		if (arrayNoMatchPatternCache.contains(productDataType)){
			return null;
		}
		for (Pattern pattern : getArrayFormatList()) {
			matcher = pattern.matcher(productDataType);
			if (matcher.matches()) {
				arrayPatternCache.put(productDataType, matcher);
				return matcher;
			}
		}
		arrayNoMatchPatternCache.add(productDataType);
		return null;
	}
	
	private Matcher getPatternMatcher(String productDataType){
		Matcher matcher=patternCache.get(productDataType);
		if (matcher!=null){
			return matcher;
		}
		if (noMatchPatternCache.contains(productDataType)){
			return null;
		}
		for (Pattern pattern : formatList) {
			matcher = pattern.matcher(productDataType);
			if (matcher.matches()) {
				patternCache.put(productDataType, matcher);
				return matcher;
			}
		}
		noMatchPatternCache.add(productDataType);
		return null;
	}

	protected Integer getProperNumber(Integer maxNum, Integer defaultNum,
			Integer num) {
		Integer ret = defaultNum != null ? defaultNum.intValue() : 0;
		if (num != null) {
			if (maxNum != null) {
				ret = maxNum.intValue() > num.intValue() ? num.intValue()
						: maxNum.intValue();
			}
		}
		return ret;
	}

	protected Integer getProperNumber(Integer maxNum, Integer defaultNum,
			Number num) {
		return getProperNumber(maxNum, defaultNum, Converters.getDefault()
				.convertObject(num, Integer.class));
	}

	@Override
	public String toString(){
		ToStringBuilder builder=new ToStringBuilder();
		builder.add("typeName", this.getTypeName());
		builder.add("dataType", this.getDataType());
		builder.add("surrogateType", this.getSurrogateType());
		builder.add("createFormat", this.getCreateFormat());
		builder.add("literalPrefix", this.getLiteralPrefix());
		builder.add("literalSuffix", this.getLiteralSuffix());
		buildToString(builder);
		return builder.toString();
	}

	protected void buildToString(ToStringBuilder builder){
	}

}
