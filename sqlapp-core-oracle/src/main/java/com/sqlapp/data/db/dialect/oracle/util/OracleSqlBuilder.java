/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.util;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.LengthProperties;
import com.sqlapp.data.db.datatype.PrecisionProperties;
import com.sqlapp.data.db.datatype.ScaleProperties;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.TimesTen;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.FunctionReturning;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * Oracle用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class OracleSqlBuilder extends AbstractSqlBuilder<OracleSqlBuilder> {

	public OracleSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	private static final Set<String> storagePropertyNames=CommonUtils.upperSet();
	
	static{
		for(String arg:OracleUtils.getTableStorageKeys()){
			registerStragePropertyName(arg);
		}
	}
	
	private static void registerStragePropertyName(String name){
		storagePropertyNames.add(name.replace("_", ""));
	}
	
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * LEVEL句を追加します
	 * 
	 */
	public OracleSqlBuilder level() {
		appendElement("LEVEL");
		return instance();
	}
	
	/**
	 * DIRECTORY句を追加します
	 * 
	 */
	public OracleSqlBuilder directory() {
		appendElement("DIRECTORY");
		return instance();
	}

	/**
	 * ALTER COLUMN句を追加します
	 * 
	 */
	@Override
	public OracleSqlBuilder alterColumn() {
		return modify();
	}
	
	/**
	 * STORAGE句を追加します
	 * 
	 */
	public OracleSqlBuilder storage() {
		appendElement("STORAGE");
		return instance();
	}

	/**
	 * VARRAY句を追加します
	 * 
	 */
	public OracleSqlBuilder varray() {
		appendElement("VARRAY");
		return instance();
	}
	
	/**
	 * ROWID句を追加します
	 * 
	 */
	public OracleSqlBuilder rowid() {
		appendElement("ROWID");
		return instance();
	}

	@Override
	protected OracleSqlBuilder typeDefinition(final Column column) {
		if (column.getDataType() != DataType.VECTOR) {
			return super.typeDefinition(column);
		}
		if (getDialect().getDbDataTypes().getDbTypeStrict(DataType.VECTOR) == null) {
			throw new IllegalArgumentException("Oracle VECTOR requires Oracle Database 23ai or later");
		}
		final Integer dimension = column.getVectorDimension();
		if (dimension != null && (dimension.intValue() <= 0 || dimension.intValue() > 65535)) {
			throw new IllegalArgumentException("Oracle VECTOR dimension must be between 1 and 65535: "
					+ column.getName());
		}
		final String elementType = getVectorElementType(column);
		_add("VECTOR");
		if (dimension != null || elementType != null) {
			brackets(() -> {
				if (dimension == null) {
					_add("*");
				} else {
					_add(dimension);
				}
				if (elementType != null) {
					comma()._add(elementType);
				}
			});
		}
		return instance();
	}

	private String getVectorElementType(final Column column) {
		return getVectorElementType(column.getVectorElementDataType(),
				column.getVectorDimension(), column.getName());
	}

	private String getVectorElementType(final DataType elementDataType,
			final Integer dimension, final String objectName) {
		if (elementDataType == null) {
			return null;
		}
		if (elementDataType == DataType.REAL) {
			return "FLOAT32";
		}
		if (elementDataType == DataType.DOUBLE) {
			return "FLOAT64";
		}
		if (elementDataType == DataType.TINYINT) {
			return "INT8";
		}
		if (elementDataType == DataType.BINARY) {
			if (dimension != null && dimension % 8 != 0) {
				throw new IllegalArgumentException("Oracle BINARY VECTOR dimension must be a multiple of 8: "
						+ objectName);
			}
			return "BINARY";
		}
		throw new IllegalArgumentException("Unsupported Oracle VECTOR element data type: "
				+ elementDataType);
	}

	/**
	 * Adds an Oracle VECTOR_DISTANCE expression.
	 *
	 * @param leftExpression left vector SQL expression
	 * @param rightExpression right vector SQL expression
	 * @param distanceType distance metric, or {@code null} for Oracle's default
	 */
	public OracleSqlBuilder vectorDistance(final CharSequence leftExpression,
			final CharSequence rightExpression,
			final VectorDistanceType distanceType) {
		checkVectorSearchSupport();
		checkExpression(leftExpression, "leftExpression");
		checkExpression(rightExpression, "rightExpression");
		_add("VECTOR_DISTANCE")._add("(")._add(leftExpression.toString());
		comma()._add(rightExpression.toString());
		if (distanceType != null) {
			comma()._add(toOracleVectorDistance(distanceType));
		}
		_add(")");
		return instance();
	}

	/**
	 * Adds an Oracle shorthand vector-distance operator expression.
	 */
	public OracleSqlBuilder vectorDistanceOperator(
			final CharSequence leftExpression,
			final CharSequence rightExpression,
			final VectorDistanceType distanceType) {
		checkVectorSearchSupport();
		checkExpression(leftExpression, "leftExpression");
		checkExpression(rightExpression, "rightExpression");
		final String operator;
		if (distanceType == VectorDistanceType.Euclidean) {
			operator = "<->";
		} else if (distanceType == VectorDistanceType.Cosine) {
			operator = "<=>";
		} else if (distanceType == VectorDistanceType.DotProduct
				|| distanceType == VectorDistanceType.InnerProduct) {
			operator = "<#>";
		} else {
			throw new IllegalArgumentException(
					"Oracle has no shorthand operator for vector distance: "
							+ distanceType);
		}
		_add(leftExpression.toString()).space()._add(operator).space()
				._add(rightExpression.toString());
		return instance();
	}

	/**
	 * Adds an Oracle TO_VECTOR conversion expression.
	 */
	public OracleSqlBuilder toVector(final CharSequence expression,
			final Integer dimension, final DataType elementDataType) {
		checkVectorSearchSupport();
		checkExpression(expression, "expression");
		if (dimension != null && (dimension <= 0 || dimension > 65535)) {
			throw new IllegalArgumentException(
					"Oracle VECTOR dimension must be between 1 and 65535: "
							+ dimension);
		}
		final String elementType = getVectorElementType(elementDataType,
				dimension, "TO_VECTOR");
		_add("TO_VECTOR")._add("(")._add(expression.toString());
		if (dimension != null || elementType != null) {
			comma()._add(dimension == null ? "*" : dimension);
		}
		if (elementType != null) {
			comma()._add(elementType);
		}
		_add(")");
		return instance();
	}

	/**
	 * Adds an Oracle FROM_VECTOR conversion expression.
	 */
	public OracleSqlBuilder fromVector(final CharSequence expression) {
		checkVectorSearchSupport();
		checkExpression(expression, "expression");
		_add("FROM_VECTOR")._add("(")._add(expression.toString())._add(")");
		return instance();
	}

	/**
	 * Adds an approximate vector-search row limiting clause.
	 */
	public OracleSqlBuilder fetchApproximateFirst(final int rowCount,
			final Integer targetAccuracy) {
		return fetchApproximateFirst(rowCount, targetAccuracy, null, null);
	}

	/**
	 * Adds an approximate vector-search row limiting clause with HNSW/IVF
	 * search parameters. Accuracy and explicit parameters are alternatives.
	 */
	public OracleSqlBuilder fetchApproximateFirst(final int rowCount,
			final Integer targetAccuracy, final Integer efSearch,
			final Integer neighborPartitionProbes) {
		checkVectorSearchSupport();
		if (rowCount <= 0) {
			throw new IllegalArgumentException("rowCount must be greater than zero");
		}
		if (targetAccuracy != null
				&& (targetAccuracy <= 0 || targetAccuracy > 100)) {
			throw new IllegalArgumentException(
					"targetAccuracy must be between 1 and 100");
		}
		if (efSearch != null && efSearch <= 0) {
			throw new IllegalArgumentException("efSearch must be greater than zero");
		}
		if (neighborPartitionProbes != null && neighborPartitionProbes <= 0) {
			throw new IllegalArgumentException(
					"neighborPartitionProbes must be greater than zero");
		}
		if (targetAccuracy != null
				&& (efSearch != null || neighborPartitionProbes != null)) {
			throw new IllegalArgumentException(
					"targetAccuracy and search parameters are alternatives");
		}
		fetch().space()._add("APPROXIMATE FIRST").space()._add(rowCount)
				.space()._add("ROWS ONLY");
		if (targetAccuracy != null) {
			space()._add("WITH TARGET ACCURACY").space()
					._add(targetAccuracy);
		} else if (efSearch != null || neighborPartitionProbes != null) {
			space()._add("WITH TARGET ACCURACY PARAMETERS").space()._add("(");
			boolean comma = false;
			if (efSearch != null) {
				_add("EFSEARCH").space()._add(efSearch);
				comma = true;
			}
			if (neighborPartitionProbes != null) {
				comma(comma)._add("NEIGHBOR PARTITION PROBES").space()
						._add(neighborPartitionProbes);
			}
			_add(")");
		}
		return instance();
	}

	private void checkVectorSearchSupport() {
		if (getDialect().getDbDataTypes().getDbTypeStrict(DataType.VECTOR) == null) {
			throw new IllegalArgumentException(
					"Oracle AI Vector Search requires Oracle Database 23ai or later");
		}
	}

	private void checkExpression(final CharSequence expression,
			final String argumentName) {
		if (expression == null || expression.toString().isBlank()) {
			throw new IllegalArgumentException(argumentName + " must not be empty");
		}
	}

	private String toOracleVectorDistance(
			final VectorDistanceType distanceType) {
		if (distanceType == VectorDistanceType.EuclideanSquared) {
			return "EUCLIDEAN_SQUARED";
		}
		if (distanceType == VectorDistanceType.DotProduct
				|| distanceType == VectorDistanceType.InnerProduct) {
			return "DOT";
		}
		return distanceType.getSqlValue();
	}
	
	/**
	 * SCN句を追加します
	 * 
	 */
	public OracleSqlBuilder scn() {
		appendElement("SCN");
		return instance();
	}

	/**
	 * FROM Sysdummy句を追加します
	 * 
	 */
	@Override
	public OracleSqlBuilder fromSysDummy() {
		appendElement("FROM DUAL");
		return instance();
	}

	/**
	 * PURGE句を追加します
	 * 
	 */
	public OracleSqlBuilder purge() {
		appendElement("PURGE");
		return instance();
	}
	
	/**
	 * IMMEDIATE句を追加します
	 * 
	 */
	public OracleSqlBuilder immediate() {
		appendElement("IMMEDIATE");
		return instance();
	}
	
	/**
	 * SYNCHRONOUS句を追加します
	 * 
	 */
	public OracleSqlBuilder synchronous() {
		appendElement("SYNCHRONOUS");
		return instance();
	}

	/**
	 * ASYNCHRONOUS句を追加します
	 * 
	 */
	public OracleSqlBuilder asynchronous() {
		appendElement("ASYNCHRONOUS");
		return instance();
	}


	/**
	 * REPEAT句を追加します
	 * 
	 */
	public OracleSqlBuilder repeat() {
		appendElement("REPEAT");
		return instance();
	}

	
	/**
	 * OBJECT句を追加します
	 * 
	 */
	public OracleSqlBuilder object() {
		appendElement("OBJECT");
		return instance();
	}
	
	/**
	 * INCLUDING句を追加します
	 * 
	 */
	public OracleSqlBuilder including() {
		appendElement("INCLUDING");
		return instance();
	}
	
	/**
	 * OF句を追加します
	 * 
	 */
	public OracleSqlBuilder of() {
		appendElement("OF");
		return instance();
	}
	
	/**
	 * VARRAY句を追加します
	 * 
	 */
	public OracleSqlBuilder varray(int size) {
		appendElement("VARRAY");
		this._add("(");
		this._add(size);
		this._add(")");
		return instance();
	}

	/**
	 * 条件を満たす場合にインデックスタイプの追加を行います
	 * 
	 * @param value
	 * @param condition
	 */
	public OracleSqlBuilder _add(IndexType value, boolean condition) {
		if (condition) {
			if (IndexType.BTree==value){
				if (this.getDialect().getClass().equals(TimesTen.class)){
					_add(value.toString());
				}
			} else{
				_add(value.toString());
			}
		}
		return instance();
	}
	
	public OracleSqlBuilder oracleProperty(String key, String value){
		if ("LOGGING".equalsIgnoreCase(key)){
			Boolean bool=Converters.getDefault().convertObject(value, Boolean.class);
			if (bool!=null&&bool.booleanValue()){
				_add("LOGGING");
			} else{
				_add("NOLOGGING");
			}
		}else if ("INITIAL_EXTENT".equalsIgnoreCase(key)){
			key="INITIAL";
			_add(key).space()._add(value);
		}else if ("NEXT_EXTENT".equalsIgnoreCase(key)){
			key="NEXT";
			_add(key).space()._add(value);
		}else if ("FREELIST_GROUPS".equalsIgnoreCase(key)){
			key="FREELIST GROUPS";
			_add(key).space()._add(value);
		} else{
			if (!"BUFFER_POOL".equalsIgnoreCase(key)){
				key=key.replace("_", "");
			}
			_add(key).space()._add(value);
		}
		return instance();
	}
	
	public boolean isStoragePropertyName(String name){
		boolean bool= storagePropertyNames.contains(name.replace("_", "").replace(" ", ""));
		return bool;
	}

	/**
	 * 引数を追加します
	 * 
	 * @param obj
	 */
	@Override
	public OracleSqlBuilder argument(NamedArgument obj) {
		argumentBefore(obj);
		if (obj.getName() != null) {
			this._add(obj.getName());
			this.space();
		}
		argumentTypeDefinition(obj.getDataType(),
				obj.getDataTypeName(), CommonUtils.notZero(obj.getLength(), obj.getOctetLength()),
				obj.getScale());
		if (obj.getDirection() != null
				&& obj.getDirection() != ParameterDirection.Input) {
			this.space()._add(obj.getDirection());
		}
		argumentAfter(obj);
		return instance();
	}
	

	
	/**
	 * カラムの型の定義を追加します
	 * 
	 * @param column
	 *            カラム
	 */
	private void argumentTypeDefinition(DataType type, String dataTypeName,
			Long maxlength, Integer scale) {
		DbDataType<?> dbDataType = null;
		if (maxlength != null) {
			dbDataType = this.getDialect().getDbDataTypes()
					.getDbType(type, maxlength);
		} else {
			dbDataType = this.getDialect().getDbDataTypes().getDbType(type);
		}
		if (type != DataType.OTHER) {
			Long len = null;
			if (dbDataType instanceof LengthProperties) {
				len = ((LengthProperties<?>) dbDataType).getLength(maxlength);
			} else if (dbDataType instanceof PrecisionProperties) {
				len = ((PrecisionProperties<?>) dbDataType).getPrecision(
						maxlength).longValue();
			}
			if (dbDataType instanceof ScaleProperties) {
				scale = ((ScaleProperties<?>) dbDataType).getScale(scale);
			}
			if (dbDataType!=null){
				String def = dbDataType.getColumCreateDefinition(len, scale);
				this._add(removeLength(def));
			} else{
				this._add(removeLength(dataTypeName));
			}
		} else {
			this._add(removeLength(dataTypeName));
		}
	}
	
	private static final Pattern NUMBER_PATTERN=Pattern.compile("[0-9]+");
	
	private String removeLength(String val){
		if (!val.contains("(")){
			return val;
		}
		String[] args=val.split("[(),]");
		StringBuilder builder=new StringBuilder();
		for(String arg:args){
			arg=CommonUtils.trim(arg);
			Matcher matcher=NUMBER_PATTERN.matcher(arg);
			if (matcher.matches()){
				continue;
			}
			builder.append(arg);
			builder.append(' ');
		}
		return builder.substring(0, builder.length()-1);
	}
	
	/**
	 * RETURNINGを追加します
	 * 
	 * @param obj
	 */
	public OracleSqlBuilder _add(FunctionReturning obj) {
		argumentTypeDefinition(obj.getDataType(),
				obj.getDataTypeName(), CommonUtils.notZero(obj.getLength(), obj.getOctetLength()),
				obj.getScale());
		return instance();
	}

	@Override
	protected OracleSqlBuilder autoIncrement(AbstractColumn<?> column) {
		return instance();
	}
	
	@Override
	public OracleSqlBuilder clone(){
		return (OracleSqlBuilder)super.clone();
	}

}
