package com.sqlapp.jdbc.sql;

import java.sql.Statement;

/**
 * Statement実行時の結果
 */
public enum GeneratedKey {
	/**
	 * The constant indicating that generated keys should be made available for
	 * retrieval.
	 */
	RETURN_GENERATED_KEYS(Statement.RETURN_GENERATED_KEYS) {
	},
	/**
	 * The constant indicating that generated keys should not be made available for
	 * retrieval.
	 */
	NO_GENERATED_KEYS(Statement.NO_GENERATED_KEYS) {
	};

	private final int value;

	private final String text;

	GeneratedKey(int value) {
		this.value = value;
		this.text = name().replace("_", "");
	}

	/**
	 * @return the value
	 */
	public Integer getValue() {
		return value;
	}

	/**
	 * 数値をenumに変換します。
	 * 
	 * @param value
	 * @return enumの値
	 */
	public static GeneratedKey parse(Integer value) {
		for (GeneratedKey type : values()) {
			if (type.getValue().equals(value)) {
				return type;
			}
		}
		return getDefault();
	}

	/**
	 * 文字列をenumに変換します。
	 * 
	 * @param value
	 * @return enumの値
	 */
	public static GeneratedKey parse(String value) {
		if (value == null) {
			return getDefault();
		}
		value = value.replace("_", "");
		for (GeneratedKey type : values()) {
			if (type.text.equalsIgnoreCase(value)) {
				return type;
			}
			if (type.getValue().toString().equals(value)) {
				return type;
			}
		}
		return getDefault();
	}

	/**
	 * デフォルト値を取得します。
	 * 
	 * @return デフォルト値
	 */
	public static GeneratedKey getDefault() {
		return null;
	}
}
