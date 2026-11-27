/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana;

import java.util.Locale;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

/**
 * SAP HANA spatial column helpers.
 * <p>
 * SAP HANA has two spatial column types, {@code ST_GEOMETRY} and
 * {@code ST_POINT}. Both map to {@link DataType#GEOMETRY}. Column specifics
 * keep the native type ({@link #TYPE}, only for {@code ST_POINT}) and the
 * spatial reference system ({@link #SRID}), because the shared data type model
 * keeps a single type name per data type.
 */
public final class SapHanaSpatial {
	/** Column specifics key for the SAP HANA spatial reference system ID. */
	public static final String SRID = "hana_srid";
	/** Column specifics key for the native SAP HANA spatial type. */
	public static final String TYPE = "hana_spatial_type";
	public static final String ST_GEOMETRY = "ST_GEOMETRY";
	public static final String ST_POINT = "ST_POINT";

	private SapHanaSpatial() {
	}

	public static boolean isSpatial(final Column column) {
		return column.getDataType() == DataType.GEOMETRY;
	}

	/** Native type name; non-HANA geometry types become ST_GEOMETRY. */
	public static String typeName(final Column column) {
		final String type = column.getSpecifics().get(TYPE);
		if (type == null) {
			return ST_GEOMETRY;
		}
		final String name = type.trim().toUpperCase(Locale.ROOT);
		if (ST_POINT.equals(name) || ST_GEOMETRY.equals(name)) {
			return name;
		}
		throw new IllegalArgumentException(
				"SAP HANA spatial type must be ST_GEOMETRY or ST_POINT: " + column.getName() + "=" + type);
	}

	/** Validates an SRID value; SAP HANA SRS IDs are non-negative integers. */
	public static String normalize(final String value) {
		if (value == null || !value.trim().matches("[0-9]{1,10}") || Long.parseLong(value.trim()) > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(
					"SAP HANA spatial SRID must be an integer between 0 and " + Integer.MAX_VALUE + ": " + value);
		}
		return Integer.toString(Integer.parseInt(value.trim()));
	}

	/** Type with optional SRID, for example {@code ST_POINT(4326)}. */
	public static String typeDefinition(final Column column) {
		final String srid = column.getSpecifics().get(SRID);
		return srid == null ? typeName(column) : typeName(column) + "(" + normalize(srid) + ")";
	}
}
