package com.sqlapp.data.db.command;

public interface Placeholders {

	/**
	 * @return the placeholderPrefix
	 */
	String getPlaceholderPrefix();

	/**
	 * @param placeholderPrefix the placeholderPrefix to set
	 */
	void setPlaceholderPrefix(final String placeholderPrefix);

	/**
	 * @return the placeholderSuffix
	 */
	String getPlaceholderSuffix();

	/**
	 * @param placeholderSuffix the placeholderSuffix to set
	 */
	void setPlaceholderSuffix(final String placeholderSuffix);

	/**
	 * @return the placeholders
	 */
	boolean isPlaceholders();

	/**
	 * @param placeholders the placeholders to set
	 */
	void setPlaceholders(final boolean placeholders);

}
