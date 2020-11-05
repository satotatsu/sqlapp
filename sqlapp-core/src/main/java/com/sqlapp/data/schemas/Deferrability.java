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
package com.sqlapp.data.schemas;

import java.sql.DatabaseMetaData;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 制約の遅延
 * 
 * @author satoh
 * 
 */
public enum Deferrability implements EnumProperties {
	/**
	 * 制約のチェックを遅延不可
	 */
	NotDeferrable(DatabaseMetaData.importedKeyNotDeferrable,
			"NOT[\\s]*DEFERRABLE", "NOT DEFERRABLE"){
		@Override
		public String getAbbrName() {
			return "NOT DEF";
		}
	}
	/**
	 * トランザクション終了時にチェックを実施
	 */
	, InitiallyDeferred(DatabaseMetaData.importedKeyInitiallyDeferred,
			"INITIALLY[\\s]*DEFERR.*", "INITIALLY DEFERRED"){
		@Override
		public String getAbbrName() {
			return "INIT DEF";
		}
	}
	/**
	 * トランザクションを開始時点およびそれぞれのSQL実行後にチェック
	 */
	, InitiallyImmediate(DatabaseMetaData.importedKeyInitiallyImmediate,
			"INITIALLY[\\s]*IMMEDIATE", "INITIALLY IMMEDIATE"){
		@Override
		public String getAbbrName() {
			return "INIT IMMED";
		}
	};
	private final int value;
	private final String text;
	private final Pattern textPattern;

	private Deferrability(int value, String pattern, String text) {
		this.value = value;
		this.text = text;
		textPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
	}

	public static Deferrability parse(int value) {
		for (Deferrability enm : Deferrability.values()) {
			if (enm.value == value) {
				return enm;
			}
		}
		return null;
	}

	public static Deferrability parse(String text) {
		if (text==null){
			return null;
		}
		for (Deferrability enm : Deferrability.values()) {
			Matcher matcher = enm.textPattern.matcher(text);
			if (matcher.matches()) {
				return enm;
			}
		}
		return null;
	}

	/**
	 * Deferrabilityを取得します
	 * 
	 * @param isDeferrable
	 * @param initiallyDeferred
	 */
	public static Deferrability getDeferrability(boolean isDeferrable,
			boolean initiallyDeferred) {
		if (isDeferrable) {
			if (initiallyDeferred) {
				return Deferrability.InitiallyDeferred;
			} else {
				return Deferrability.InitiallyImmediate;
			}
		} else {
			return Deferrability.NotDeferrable;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return text;
	}

	public String getAbbrName() {
		return this.getDisplayName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.EnumProperties#getDisplayName(java.util.Locale)
	 */
	@Override
	public String getDisplayName(Locale locale) {
		return text;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getSqlValue()
	 */
	@Override
	public String getSqlValue() {
		return text;
	}
}
