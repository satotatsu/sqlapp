/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.properties;

import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;

/**
 * Dialectオプション(例: oracle.maxStringSize=STANDARD)を指定できるコマンド。接続のないSQL生成で、 Schema
 * XMLの製品情報や製品名から求めたDialectに適用する。接続のあるコマンドではデータソースに指定する。
 */
public interface DialectOptionsProperty {

	Map<String, String> getDialectOptions();

	void setDialectOptions(Map<String, String> dialectOptions);

	/**
	 * Dialectオプションを適用します。
	 * 
	 * @param dialect 製品情報から求めたDialect
	 * @return オプションを適用したDialect
	 * @throws IllegalArgumentException 対象のDBで使用できないオプションや不正な値の場合
	 */
	default Dialect applyDialectOptions(final Dialect dialect) {
		return DialectResolver.getInstance().applyDialectOptions(dialect, getDialectOptions());
	}
}
