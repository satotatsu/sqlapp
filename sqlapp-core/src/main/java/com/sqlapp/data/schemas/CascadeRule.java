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
 * 外部キー制約での動作
 * 
 * @author satoh
 * 
 */
public enum CascadeRule implements EnumProperties {
	/**
	 * 関連行を削除または更新する。（既定）
	 */
	Cascade(DatabaseMetaData.importedKeyCascade, "CASCADE", "c.*"){
		@Override
		public String getAbbrName() {
			return "CASC";
		}
	}
	/**
	 * 関連行で何もアクションが実行しない
	 */
	, None(DatabaseMetaData.importedKeyNoAction, "NONE", "no.*")
	/**
	 * 関連行の値を DefaultValue プロパティに格納されている値に設定
	 */
	, SetDefault(DatabaseMetaData.importedKeySetDefault, "SET DEFAULT",
			".*default.*"){
		@Override
		public String getAbbrName() {
			return "DEFAULT";
		}
	}
	/**
	 * 関連行の値を DBNull に設定
	 */
	, SetNull(DatabaseMetaData.importedKeySetNull, "SET NULL", ".*null.*"){
		@Override
		public String getAbbrName() {
			return "NULL";
		}
	}
	/**
	 * 主キーを削除させない
	 */
	, Restrict(DatabaseMetaData.importedKeyRestrict, "RESTRICT", "r.*"){
		@Override
		public String getAbbrName() {
			return "RESTRICT";
		}
		@Override
		public boolean isRestrict(){
			return true;
		}
	};
	private final int value;
	private final Pattern pattern;
	private final String text;

	private CascadeRule(int value, String text, String patternText) {
		this.value = value;
		this.text = text;
		pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
	}

	public boolean isRestrict(){
		return false;
	}
	
	/**
	 * JDBC以下の値をenumに変換します。 <code>DatabaseMetaData.importedKeyCascade</code>
	 * <code>DatabaseMetaData.importedKeyNoAction</code>
	 * <code>DatabaseMetaData.importedKeySetDefault</code>
	 * <code>DatabaseMetaData.importedKeySetNull</code>
	 * <code>DatabaseMetaData.importedKeyRestrict</code>
	 * 
	 * @param value
	 */
	public static CascadeRule parse(int value) {
		for (CascadeRule rule : CascadeRule.values()) {
			if (rule.value == value) {
				return rule;
			}
		}
		return null;
	}

	/**
	 * 文字列からenumオブジェクトを取得します
	 * 
	 * @param text
	 */
	public static CascadeRule parse(String text) {
		if (text==null){
			return null;
		}
		for (CascadeRule rule : CascadeRule.values()) {
			Matcher matcher = rule.pattern.matcher(text);
			if (matcher.matches()) {
				return rule;
			}
		}
		return null;
	}

	public String getAbbrName() {
		return this.getDisplayName();
	}

	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getDisplayName()
	 */
	@Override
	public String getDisplayName() {
		return this.text;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sqlapp.data.schemas.EnumProperties#getDisplayName(java.util.Locale)
	 */
	@Override
	public String getDisplayName(Locale locale) {
		return this.text;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.EnumProperties#getSqlValue()
	 */
	@Override
	public String getSqlValue() {
		return this.text;
	}
}
