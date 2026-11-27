/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle;

import java.util.Locale;

/**
 * Oracleの初期化パラメータMAX_STRING_SIZE。VARCHAR2/NVARCHAR2/RAWの最大長を決める。
 * <p>
 * EXTENDEDは12c以降で使用でき、VARCHAR2/NVARCHAR2/RAWの最大長は32767バイトになる。
 * STANDARDはOracleの既定値で、VARCHAR2/NVARCHAR2は4000バイト、RAWは2000バイト。
 */
public enum OracleMaxStringSize {
	/**
	 * VARCHAR2/NVARCHAR2 4000バイト、RAW 2000バイト(Oracleの既定値)
	 */
	STANDARD(4000, 2000),
	/**
	 * VARCHAR2/NVARCHAR2/RAW 32767バイト(12c以降)
	 */
	EXTENDED(32767, 32767);

	/**
	 * Dialectオプション名
	 */
	public static final String OPTION_NAME = "oracle.maxStringSize";

	private final int maxVarcharLength;

	private final int maxRawLength;

	OracleMaxStringSize(final int maxVarcharLength, final int maxRawLength) {
		this.maxVarcharLength = maxVarcharLength;
		this.maxRawLength = maxRawLength;
	}

	/**
	 * @return VARCHAR2/NVARCHAR2の最大長
	 */
	public int getMaxVarcharLength() {
		return maxVarcharLength;
	}

	/**
	 * @return RAWの最大長
	 */
	public int getMaxRawLength() {
		return maxRawLength;
	}

	/**
	 * 設定値を解析します。大文字小文字は区別しません。
	 *
	 * @param value STANDARDまたはEXTENDED
	 * @return 値
	 * @throws IllegalArgumentException 値が不正な場合
	 */
	public static OracleMaxStringSize parse(final String value) {
		if (value != null) {
			final String text = value.trim().toUpperCase(Locale.ROOT);
			for (final OracleMaxStringSize size : values()) {
				if (size.name().equals(text)) {
					return size;
				}
			}
		}
		throw new IllegalArgumentException("Invalid " + OPTION_NAME + ": " + value + ". Specify STANDARD or EXTENDED.");
	}
}
