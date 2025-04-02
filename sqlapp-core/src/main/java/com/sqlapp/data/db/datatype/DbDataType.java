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

import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.map;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;

import com.sqlapp.data.converter.Converter;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.ColumnTypeMatcherWrapper;
import com.sqlapp.data.db.datatype.util.DefaultLength;
import com.sqlapp.data.db.datatype.util.DefaultPrecision;
import com.sqlapp.data.db.datatype.util.DefaultScale;
import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher.MatcherColumn;
import com.sqlapp.data.db.datatype.util.SimpleColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.TypeInformation;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.properties.DataTypeLengthProperties;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.ToStringBuilder;

/**
 * DBのカラムの型情報
 * 
 * @author 竜夫
 * 
 * @param <T>
 */
public abstract class DbDataType<T extends DbDataType<? super T>> implements Serializable, Cloneable {
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
	public DbDataType(final String dataTypeName) {
		this.dataTypeName = dataTypeName;
	}

	/** カラムの一致判定 */
	private List<ColumnTypeMatcher> columnTypeMatchers = list();

	/**
	 * 特定のサイズで代替として設定するタイプ
	 */
	protected Map<Long, DataType> sizeSarrogatedType = map();

	@FunctionalInterface
	public static interface DbDataTypeBiConsumer {
		void accept(DbDataType<?> dbDataType, Matcher matcher, DataTypeLengthProperties<?> column);
	}

	/**
	 * プロダクトの型名にマッチした結果を返します
	 * 
	 * @param dataTypeName プロダクトの型名
	 * @return DataType、Length、Scaleなどの情報
	 */
	public Optional<TypeInformation> matchDataTypeName(String dataTypeName) {
		for (ColumnTypeMatcher columnTypeMatcher : columnTypeMatchers) {
			final Optional<TypeInformation> op = columnTypeMatcher.match(dataTypeName);
			if (op.isPresent()) {
				op.get().setDbDataType(this);
				op.get().setDataTypeName(this.getTypeName());
				return op;
			}
		}
		return Optional.empty();
	}

	/**
	 * カラムの一致判定一覧をかえします
	 * 
	 * @return カラムの一致判定一覧
	 */
	public List<ColumnTypeMatcher> getColumnTypeMatchers() {
		return this.columnTypeMatchers;
	}

	/**
	 * カラムの一致判定を追加します
	 * 
	 * @param カラムの一致判定一覧
	 * @return this
	 */
	public T addColumnTypeMatcher(ColumnTypeMatcher columnTypeMatcher) {
		setDefaultValue(columnTypeMatcher);
		this.columnTypeMatchers.add(columnTypeMatcher);
		return instance();
	}

	/**
	 * カラムの一致判定をクリアします
	 * 
	 * @return this
	 */
	public T clearColumnTypeMatchers() {
		this.columnTypeMatchers.clear();
		return instance();
	}

	/**
	 * カラムの一致判定を追加します
	 * 
	 * @param カラムの一致判定一覧
	 * @return this
	 */
	public T convertColumnTypeMatchers(Function<ColumnTypeMatcher, ColumnTypeMatcher> func) {
		if (func != null) {
			for (int i = 0; i < columnTypeMatchers.size(); i++) {
				final ColumnTypeMatcher columnTypeMatcher = columnTypeMatchers.get(i);
				final ColumnTypeMatcher converted = func.apply(columnTypeMatcher);
				setDefaultValueRecursive(converted);
				columnTypeMatchers.set(i, converted);
			}
		}
		return instance();
	}

	protected void setDefaultValueRecursive(ColumnTypeMatcher matcher) {
		setDefaultValue(matcher);
		while (true) {
			if ((matcher instanceof ColumnTypeMatcherWrapper)) {
				ColumnTypeMatcherWrapper wrapper = ColumnTypeMatcherWrapper.class.cast(matcher);
				setDefaultValue(wrapper.getInternal());
				matcher = wrapper.getInternal();
			} else {
				break;
			}
		}
	}

