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

package com.sqlapp.data.converter;

import static com.sqlapp.util.CommonUtils.isEmpty;

import java.util.regex.Pattern;

import org.geolatte.geom.ByteBuffer;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.codec.Wkt;
import org.geolatte.geom.jts.JTS;

/**
 * GeometryType Converter
 * <p>
 * 文字列との変換形式は次のとおり。
 * </p>
 * <ul>
 * <li>出力: PostGIS EWKT ({@link Wkt.Dialect#POSTGIS_EWKT_1})。SRID は "SRID=n;" として含め、Z/M は PostGIS の表記
 * ("POINT(x y z)"、"POINTM(x y m)"、"POINT(x y z m)")で出力する。SRID 0 は SRID なしとして扱う。</li>
 * <li>入力: WKT / EWKT(ISO の "POINT Z (...)" 表記を含む)と、16進文字列の EWKB(PostgreSQL の COPY が出力する形式)。
 * 構文が不正な入力や末尾に余分なデータがある入力は拒否する。</li>
 * </ul>
 * 
 * @author SATOH
 *
 */
public class GeometryConverter extends AbstractConverter<org.geolatte.geom.Geometry<?>> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7894583139837528990L;

	@Override
	public org.geolatte.geom.Geometry<?> convertObject(final Object value) {
		if (isSupplier(value)) {
			return convertObject(getSupplierValue(value));
		} else if (isEmpty(value)) {
			return getDefaultValue();
		} else if (value instanceof org.geolatte.geom.Geometry) {
			return (org.geolatte.geom.Geometry<?>) value;
		} else if (value instanceof String) {
			return parse((String) value);
		} else if (value instanceof com.sqlapp.data.geometry.AbstractGeometry geometry) {
			return SpatialGeometryConversions.toSpatial(geometry);
		} else if (value instanceof org.locationtech.jts.geom.Geometry) {
			return JTS.from((org.locationtech.jts.geom.Geometry) value);
		}
		throw new IllegalArgumentException("value=" + value);
	}

	@Override
	public String format(final org.geolatte.geom.Geometry<?> value) {
		if (value == null) {
			return null;
		}
		return Wkt.toWkt(value, Wkt.Dialect.POSTGIS_EWKT_1);
	}

	private static final Pattern HEX = Pattern.compile("[0-9A-Fa-f]+");

	/**
	 * WKT / EWKT または16進文字列の EWKB を解析します。
	 * 
	 * @throws IllegalArgumentException
	 *             構文が不正な場合
	 */
	private static org.geolatte.geom.Geometry<?> parse(final String value) {
		final String text = value.trim();
		if (HEX.matcher(text).matches()) {
			return parseHexEwkb(text);
		}
		WktSyntaxValidator.validate(text);
		try {
			return Wkt.fromWkt(text, Wkt.Dialect.POSTGIS_EWKT_1);
		} catch (final RuntimeException e) {
			throw new IllegalArgumentException("Invalid WKT: \"" + value + "\"", e);
		}
	}

	private static org.geolatte.geom.Geometry<?> parseHexEwkb(final String hex) {
		if (hex.length() % 2 != 0) {
			throw new IllegalArgumentException("Invalid EWKB: odd number of hex digits. \"" + hex + "\"");
		}
		if (!hex.startsWith("00") && !hex.startsWith("01")) {
			throw new IllegalArgumentException("Invalid EWKB: unknown byte order. \"" + hex + "\"");
		}
		final org.geolatte.geom.Geometry<?> geometry;
		try {
			geometry = Wkb.fromWkb(ByteBuffer.from(hex), Wkb.Dialect.POSTGIS_EWKB_1);
		} catch (final RuntimeException e) {
			throw new IllegalArgumentException("Invalid EWKB: \"" + hex + "\"", e);
		}
		// デコーダは末尾の余分なバイトを無視するため、構造をたどって全バイトを使い切ることを確認する
		final byte[] bytes = ByteBuffer.from(hex).toByteArray();
		final int length;
		try {
			length = wkbLength(bytes, 0);
		} catch (final IndexOutOfBoundsException e) {
			throw new IllegalArgumentException("Invalid EWKB: unexpected end of data. \"" + hex + "\"", e);
		}
		if (length != bytes.length) {
			throw new IllegalArgumentException("Invalid EWKB: expected " + length + " bytes but was " + bytes.length
					+ " bytes. \"" + hex + "\"");
		}
		return geometry;
	}

	/**
	 * offset から始まる WKB / EWKB の図形1つ分の終端位置を返します。
	 */
	private static int wkbLength(final byte[] bytes, final int offset) {
		int pos = offset;
		final boolean littleEndian;
		if (bytes[pos] == 1) {
			littleEndian = true;
		} else if (bytes[pos] == 0) {
			littleEndian = false;
		} else {
			throw new IllegalArgumentException("Invalid EWKB: unknown byte order at " + pos);
		}
		pos++;
		final long type = readUInt(bytes, pos, littleEndian);
		pos += 4;
		boolean z = (type & 0x80000000L) != 0;
		boolean m = (type & 0x40000000L) != 0;
		if ((type & 0x20000000L) != 0) {
			pos += 4; // SRID
		}
		long baseType = type & 0x0FFFFFFFL;
		if (baseType >= 1000) {
			// ISO WKB の Z / M / ZM 型番号
			final long dims = baseType / 1000;
			z = z || dims == 1 || dims == 3;
			m = m || dims == 2 || dims == 3;
			baseType = baseType % 1000;
		}
		final int pointSize = 8 * (2 + (z ? 1 : 0) + (m ? 1 : 0));
		switch ((int) baseType) {
		case 1: // Point
			pos += pointSize;
			break;
		case 2: // LineString
			pos = skipPoints(bytes, pos, littleEndian, pointSize);
			break;
		case 3: { // Polygon
			final long rings = readUInt(bytes, pos, littleEndian);
			pos += 4;
			for (long i = 0; i < rings; i++) {
				pos = skipPoints(bytes, pos, littleEndian, pointSize);
			}
			break;
		}
		case 4: // MultiPoint
		case 5: // MultiLineString
		case 6: // MultiPolygon
		case 7: { // GeometryCollection
			final long count = readUInt(bytes, pos, littleEndian);
			pos += 4;
			for (long i = 0; i < count; i++) {
				pos = wkbLength(bytes, pos);
			}
			break;
		}
		default:
			throw new IllegalArgumentException("Invalid EWKB: unsupported geometry type " + baseType);
		}
		if (pos > bytes.length) {
			throw new IndexOutOfBoundsException(pos);
		}
		return pos;
	}

	private static int skipPoints(final byte[] bytes, final int pos, final boolean littleEndian,
			final int pointSize) {
		final long count = readUInt(bytes, pos, littleEndian);
		final long end = pos + 4 + count * pointSize;
		if (end > bytes.length) {
			throw new IndexOutOfBoundsException((int) Math.min(end, Integer.MAX_VALUE));
		}
		return (int) end;
	}

	private static long readUInt(final byte[] bytes, final int pos, final boolean littleEndian) {
		if (pos + 4 > bytes.length) {
			throw new IndexOutOfBoundsException(pos);
		}
		long value = 0;
		for (int i = 0; i < 4; i++) {
			final int b = bytes[pos + (littleEndian ? 3 - i : i)] & 0xff;
			value = (value << 8) | b;
		}
		return value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (!super.equals(this)) {
			return false;
		}
		if (!(obj instanceof GeometryConverter)) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.converter.Converter#copy(java.lang.Object)
	 */
	@Override
	public org.geolatte.geom.Geometry<?> copy(final Object obj) {
		if (obj == null) {
			return null;
		}
		return convertObject(obj);
	}
}
