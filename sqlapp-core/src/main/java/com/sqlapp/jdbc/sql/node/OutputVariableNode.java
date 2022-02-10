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

package com.sqlapp.jdbc.sql.node;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.jdbc.sql.SqlParameterCollection;

/**
 * 出力変数用の要素
 * 
 */
public class OutputVariableNode extends CommentNode implements Cloneable {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1700573849729755073L;

	@Override
	public boolean eval(Object context, SqlParameterCollection sqlParameters) {
		Object val = evalExpression(this.getExpression(), context);
		if (val != null) {
			String strVal = Converters.getDefault().convertString(val,
					val.getClass());
			sqlParameters.addSql(sanitizeSimple(strVal));
		}
		return true;
	}

	/**
	 * 入力された文字でSQLインジェクションになりそうな文字を無効化した文字列を返します
	 * 
	 * @param text
	 */
	protected String sanitizeSimple(final String text) {
		if (text.startsWith("'") && text.endsWith("'")) {
			return "'"
					+ sanitizeSimpleInternal(text.substring(1,
							text.length() - 1)) + "'";
		}
		return sanitizeSimpleInternal(text);
	}

	private String sanitizeSimpleInternal(final String text) {
		return text.replace("--", "").replace(";", "").replace("\\", "")
				.replace("%", "").replace("?", "");
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
