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

package com.sqlapp.jdbc.sql.node;

import static com.sqlapp.util.CommonUtils.trim;

import java.util.regex.Pattern;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.exceptions.SqlSecurityException;
import com.sqlapp.jdbc.sql.SqlParameterCollection;

/**
 * 出力変数用の要素
 * <p>
 * {@code /*#expression*\/} は式の値をバインド変数ではなくSQLテキストとして出力します。
 * </p>
 * <ul>
 * <li>既定: 数値と真偽値はそのまま出力します。それ以外の値は文字・数字・{@code _}・{@code .}
 * だけで構成される場合に限り出力し、それ以外は {@link SqlSecurityException} とします。</li>
 * <li>{@code /*#expression;literal*\/}: 値を {@link Dialect#toStringLiteral(String)} で文字列リテラルとして
 * 出力します。既定では {@code '} を {@code ''} にエスケープし、バックスラッシュを含む値は拒否します。
 * MySQL・PostgreSQL系はバックスラッシュもエスケープします。</li>
 * <li>{@code /*#expression;identifier*\/}: 値全体を {@link Dialect#quoteIdentifier(String)} で1つの識別子として
 * クォートして出力します。空文字の場合は何も出力しません。</li>
 * <li>{@code /*#expression;raw*\/}: 値を加工せずに出力します。信頼できる値にだけ使用してください。</li>
 * </ul>
 * 文字列の値を埋め込むだけであれば、バインド変数 {@code /*expression*\/'dummy'} を使用してください。
 */
public class OutputVariableNode extends CommentNode implements Cloneable {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1700573849729755073L;

	/**
	 * 出力方法
	 */
	public enum OutputMode {
		/** 数値・真偽値、または安全な文字だけの値を出力 */
		DEFAULT,
		/** 文字列リテラルとして出力 */
		LITERAL,
		/** クォートした識別子として出力 */
		IDENTIFIER,
		/** 加工せずに出力 */
		RAW;
	}

	private static final Pattern SAFE_TEXT = Pattern.compile("[\\p{L}\\p{N}_.]*");

	private OutputMode outputMode = OutputMode.DEFAULT;

	@Override
	public boolean eval(Object context, SqlParameterCollection sqlParameters) {
		Object val = evalExpression(this.getExpression(), context);
		if (val != null) {
			sqlParameters.registerEvaluatedParameter(getExpression());
			final Dialect dialect = sqlParameters.getDialect() != null ? sqlParameters.getDialect() : getDialect();
			sqlParameters.addSql(toSql(val, dialect));
		}
		return true;
	}

	@Override
	public void setExpression(String expression) {
		String text = trim(expression);
		OutputMode mode = OutputMode.DEFAULT;
		if (text != null) {
			final String[] parts = text.split("\\s*;\\s*");
			text = parts[0];
			for (int i = 1; i < parts.length; i++) {
				final OutputMode option = parseOption(parts[i], expression);
				if (mode != OutputMode.DEFAULT && mode != option) {
					throw new IllegalArgumentException("Conflicting output options: " + expression);
				}
				mode = option;
			}
		}
		super.setExpression(text);
		this.outputMode = mode;
	}

	private static OutputMode parseOption(final String option, final String expression) {
		if ("literal".equalsIgnoreCase(option)) {
			return OutputMode.LITERAL;
		} else if ("identifier".equalsIgnoreCase(option)) {
			return OutputMode.IDENTIFIER;
		} else if ("raw".equalsIgnoreCase(option)) {
			return OutputMode.RAW;
		}
		throw new IllegalArgumentException("Unknown output option [" + option + "]: " + expression);
	}

	/**
	 * @return the output mode
	 */
	public OutputMode getOutputMode() {
		return outputMode;
	}

	protected String toSql(final Object val, final Dialect dialect) {
		final String converted = Converters.getDefault().convertString(val, val.getClass());
		final String text = converted != null ? converted : val.toString();
		switch (outputMode) {
		case RAW:
			return text;
		case LITERAL:
			return toLiteral(text, dialect);
		case IDENTIFIER:
			return toIdentifier(text, dialect);
		default:
			if (val instanceof Number || val instanceof Boolean) {
				return text;
			}
			if (!SAFE_TEXT.matcher(text).matches()) {
				throw new SqlSecurityException("Unsafe value for " + this.getMatchText()
						+ ". Use a bind variable, ;literal or ;raw. value=" + text);
			}
			return text;
		}
	}

	private String toLiteral(final String text, final Dialect dialect) {
		if (dialect == null) {
			// Without a dialect the backslash handling is unknown.
			if (text.indexOf('\\') >= 0 || text.indexOf('\0') >= 0) {
				throw new SqlSecurityException("Backslash or NUL is not supported in " + this.getMatchText()
						+ " without a dialect. Use a bind variable.");
			}
			return "'" + text.replace("'", "''") + "'";
		}
		try {
			return dialect.toStringLiteral(text);
		} catch (final IllegalArgumentException e) {
			throw new SqlSecurityException(e.getMessage() + " " + this.getMatchText() + ". Use a bind variable.");
		}
	}

	private String toIdentifier(final String text, final Dialect dialect) {
		if (text.isEmpty()) {
			return text;
		}
		if (text.indexOf('\0') >= 0) {
			throw new SqlSecurityException("NUL character is not supported in " + this.getMatchText() + ".");
		}
		if (dialect == null) {
			return "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return dialect.quoteIdentifier(text);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public OutputVariableNode clone() {
		return (OutputVariableNode) super.clone();
	}
}