	protected void setDefaultValue(ColumnTypeMatcher matcher) {
		if ((matcher instanceof DefaultLength) && this instanceof LengthProperties) {
			LengthProperties<?> prop = LengthProperties.class.cast(this);
			(DefaultLength.class.cast(matcher)).setDefaultLength(() -> prop.getDefaultLength());
		}
		if ((matcher instanceof DefaultPrecision) && this instanceof PrecisionProperties) {
			PrecisionProperties<?> prop = PrecisionProperties.class.cast(this);
			(DefaultPrecision.class.cast(matcher)).setDefaultPrecision(() -> prop.getDefaultPrecision());
		}
		if ((matcher instanceof DefaultScale) && this instanceof ScaleProperties) {
			ScaleProperties<?> prop = ScaleProperties.class.cast(this);
			(DefaultScale.class.cast(matcher)).setDefaultScale(() -> prop.getDefaultScale());
		}
	}

	/**
	 * 初期化
	 * 
	 * @param typeName
	 */
	protected void initialize(final String dataTypeName) {
		setTypeName(dataTypeName);
		setCreateFormat(dataTypeName);
	}

	/**
	 * 特定のサイズで代替として設定するための型の設定
	 * 
	 * @param size サイズ
	 * @param type
	 */
	public T setSizeSarrogation(final Long size, final DataType type) {
		sizeSarrogatedType.put(size, type);
		return instance();
	}

	public T setSizeSarrogation(final long size, final DataType type) {
		sizeSarrogatedType.put(size, type);
		return instance();
	}

	/**
	 * 特定のサイズで代替として設定する型の取得
	 * 
	 * @param size
	 */
	public DataType getSizeSarrogation(final Long size) {
		return sizeSarrogatedType.get(size);
	}

	/**
	 * 最大値を返します
	 * 
	 * @return 最大値
	 */
	public Object getMaxValue() {
		return null;
	}

