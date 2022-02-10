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

package com.sqlapp.jdbc.sql;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.rowiterator.ExpressionConverter;
import com.sqlapp.exceptions.InvalidTextException;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class SqlConverter {

	private ExpressionConverter expressionConverter=new ExpressionConverter();
	
	/**
	 * @return the expressionConverter
	 */
	public ExpressionConverter getExpressionConverter() {
		return expressionConverter;
	}

	/**
	 * @param expressionConverter the expressionConverter to set
	 */
	public void setExpressionConverter(ExpressionConverter expressionConverter) {
		this.expressionConverter = expressionConverter;
	}

	/**
	 * SQLを解析してファイルデータをファイルデータで置換します。
	 * @param sql 解析前のSQL
	 * @return 解析済みSQL
	 */
	public SqlNode parseSql(ParametersContext context, String sql){
		if (getExpressionConverter().isPlaceholders()){
			return parseSqlInternal(context, sql);
		} else{
			return SqlParser.getInstance().parse(sql);
		}
	}

	private SqlNode parseSqlInternal(ParametersContext context, String sql){
		StringBuilder builder=new StringBuilder(sql.length());
		int pos=0;
		boolean find=false;
		while(pos<sql.length()){
			int start=sql.indexOf(getExpressionConverter().getPlaceholderPrefix(), pos);
			if (start>=0){
				int end=sql.indexOf(getExpressionConverter().getPlaceholderSuffix(), start+getExpressionConverter().getPlaceholderPrefix().length());
				if (end<0){
					toInvalidTextException(sql,pos, "placeholderSuffix["+getExpressionConverter().getPlaceholderSuffix()+"] not found.");
				}
				builder.append(sql.substring(pos, start));
				String expression=sql.substring(start+getExpressionConverter().getPlaceholderPrefix().length(), end);
				builder.append("/*"+expression+"*/'1'");
				pos=end+1;
				find=true;
			} else{
				builder.append(sql.substring(pos));
				break;
			}
		}
		SqlNode node;
		if (find){
			node=SqlParser.getInstance().parse(builder.toString());
		} else{
			node=SqlParser.getInstance().parse(sql);
		}
		return node;
	}
	
	private InvalidTextException toInvalidTextException(String sql, int pos, String message){
		int lineBreakCount=1;
		int lastLineBreakPos=0;
		for(int i=0;i<pos;i++){
			if (sql.charAt(i)=='\n'){
				lineBreakCount++;
				lastLineBreakPos=i;
			}
		}
		int nextLineBreakPos=0;
		for(nextLineBreakPos=pos;nextLineBreakPos<sql.length();nextLineBreakPos++){
			if (sql.charAt(nextLineBreakPos)=='\n'){
				break;
			}
		}
		throw new InvalidTextException(sql.substring(lastLineBreakPos+1, nextLineBreakPos), lineBreakCount, pos, message);
	}
}
