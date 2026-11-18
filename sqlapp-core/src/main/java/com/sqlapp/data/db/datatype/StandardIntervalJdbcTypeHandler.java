/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.datatype;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;

import com.sqlapp.data.schemas.Column;

/** Exact text-based JDBC interval handling; vendor subclasses own binding strategy and limits. */
public class StandardIntervalJdbcTypeHandler implements IntervalTypeHandler, java.io.Serializable {
	private static final long serialVersionUID = 1L;
	private final DataType type;
	private final int minimumPrecision;
	private final int maximumPrecision;
	private final int maximumScale;
	private final int maximumArrayDimensions;

	public StandardIntervalJdbcTypeHandler(DataType type, int minimumPrecision, int maximumPrecision) {
		this(type, minimumPrecision, maximumPrecision, 9);
	}

	public StandardIntervalJdbcTypeHandler(DataType type, int minimumPrecision, int maximumPrecision, int maximumScale) {
		this(type, minimumPrecision, maximumPrecision, maximumScale, 32);
	}

	public StandardIntervalJdbcTypeHandler(DataType type, int minimumPrecision, int maximumPrecision, int maximumScale,
			int maximumArrayDimensions) {
		this.type = type;
		this.minimumPrecision = minimumPrecision;
		this.maximumPrecision = maximumPrecision;
		this.maximumScale = maximumScale;
		this.maximumArrayDimensions = maximumArrayDimensions;
		codec(null);
	}

	public final StandardIntervalCodec codec(Column column) {
		if (column != null && (column.getArrayDimension() > 0 || column.getDataType() != type))
			throw new IllegalArgumentException("Expected scalar " + type + " column");
		int precision = column == null || column.getLength() == null ? 2 : Math.toIntExact(column.getLength());
		int scale = column == null || column.getScale() == null ? Math.min(6, maximumScale) : column.getScale();
		if (scale < 0 || scale > maximumScale)
			throw new IllegalArgumentException("Fractional interval precision must be 0.." + maximumScale);
		if (precision < minimumPrecision || precision > maximumPrecision)
			throw new IllegalArgumentException("Leading interval precision must be " + minimumPrecision + ".." + maximumPrecision);
		return new StandardIntervalCodec(type, precision, scale);
	}

	/** Callers preparing migrations can retain warnings before execution. */
	public final StandardIntervalCodec.Result convert(Object value, Column column) {
		return codec(column).convert(value);
	}

	@Override
	public IntervalConversion prepareInterval(Object value, Column destination) {
		if (destination.getArrayDimension() > 0) {
			validateArrayDimensions(destination.getArrayDimension());
			IntervalArrays.validateBounds(value, destination.getArrayDimension(),
					destination.getSpecifics().get(IntervalArrays.DECLARED_BOUNDS));
			var scalar = destination.clone().setArrayDimension(0);
			var warnings = new java.util.LinkedHashSet<String>();
			var projected = IntervalArrays.map(value, destination.getArrayDimension(), leaf -> {
				var result = prepareInterval(leaf, scalar);
				warnings.addAll(result.warnings());
				return result.value();
			});
			return new IntervalConversion(projected, java.util.List.copyOf(warnings));
		}
		var result = convert(value, destination);
		return new IntervalConversion(result.value(), result.warnings().stream().map(Enum::name).toList());
	}

	protected void validateArrayDimensions(int dimensions) {
		if (dimensions < 1 || dimensions > maximumArrayDimensions)
			throw new IllegalArgumentException("Unsupported interval array dimensions");
	}

	public String literal(Object value, Column column) {
		if (column.getArrayDimension() == 0) return value == null ? "NULL" : codec(column).literal(value);
		validateArrayDimensions(column.getArrayDimension());
		if (value != null) prepareInterval(value, column);
		var scalar = column.clone().setArrayDimension(0);
		var codec = codec(scalar);
		return "CAST(" + IntervalArrays.literal(value, column.getArrayDimension(), codec::literal)
				+ " AS INTERVAL " + codec.qualifier() + " ARRAY".repeat(column.getArrayDimension()) + ")";
	}

	@Override
	public void validateIntervalColumn(Column column) {
		if (column.getArrayDimension() > 0) validateArrayDimensions(column.getArrayDimension());
		codec(column.clone().setArrayDimension(0));
	}

	@Override
	public IntervalPrecision intervalPrecision(Column column) {
		return new IntervalPrecision(column.getLength() == null ? 2 : Math.toIntExact(column.getLength()),
				type.name().endsWith("SECOND") ? column.getScale() == null ? 6 : column.getScale() : 0);
	}

	@Override
	public Column adaptIntervalColumn(Column destination, IntervalPrecision source) {
		return destination.setDataTypeName(null).setLength(source.leading() < 0 ? maximumPrecision
				: Math.max(minimumPrecision, Math.min(source.leading(), maximumPrecision)))
				.setScale(type.name().endsWith("SECOND") ? Math.min(source.fractional(), maximumScale) : null);
	}

	@Override
	public Object getObject(ResultSet rows, int index) throws SQLException {
		if (IntervalArrays.array(rows, index)) return IntervalArrays.read(rows.getArray(index), codec(null)::parse);
		String text = rows.getString(index);
		return text == null ? null : codec(null).parse(text);
	}

	@Override
	public Object getObject(ResultSet rows, String label) throws SQLException {
		if (rows.getMetaData() != null && IntervalArrays.array(rows, rows.findColumn(label)))
			return IntervalArrays.read(rows.getArray(label), codec(null)::parse);
		String text = rows.getString(label);
		return text == null ? null : codec(null).parse(text);
	}

	@Override
	public void setObject(PreparedStatement statement, int index, Object value) throws SQLException {
		setObject(statement, index, value, null);
	}

	@Override
	public void setObject(PreparedStatement statement, int index, Object value, Column column) throws SQLException {
		if (column != null && column.getArrayDimension() > 0) {
			validateArrayDimensions(column.getArrayDimension());
			if (value == null) { statement.setNull(index, Types.ARRAY); return; }
			prepareInterval(value, column);
			var scalar = column.clone().setArrayDimension(0);
			var codec = codec(scalar);
			var text = IntervalArrays.map(value, column.getArrayDimension(), leaf -> codec.convert(leaf).text());
			statement.setObject(index, text, Types.ARRAY);
			return;
		}
		StandardIntervalCodec destination = codec(column);
		if (value == null) bindNull(statement, index);
		else bindText(statement, index, destination.convert(value).text());
	}

	protected void bindText(PreparedStatement statement, int index, String text) throws SQLException {
		statement.setString(index, text);
	}

	protected void bindNull(PreparedStatement statement, int index) throws SQLException {
		statement.setNull(index, Types.OTHER);
	}
}
