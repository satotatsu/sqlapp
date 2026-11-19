/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.postgresql.core.BaseConnection;
import org.postgresql.core.Oid;
import org.postgresql.jdbc.PreferQueryMode;
import org.postgresql.util.PGBinaryObject;
import org.postgresql.util.PGobject;
import com.sqlapp.data.db.datatype.IntervalArrays;
import com.sqlapp.data.interval.IntervalValue;

/** PostgreSQL's integer-datetime interval wire representation; no floating point arithmetic. */
public final class PostgresIntervalBinary {
	private static final BigInteger MILLION = BigInteger.valueOf(1_000_000);
	private PostgresIntervalBinary() {}
	public static boolean binary(java.sql.ResultSet rows, int column) throws SQLException {
		return rows.getMetaData() instanceof org.postgresql.jdbc.PgResultSetMetaData metadata && metadata.getFormat(column) == 1;
	}
	public static com.sqlapp.data.db.datatype.JdbcReadScope prepareRead(PreparedStatement statement) throws SQLException {
		var executor = statement.getConnection().unwrap(BaseConnection.class).getQueryExecutor();
		if (!executor.getIntegerDateTimes()) return com.sqlapp.data.db.datatype.JdbcReadScope.NONE;
		if (executor.getPreferQueryMode() == PreferQueryMode.SIMPLE)
			throw new SQLException("Exact PostgreSQL interval reads require extended query mode");
		statement.unwrap(org.postgresql.PGStatement.class).setPrepareThreshold(-1);
		boolean scalar = executor.useBinaryForReceive(Oid.INTERVAL);
		boolean array = executor.useBinaryForReceive(Oid.INTERVAL_ARRAY);
		if (!scalar) executor.addBinaryReceiveOid(Oid.INTERVAL);
		if (!array) executor.addBinaryReceiveOid(Oid.INTERVAL_ARRAY);
		return () -> {
			if (!scalar) executor.removeBinaryReceiveOid(Oid.INTERVAL);
			if (!array) executor.removeBinaryReceiveOid(Oid.INTERVAL_ARRAY);
		};
	}
	public static IntervalValue readScalar(byte[] bytes) {
		if (bytes.length != 16) throw new IllegalArgumentException("Invalid binary interval length");
		return read(ByteBuffer.wrap(bytes));
	}
	private static IntervalValue read(ByteBuffer buffer) {
		long micros = buffer.getLong(); int days = buffer.getInt(), months = buffer.getInt();
		return PostgresIntervalCodec.validate(new IntervalValue(months, days,
				java.time.Duration.ofSeconds(Math.floorDiv(micros, 1_000_000), Math.floorMod(micros, 1_000_000) * 1000)));
	}
	public static Object readArray(byte[] bytes) {
		try {
			var buffer = ByteBuffer.wrap(bytes);
			int dimensions = buffer.getInt(), nulls = buffer.getInt(), oid = buffer.getInt();
			if (dimensions < 0 || dimensions > 6 || nulls < 0 || nulls > 1 || oid != Oid.INTERVAL)
				throw new IllegalArgumentException("Invalid binary interval array header");
			int[] shape = new int[dimensions]; long elements = dimensions == 0 ? 0 : 1;
			for (int i = 0; i < dimensions; i++) {
				shape[i] = buffer.getInt();
				if (buffer.getInt() != 1) throw new IllegalArgumentException("Non-default interval array lower bounds cannot be preserved");
				if (shape[i] <= 0) throw new IllegalArgumentException("Invalid binary interval array dimension");
				elements = Math.multiplyExact(elements, shape[i]);
			}
			if (elements > buffer.remaining() / 4) throw new IllegalArgumentException("Truncated binary interval array");
			Object result = dimensions == 0 ? new Object[0] : readArray(buffer, shape, 0);
			if (buffer.hasRemaining()) throw new IllegalArgumentException("Trailing binary interval array bytes");
			return result;
		} catch (java.nio.BufferUnderflowException failure) { throw new IllegalArgumentException("Truncated binary interval array", failure); }
	}
	private static Object[] readArray(ByteBuffer buffer, int[] shape, int depth) {
		var result = new Object[shape[depth]];
		for (int i = 0; i < result.length; i++) {
			if (depth + 1 < shape.length) result[i] = readArray(buffer, shape, depth + 1);
			else {
				int length = buffer.getInt();
				if (length != -1 && length != 16) throw new IllegalArgumentException("Invalid binary interval element length");
				result[i] = length == -1 ? null : read(buffer);
			}
		}
		return result;
	}
	public static long microseconds(IntervalValue value) {
		PostgresIntervalCodec.validate(value);
		return BigInteger.valueOf(value.timePart().getSeconds()).multiply(MILLION)
				.add(BigInteger.valueOf(value.timePart().getNano() / 1000)).longValueExact();
	}
	public static boolean required(Object input) {
		if (input == null) return false;
		if (input.getClass().isArray()) {
			for (int i = 0; i < java.lang.reflect.Array.getLength(input); i++)
				if (required(java.lang.reflect.Array.get(input, i))) return true;
			return false;
		}
		var value = PostgresIntervalCodec.decode(input);
		return BigInteger.valueOf(microseconds(value)).abs().divide(BigInteger.valueOf(3_600_000_000L))
				.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0;
	}
	private static void write(ByteBuffer buffer, Object input) {
		var value = PostgresIntervalCodec.decode(input);
		buffer.putLong(microseconds(value)).putInt(Math.toIntExact(value.days())).putInt(Math.toIntExact(value.months()));
	}
	public static byte[] scalar(Object input) {
		var buffer = ByteBuffer.allocate(16); write(buffer, input); return buffer.array();
	}
	public static byte[] array(Object input) {
		int dimensions = IntervalArrays.dimensions(input);
		if (dimensions < 1 || dimensions > 6) throw new IllegalArgumentException("Interval arrays require one to six dimensions");
		int[] shape = IntervalArrays.rectangular(input, dimensions);
		int count = 1;
		for (int size : shape) count = Math.multiplyExact(count, size);
		if (count == 0) return ByteBuffer.allocate(12).putInt(0).putInt(0).putInt(Oid.INTERVAL).array();
		var leaves = new java.util.ArrayList<Object>();
		flatten(input, dimensions, leaves);
		int bytes = Math.addExact(12, Math.multiplyExact(dimensions, 8));
		for (Object leaf : leaves) bytes = Math.addExact(bytes, leaf == null ? 4 : 20);
		var buffer = ByteBuffer.allocate(bytes).putInt(dimensions)
				.putInt(leaves.contains(null) ? 1 : 0).putInt(Oid.INTERVAL);
		for (int size : shape) buffer.putInt(size).putInt(1);
		for (Object leaf : leaves) {
			buffer.putInt(leaf == null ? -1 : 16);
			if (leaf != null) write(buffer, leaf);
		}
		return buffer.array();
	}
	private static void flatten(Object value, int depth, java.util.List<Object> result) {
		if (depth == 0) { result.add(value); return; }
		for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++)
			flatten(java.lang.reflect.Array.get(value, i), depth - 1, result);
	}
	public static void bind(PreparedStatement statement, int index, Object input, boolean array) throws SQLException {
		var connection = statement.getConnection().unwrap(BaseConnection.class);
		var executor = connection.getQueryExecutor();
		if (executor.getPreferQueryMode() == PreferQueryMode.SIMPLE)
			throw new SQLException("Large interval binary binding requires PostgreSQL extended query mode");
		if (!executor.getIntegerDateTimes()) throw new SQLException("Interval binary binding requires integer_datetimes");
		int oid = array ? Oid.INTERVAL_ARRAY : Oid.INTERVAL;
		var encoded = new Value(array ? "_interval" : "interval", array ? array(input) : scalar(input));
		synchronized (executor) {
			boolean enabled = executor.useBinaryForSend(oid);
			try {
				if (!enabled) executor.addBinarySendOid(oid);
				statement.setObject(index, encoded);
			} finally {
				if (!enabled) executor.removeBinarySendOid(oid);
			}
		}
	}
	private static final class Value extends PGobject implements PGBinaryObject {
		private static final long serialVersionUID = 1L;
		private final byte[] bytes;
		Value(String type, byte[] bytes) { setType(type); this.bytes = bytes; }
		@Override public int lengthInBytes() { return bytes.length; }
		@Override public void toBytes(byte[] target, int offset) { System.arraycopy(bytes, 0, target, offset, bytes.length); }
		@Override public void setByteValue(byte[] source, int offset) throws SQLException { throw new SQLException("Write-only interval binary parameter"); }
	}
}