	/**
	 * 特定のサイズで代替として設定する型の取得
	 * 
	 * @param size
	 */
	public DataType getSizeSarrogation(final long size) {
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
	private Set<CharacterSemantics> supportCharacterSemantics = CommonUtils.set();

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
	public T setSupportCharacterSemantics(final Set<CharacterSemantics> supportCharacterSemantics) {
		this.supportCharacterSemantics = supportCharacterSemantics;
		return instance();
	}

	/**
	 * @param supportCharacterSemantics the supportCharacterSemantics to set
	 */
	public T setSupportCharacterSemantics(final CharacterSemantics... supportCharacterSemantics) {
		if (supportCharacterSemantics != null) {
			this.supportCharacterSemantics = CommonUtils.linkedSet(supportCharacterSemantics);
		} else {
			this.supportCharacterSemantics = Collections.emptySet();
		}
		return instance();
	}

	/**
	 * @param converter the converter to set
	 */
	public T setConverter(final Converter<?> converter) {
		this.converter = converter;
		return instance();
	}

	/**
	 * @return the sqlTextConverter
	 */
	public Converter<?> getSqlTextConverter() {
		if (this.sqlTextConverter == null) {
			return this.getConverter();
		}
		return sqlTextConverter;
	}

	/**
	 * @param sqlTextConverter the sqlTextConverter to set
	 */
	public T setSqlTextConverter(final Converter<?> sqlTextConverter) {
		this.sqlTextConverter = sqlTextConverter;
		return instance();
	}

	public String getTypeName() {
		return dataTypeName;
	}

	protected T setTypeName(final String dataTypeName) {
		this.dataTypeName = dataTypeName;
		if (this.createFormat == null) {
			this.createFormat = dataTypeName;
		}
		return this.instance();
	}

	public String getCreateFormat() {
		return createFormat;
	}

	public T setCreateFormat(final String createFormat) {
		this.createFormat = createFormat;
		return this.instance();
	}

	public String getCreateParameters() {
		return createParameters;
	}

	public T setCreateParameters(final String createParameters) {
		this.createParameters = createParameters;
		return this.instance();
	}

	public Class<?> getType() {
		return type;
	}

	public T setType(final Class<?> clazz) {
		this.type = clazz;
		return this.instance();
	}

	public boolean isAutoIncrementable() {
		return autoIncrementable;
	}

	public T setAutoIncrementable(final boolean autoIncrementable) {
		this.autoIncrementable = autoIncrementable;
		return this.instance();
	}

	public boolean isCaseSensitive() {
		return caseSensitive;
	}

	public T setCaseSensitive(final boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
		return this.instance();
	}

	public boolean isFixedLength() {
		return fixedLength;
	}

	public T setFixedLength(final boolean fixedLength) {
		this.fixedLength = fixedLength;
		return this.instance();
	}

	public boolean isFixedPrecision() {
		return fixedPrecision;
	}

	public T setFixedPrecision(final boolean fixedPrecision) {
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
	 * @param fixedScale the fixedScale to set
	 */
	public T setFixedScale(final boolean fixedScale) {
		this.fixedScale = fixedScale;
		return this.instance();
	}

	public boolean isNullable() {
		return nullable;
	}

	public T setNullable(final boolean nullable) {
		this.nullable = nullable;
		return this.instance();
	}

	public boolean isSearchable() {
		return searchable;
	}

	public T setSearchable(final boolean searchable) {
		this.searchable = searchable;
		return this.instance();
	}

	public boolean isSearchableWithLike() {
		return searchableWithLike;
	}

	public T setSearchableWithLike(final boolean searchableWithLike) {
		this.searchableWithLike = searchableWithLike;
		return this.instance();
	}

	public boolean isUnsigned() {
		return unsigned;
	}

	public T setUnsigned(final boolean unsigned) {
		this.unsigned = unsigned;
		return this.instance();
	}

	public boolean isConcurrencyType() {
		return concurrencyType;
	}

	public T setConcurrencyType(final boolean concurrencyType) {
		this.concurrencyType = concurrencyType;
		return this.instance();
	}

	public boolean isLiteralSupported() {
		return literalSupported;
	}

	public T setLiteralSupported(final boolean literalSupported) {
		this.literalSupported = literalSupported;
		return this.instance();
	}

	public String getLiteralPrefix() {
		return literalPrefix;
	}

	public T setLiteralPrefix(final String literalPrefix) {
		this.literalPrefix = literalPrefix;
		return this.instance();
	}

	public String withLiteral(final String value) {
		if (value == null) {
			return null;
		}
		if (this.getDataType().isCharacter() || this.getDataType().isBinary() || this.getDataType().isDateTime()) {
			return this.getLiteralPrefix() + value.replace("'", "''") + this.getLiteralSuffix();
		}
		return value;
	}

	public String getLiteralSuffix() {
		return literalSuffix;
	}

	public T setLiteralSuffix(final String literalSuffix) {
		this.literalSuffix = literalSuffix;
		return this.instance();
	}

	public String getDefaultValueLiteral() {
		return defaultValueLiteral;
	}

	/**
	 * 指定リテラルの接頭辞と指定リテラルの接尾辞を同時に設定するメソッド
	 * 
	 * @param literalPrefix 指定リテラルの接頭辞
	 * @param literalSuffix 指定リテラルの接尾辞
	 */
	public T setLiteral(final String literalPrefix, final String literalSuffix) {
		this.literalPrefix = literalPrefix;
		this.literalSuffix = literalSuffix;
		return this.instance();
	}

	public T setDefaultValueLiteral(final String defaultValueLiteral) {
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
	public boolean equals(final Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof DbDataType)) {
			return false;
		}
		final DbDataType<?> objValue = (DbDataType<?>) obj;
		if (!eq(objValue.getCreateFormat(), this.getCreateFormat())) {
			return false;
		}
		if (!eq(objValue.getCreateParameters(), this.getCreateParameters())) {
			return false;
		}
		if (!eq(objValue.getType(), this.getType())) {
			return false;
		}
		if (!eq(objValue.getDefaultValueLiteral(), this.getDefaultValueLiteral())) {
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
		} catch (final CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * CREATE TABLE時のカラムの定義情報
	 * 
	 * @param sizeOrPrecision
	 * @param scale
	 */
	public String getColumCreateDefinition(final Long sizeOrPrecision, final Integer scale) {
		String result = getCreateFormat();
		if (sizeOrPrecision != null) {
			result = result.replace(LENGTH_REPLACE, sizeOrPrecision.toString());
			result = result.replace(PRECISION_REPLACE, sizeOrPrecision.toString());
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
	protected T setDataType(final DataType dataType) {
		this.dataType = dataType;
		this.jdbcType = dataType.getJdbcType();
		this.setTypeName(dataType.getTypeName());
		this.setJdbcTypeHandler(new DefaultJdbcTypeHandler(getDataType()));
		this.setAutoIncrementable(dataType.isAutoIncrementable());
		if (dataType.getDefaultClass() != null) {
			this.setType(dataType.getDefaultClass());
		}
		if (this.getConverter() == null) {
			this.setConverter(Converters.getDefault().getConverter(dataType.getDefaultClass()));
		}
		if (this instanceof LengthProperties) {
			this.setFixedLength(true);
		}
		if (this instanceof PrecisionProperties) {
			this.setFixedPrecision(true);
		}
		if (this instanceof ScaleProperties) {
			this.setFixedScale(true);
		}
		return (T) (this);
	}

	public JdbcTypeHandler getJdbcTypeHandler() {
		return jdbcTypeHandler;
	}

	public T setJdbcTypeHandler(final JdbcTypeHandler jdbcTypeHandler) {
		this.jdbcTypeHandler = jdbcTypeHandler;
		return this.instance();
	}

	/**
	 * Jdbcタイプハンドラーを設定します
	 * 
	 * @param converter
	 */
	public T setJdbcTypeHandler(final Converter<?> converter) {
		this.setJdbcTypeHandler(new DefaultJdbcTypeHandler(this.getDataType().getJdbcType(), converter));
		return this.instance();
	}

	/**
	 * Jdbcタイプハンドラーを設定します
	 * 
	 */
	public T setJdbcTypeHandler(final Converter<?> statementConverter, final Converter<?> resultSetconverter) {
		this.setJdbcTypeHandler(
				new DefaultJdbcTypeHandler(this.getDataType().getJdbcType(), statementConverter, resultSetconverter));
		return this.instance();
	}

	public Long getOctetSize() {
		return octetSize;
	}

	public T setOctetSize(final Long octetSize) {
		this.octetSize = octetSize;
		return this.instance();
	}

	public T setOctetSize(final int octetSize) {
		this.octetSize = Long.valueOf(octetSize);
		return this.instance();
	}

	/**
	 * マッチした要素からIntegerを返す
	 * 
	 * @param matcher
	 * @param count
	 */
	protected static Long getLong(final Matcher matcher, final int count) {
		if (matcher.groupCount() < count) {
			return null;
		}
		final String group = matcher.group(count);
		try {
			final Long size = Long.valueOf(group);
			return size;
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	/**
	 * JDBCでVARCHARとして扱う
	 * 
	 */
	public T setAsVarcharType() {
		throw new UnsupportedOperationException(this.getClass().getName() + "#setAsVarcharType() does not support.");
	}

	/**
	 * JDBCでBINARYとして扱う
	 * 
	 */
	public T setAsBinaryType() {
		throw new UnsupportedOperationException(this.getClass().getName() + "#setAsBinaryType() does not support.");
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
	public DbDataType<?> setDeprecated(final DbDataType<?> surrogateType) {
		if (getParent() == null) {
			throw new NullPointerException(this.getClass().getName() + "#getDbTypeCollection() is null.");
		}
		final Map<DataType, DataType> map = getParent().getSurrogateMap();
		for (final Map.Entry<DataType, DataType> entry : map.entrySet()) {
			final DataType key = entry.getKey();
			final DataType val = entry.getValue();
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
	public static String getCreateValueSetFormat(final String start, final String end) {
		return start + VALUESET_REPLACE + end;
	}

	protected DbDataTypeCollection getParent() {
		if (this.parent == null) {
			this.parent = new DbDataTypeCollection();
		}
		return parent;
	}

	protected T setParent(final DbDataTypeCollection parent) {
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

	public T setSupportsArray(final boolean supportsArray) {
		this.supportsArray = supportsArray;
		return instance();
	}

	public String getCharset() {
		return charset;
	}

	public T setCharset(final String charset) {
		this.charset = charset;
		return instance();
	}

	public boolean isDbSpecificType() {
		return dbSpecificType;
	}

	public T setDbSpecificType(final boolean dbSpecificType) {
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
	public T setSurrogateType(final DbDataType<?> surrogateType) {
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
	 * @param jdbcType the jdbcType to set
	 */
	public T setJdbcType(final java.sql.JDBCType jdbcType) {
		this.jdbcType = jdbcType;
		return instance();
	}

	/**
	 * @return the systemInternalType
	 */
	public boolean isSystemInternalType() {
		return systemInternalType;
	}

	/**
	 * @param systemInternalType the systemInternalType to set
	 */
	public T setSystemInternalType(final boolean systemInternalType) {
		this.systemInternalType = systemInternalType;
		return instance();
	}

	public boolean matchLength(final DataTypeLengthProperties<?> column) {
		if (column.getLength() != null) {
			return false;
		}
		return true;
	}

	protected Integer getProperNumber(final Integer maxNum, final Integer defaultNum, final Integer num) {
		Integer ret = defaultNum != null ? defaultNum.intValue() : 0;
		if (num != null) {
			if (maxNum != null) {
				ret = maxNum.intValue() > num.intValue() ? num.intValue() : maxNum.intValue();
			}
		}
		return ret;
	}

	protected Integer getProperNumber(final Integer maxNum, final Integer defaultNum, final Number num) {
		return getProperNumber(maxNum, defaultNum, Converters.getDefault().convertObject(num, Integer.class));
	}

	/**
	 * カラムの一致判定を追加します
	 * 
	 * @param typeName 型名
	 * @return this
	 */
	public T setColumnTypeMatcher(String... typeName) {
		this.getColumnTypeMatchers().clear();
		this.addColumnTypeMatcher(new SimpleColumnTypeMatcher(typeName));
		return instance();
	}

	/**
	 * カラムの一致判定を設定します
	 * 
	 * @param columnTypeMatcher ColumnTypeMatcher
	 * @return this
	 */
	public T setColumnTypeMatcher(ColumnTypeMatcher columnTypeMatcher) {
		this.getColumnTypeMatchers().clear();
		this.addColumnTypeMatcher(columnTypeMatcher);
		return instance();
	}

	/**
	 * 正規表現カラムの一致判定を設定します
	 * 
	 * @param pattern        正規表現
	 * @param matcherColumns マッチした場合の処理
	 * @return this
	 */
	public T setPetternColumnTypeMatcher(String pattern, MatcherColumn... matcherColumns) {
		this.getColumnTypeMatchers().clear();
		this.addColumnTypeMatcher(new RegexColumnTypeMatcher(pattern));
		return instance();
	}

	/**
	 * 正規表現カラムの一致判定を追加します
	 * 
	 * @param pattern        正規表現
	 * @param matcherColumns マッチした場合の処理
	 * @return this
	 */
	public T addPetternColumnTypeMatcher(String pattern, MatcherColumn... matcherColumns) {
		this.addColumnTypeMatcher(new RegexColumnTypeMatcher(pattern));
		return instance();
	}

	@Override
	public String toString() {
		final ToStringBuilder builder = new ToStringBuilder();
		builder.add("typeName", this.getTypeName());
		builder.add("dataType", this.getDataType());
		builder.add("surrogateType", this.getSurrogateType());
		builder.add("createFormat", this.getCreateFormat());
		builder.add("literalPrefix", this.getLiteralPrefix());
		builder.add("literalSuffix", this.getLiteralSuffix());
		buildToString(builder);
		return builder.toString();
	}

	protected void buildToString(final ToStringBuilder builder) {
	}

}
