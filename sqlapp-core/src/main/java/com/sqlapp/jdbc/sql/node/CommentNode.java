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

import static com.sqlapp.util.CommonUtils.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
/**
 * SQLのコメント要素
 * @author SATOH
 *
 */
public class CommentNode extends Node{
    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2382715829576494053L;
	/**
	 * 要素に正規表現でマッチした値
	 */
	private String matchText = null;
	/**評価する式*/
    protected String expression = null;

    
    public boolean eval(Object context, SqlParameterCollection sqlParameters) {
        return true;
    }

	public String getMatchText() {
		return matchText;
	}

	public void setMatchText(String matchText) {
		this.matchText = matchText;
	}

	public String getExpression() {
		return expression;
	}

	public void setExpression(String expression) {
		this.expression = trim(expression);
	}

	@Override
    public String toString(){
    	return this.getMatchText();
    }

    /**
     * 文字列のサニタイズ処理を行います
     * @param text
     */
    protected static String sanitize(String text){
    	if (text==null){
    		return text;
    	}
    	if (text.charAt(0)=='\''&&text.charAt(text.length()-1)=='\''){
        	return "'"+sanitizeInternal(text.substring(1, text.length()-1))+"'";
    	}else{
    		return sanitizeInternal(text);
    	}
    }


    /**
     * 文字列のサニタイズ処理を行います
     * @param text
     */
    protected static String sanitizeInternal(String text){
    	if (text==null){
    		return text;
    	}
    	return text.replace("'", "''").replace("/*", "").replace("*/", "").replace("--", "").replace(";", "");
    }

    private ParameterDefinition parameterDefinition;
    
    protected void setParameterDefinition(ParameterDefinition parameterDefinition){
    	if (parameterDefinition!=null&&parameterDefinition.getName()==null){
        	this.parameterDefinition=null;
    	} else{
        	this.parameterDefinition=parameterDefinition;
    	}
    }
    
    public ParameterDefinition getParameterDefinition(){
    	return parameterDefinition;
    }
    
    private static final Pattern SQL_PATTERN= Pattern.compile("(SELECT|INSERT|UPDATE|DELETE|DROP|TRUNCATE|UNION|TABLE)", Pattern.CASE_INSENSITIVE+Pattern.MULTILINE);
    
    protected boolean containsSqlWord(String text){
    	Matcher matcher=SQL_PATTERN.matcher(text);
    	return matcher.find();
    }
}
