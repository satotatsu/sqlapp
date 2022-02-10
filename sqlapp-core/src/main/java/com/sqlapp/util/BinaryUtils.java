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

package com.sqlapp.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.UUID;

import static com.sqlapp.util.FileUtils.*;
import static com.sqlapp.util.CommonUtils.*;

/**
 * バイナリ操作ユーティリティ
 * 
 * @author SATOH
 *
 */
public final class BinaryUtils {

	private static final int BUFFER_LENGTH = 1024 * 4;

	/**
	 * コンストラクタ
	 */
	private BinaryUtils() {
	}

	/**
	 * オブジェクトをbyteの配列に変換
	 * 
	 * @param o
	 */
	public static byte[] toBinary(Object o) {
		ObjectOutputStream ostream = null;
		ByteArrayOutputStream bstream = new ByteArrayOutputStream(BUFFER_LENGTH);
		try {
			ostream = new ObjectOutputStream(bstream);
			ostream.writeObject(o);
			return bstream.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			close(ostream);
		}
	}

	/**
	 * byteの配列をオブジェクトに変換します
	 * 
	 * @param binary
	 */
	public static Object toObject(byte[] binary) {
		return toObject(new ByteArrayInputStream(binary));
	}

	/**
	 * InputStreamをオブジェクトに変換します
	 * 
	 * @param stream
	 */
	public static Object toObject(InputStream stream) {
		ObjectInputStream ostream = null;
		try {
			ostream = new ObjectInputStream(stream);
			return ostream.readObject();
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			close(ostream);
		}
	}

	/**
	 * BASE64のデコード
	 * 
	 * @param value
	 */
	public static byte[] decodeBase64(String value) {
		return Base64.getMimeDecoder().decode(value);
	}

	/**
	 * BASE64のデコード
	 * 
	 * @param value
	 */
	public static byte[] decodeBase64(byte[] value) {
		return Base64.getMimeDecoder().decode(value);
	}

	/**
	 * BASE64のエンコード
	 * 
	 * @param value
	 */
	public static String encodeBase64(byte[] value) {
		return Base64.getMimeEncoder().encodeToString(value);
	}

	/**
	 * UUIDのバイト配列(BIG_ENDIAN)への変換
	 * 
	 * @param key
	 *            変換するUUID
	 */
	public static byte[] toBinary(UUID... key) {
		return toBinary(ByteOrder.BIG_ENDIAN, key);
	}

	/**
	 * UUIDのバイト配列(BIG_ENDIAN)への変換
	 * 
	 * @param key
	 *            変換するUUID
	 */
	public static byte[] toBinary(UUID key) {
		return toBinary(ByteOrder.BIG_ENDIAN, key);
	}

	/**
	 * InputStreamをバイト配列へ変換します。
	 * 
	 * @param is
	 *            変換するInputStream
	 */
	public static byte[] toBinary(InputStream is) {
		byte[] bytes = new byte[2048];
		int len = 0;
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			while ((len = is.read(bytes)) != -1) {
				bos.write(bytes, 0, len);
			}
			return bos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * UUIDのバイト配列への変換
	 * 
	 * @param uuid
	 *            変換するUUID
	 * @param order
	 *            バイトオーダー
	 */
	public static byte[] toBinary(ByteOrder order, UUID uuid) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(INT128_SIZE);
		keyBuffer.order(order);
		keyBuffer.putLong(uuid.getMostSignificantBits());
		keyBuffer.putLong(uuid.getLeastSignificantBits());
		return keyBuffer.array();
	}

	/**
	 * UUIDをバイト配列への変換します
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param key
	 *            変換するUUID
	 */
	public static byte[] toBinary(ByteOrder order, final UUID... key) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(INT128_SIZE * key.length);
		keyBuffer.order(order);
		for (UUID uuid : key) {
			keyBuffer.putLong(uuid.getMostSignificantBits());
			keyBuffer.putLong(uuid.getLeastSignificantBits());
		}
		return keyBuffer.array();
	}

	/**
	 * バイト配列(BIG_ENDIAN)のUUID配列への変換
	 * 
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static UUID[] toUUIDArray(final byte[] bytes) {
		return toUUIDArray(ByteOrder.BIG_ENDIAN, bytes);
	}

	/**
	 * バイト配列のUUID配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static UUID[] toUUIDArray(ByteOrder order, byte[] bytes) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(bytes.length);
		keyBuffer.order(order);
		keyBuffer.put(bytes);
		int size = keyBuffer.limit() / INT128_SIZE;
		UUID[] result = new UUID[size];
		for (int i = 0; i < size; i++) {
			UUID uuid = new UUID(keyBuffer.getLong(i * INT128_SIZE), keyBuffer.getLong((i) * INT128_SIZE + INT64_SIZE));
			result[i] = uuid;
		}
		return result;
	}

	/**
	 * バイト配列のUUIDへの変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static UUID toUUID(ByteOrder order, byte[] bytes) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(bytes.length);
		keyBuffer.order(order);
		keyBuffer.put(bytes);
		UUID result = new UUID(keyBuffer.getLong(0), keyBuffer.getLong(8));
		return result;
	}

	/**
	 * バイト配列のUUIDへの変換
	 * 
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static UUID toUUID(byte[] bytes) {
		return toUUID(ByteOrder.BIG_ENDIAN, bytes);
	}

	/**
	 * バイト配列(BIG_ENDIAN)への変換
	 * 
	 * @param key
	 *            変換するshort
	 */
	public static byte[] toBinary(short key) {
		return toBinary(ByteOrder.BIG_ENDIAN, key);
	}

	/**
	 * バイト配列(BIG_ENDIAN)への変換
	 * 
	 * @param key
	 *            変換するshort
	 */
	public static byte[] toBinary(short... key) {
		return toBinary(ByteOrder.BIG_ENDIAN, key);
	}

	/**
	 * Shortのバイト配列(BIG_ENDIAN)への変換
	 * 
	 * @param key
	 *            変換するShort
	 */
	public static byte[] toBinary(Short... key) {
		return toBinary(ByteOrder.BIG_ENDIAN, key);
	}

	/**
	 * shortのバイト配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param key
	 *            変換するshort
	 */
	public static byte[] toBinary(ByteOrder order, short key) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(INT16_SIZE);
		keyBuffer.order(order);
		keyBuffer.putShort(key);
		return keyBuffer.array();
	}

	/**
	 * shortのバイト配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param key
	 *            変換するshort
	 */
	public static byte[] toBinary(ByteOrder order, short... key) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(INT16_SIZE * key.length);
		keyBuffer.order(order);
		for (short val : key) {
			keyBuffer.putShort(val);
		}
		return keyBuffer.array();
	}

	/**
	 * Shortのバイト配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param key
	 *            変換するlong
	 */
	public static byte[] toBinary(ByteOrder order, Short... key) {
		return toBinary(order, CommonUtils.toShortArray(key));
	}

	/**
	 * バイト配列のshortへの変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static short toShort(ByteOrder order, byte[] bytes) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(bytes.length);
		keyBuffer.order(order);
		keyBuffer.put(bytes);
		return keyBuffer.getShort(0);
	}

	/**
	 * バイト配列のshortへの変換
	 * 
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static short toShort(byte[] bytes) {
		return toShort(ByteOrder.BIG_ENDIAN, bytes);
	}

	/**
	 * バイト配列(BIG_ENDIAN)のshort配列への変換
	 * 
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static short[] toShortArray(byte[] bytes) {
		return toShortArray(ByteOrder.BIG_ENDIAN, bytes);
	}

	/**
	 * バイト配列のshort配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static short[] toShortArray(ByteOrder order, byte[] bytes) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(bytes.length);
		keyBuffer.order(order);
		keyBuffer.put(bytes);
		int size = keyBuffer.limit() / INT16_SIZE;
		short[] result = new short[size];
		for (int i = 0; i < size; i++) {
			result[i] = keyBuffer.getShort(i * INT16_SIZE);
		}
		return result;
	}

	/**
	 * int配列(BIG_ENDIAN)への変換
	 * 
	 * @param key
	 *            変換するlong
	 */
	public static byte[] toBinary(int... key) {
		return toBinary(ByteOrder.BIG_ENDIAN, key);
	}

	/**
	 * Integerのバイト配列(BIG_ENDIAN)への変換
	 * 
	 * @param key
	 *            変換するlong
	 */
	public static byte[] toBinary(Integer... key) {
		return toBinary(ByteOrder.BIG_ENDIAN, key);
	}

	/**
	 * intのバイト配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param val
	 *            変換するint
	 */
	public static byte[] toBinary(ByteOrder order, int val) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(INT32_SIZE);
		keyBuffer.order(order);
		keyBuffer.putInt(val);
		return keyBuffer.array();
	}

	/**
	 * intのバイト配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param key
	 *            変換するint
	 */
	public static byte[] toBinary(ByteOrder order, int... key) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(INT32_SIZE * key.length);
		keyBuffer.order(order);
		for (int val : key) {
			keyBuffer.putInt(val);
		}
		return keyBuffer.array();
	}

	/**
	 * Integerのバイト配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param key
	 *            変換するlong
	 */
	public static byte[] toBinary(ByteOrder order, Integer... key) {
		return toBinary(order, CommonUtils.toIntArray(key));
	}

	/**
	 * バイト配列(BIG_ENDIAN)のint配列への変換
	 * 
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static int[] toIntArray(byte[] bytes) {
		return toIntArray(ByteOrder.BIG_ENDIAN, bytes);
	}

	/**
	 * バイト配列のintへの変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static int toInt(ByteOrder order, byte[] bytes) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(bytes.length);
		keyBuffer.order(order);
		keyBuffer.put(bytes);
		return keyBuffer.getInt(0);
	}

	/**
	 * バイト配列のintへの変換
	 * 
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static int toInt(byte[] bytes) {
		return toInt(ByteOrder.BIG_ENDIAN, bytes);
	}

	/**
	 * バイト配列のint配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static int[] toIntArray(ByteOrder order, byte[] bytes) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(bytes.length);
		keyBuffer.order(order);
		keyBuffer.put(bytes);
		int size = keyBuffer.limit() / INT32_SIZE;
		int[] result = new int[size];
		for (int i = 0; i < size; i++) {
			result[i] = keyBuffer.getInt(i * INT32_SIZE);
		}
		return result;
	}

	/**
	 * int配列(BIG_ENDIAN)への変換
	 * 
	 * @param key
	 *            変換するlong
	 */
	public static byte[] toBinary(int key) {
		return toBinary(ByteOrder.BIG_ENDIAN, key);
	}

	/**
	 * long配列(BIG_ENDIAN)への変換
	 * 
	 * @param key
	 *            変換するlong
	 */
	public static byte[] toBinary(long key) {
		return toBinary(ByteOrder.BIG_ENDIAN, key);
	}

	/**
	 * long配列(BIG_ENDIAN)への変換
	 * 
	 * @param key
	 *            変換するlong
	 */
	public static byte[] toBinary(long... key) {
		return toBinary(ByteOrder.BIG_ENDIAN, key);
	}

	/**
	 * long配列(BIG_ENDIAN)への変換
	 * 
	 * @param key
	 *            変換するlong
	 */
	public static byte[] toBinary(Long... key) {
		return toBinary(ByteOrder.BIG_ENDIAN, key);
	}

	/**
	 * longのバイト配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param key
	 *            変換するlong
	 */
	public static byte[] toBinary(ByteOrder order, Long... key) {
		return toBinary(order, CommonUtils.toLongArray(key));
	}

	/**
	 * longのバイト配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param key
	 *            変換するlong
	 */
	public static byte[] toBinary(ByteOrder order, long... key) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(INT64_SIZE * key.length);
		keyBuffer.order(order);
		for (long val : key) {
			keyBuffer.putLong(val);
		}
		return keyBuffer.array();
	}

	/**
	 * longのバイト配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param val
	 *            変換するlong
	 */
	public static byte[] toBinary(ByteOrder order, long val) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(INT64_SIZE);
		keyBuffer.order(order);
		keyBuffer.putLong(val);
		return keyBuffer.array();
	}

	/**
	 * バイト配列(BIG_ENDIAN)のlong配列への変換
	 * 
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static long[] toLongArray(byte[] bytes) {
		return toLongArray(ByteOrder.BIG_ENDIAN, bytes);
	}

	/**
	 * バイト配列のlong配列への変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static long[] toLongArray(ByteOrder order, byte[] bytes) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(bytes.length);
		keyBuffer.order(order);
		keyBuffer.put(bytes);
		int size = keyBuffer.limit() / INT64_SIZE;
		long[] result = new long[size];
		for (int i = 0; i < size; i++) {
			result[i] = keyBuffer.getLong(i * INT64_SIZE);
		}
		return result;
	}

	/**
	 * バイト配列のlongへの変換
	 * 
	 * @param order
	 *            バイトオーダー
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static long toLong(ByteOrder order, byte[] bytes) {
		ByteBuffer keyBuffer = ByteBuffer.allocate(bytes.length);
		keyBuffer.order(order);
		keyBuffer.put(bytes);
		return keyBuffer.getLong(0);
	}

	/**
	 * バイト配列のlongへの変換
	 * 
	 * @param bytes
	 *            変換するバイト配列
	 */
	public static long toLong(byte[] bytes) {
		return toLong(ByteOrder.BIG_ENDIAN, bytes);
	}

	/**
	 * バイト配列を16進数の文字列に変換します。
	 * 
	 * @param bytes
	 *            バイト配列
	 * @return 16進数の文字列
	 */
	public static String toHexString(byte bytes[]) {
		if (bytes == null) {
			return null;
		}
		StringBuilder builder = new StringBuilder(bytes.length * 2);
		for (int index = 0; index < bytes.length; index++) {
			int bt = bytes[index] & 0xff;
			builder.append(Character.forDigit(bt >> 4 & 0xF, 16));
			builder.append(Character.forDigit(bt & 0xF, 16));
			// if (bt < 0x10) {
			// builder.append("0");
			// }
			// builder.append(Integer.toHexString(bt));
		}
		return builder.toString();
	}

	/**
	 * 16進数の文字列をバイト配列に変換します
	 * 
	 * @param hex
	 *            16進数の文字列
	 * @return バイト配列
	 */
	public static byte[] toBinaryFromHex(String hex) {
		byte[] bytes = new byte[hex.length() / 2];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, (i + 1) * 2), 16);
		}
		return bytes;
	}
}