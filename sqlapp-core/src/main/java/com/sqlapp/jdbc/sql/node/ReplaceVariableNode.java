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

import java.util.Map;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.exceptions.SqlSecurityException;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
/**
 * SQLコメントの置換変数用の要素クラス
 */
public class ReplaceVariableNode extends CommentNode{
    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7741989233299651746L;
	/**
	 * 置換する文字列の長さ
	 */
	private Integer length=null;
	/**
	 * 置換される文字列
	 */
	private String replaceString=null;
	
    @Override
    public boolean eval(Object context, SqlParameterCollection sqlParameters){
        Object val=evalExpression(this.getExpression(), context);
        if (val != null) {
        	String text=sanitize(val.toString());
        	if (this.getSqlKeywordCheck()!=null&&this.getSqlKeywordCheck().booleanValue()){
        		checkSqlSecurity(text);
        	}
            sqlParameters.addSql(text);
        	if (this.length!=null){
                sqlParameters.addSql(this.replaceString);
        	}
        }
        return true;
    }
    
    private void checkSqlSecurity(String text){
    	if (containsSqlWord(text)){
    		throw new SqlSecurityException("Invalid parameter."+this.getExpression()+"="+text);
    	}
    }
    
    @Override
    public void setExpression(String expression) {
    	String[] splits=expression.split(";");
    	this.expression=trim(splits[0]);
    	Map<String, String> map=parseKeyValue(expression);
    	for(Map.Entry<String, String> entry:map.entrySet()){
    		if ("length".equalsIgnoreCase(entry.getKey())){
    			this.length=Integer.valueOf(entry.getValue());
    		}
    		if ("sqlKeywordCheck".equalsIgnoreCase(entry.getKey())){
    			this.sqlKeywordCheck=Converters.getDefault().convertObject(entry.getValue(), Boolean.class);
    		}
    	}
    	if (this.length!=null){
    		String txt=this.getMatchText().substring(this.getMatchText().lastIndexOf("*/")+2);
    		this.replaceString=txt.substring(this.length);
    	}
		this.setParameterDefinition(new ParameterDefinition(this.expression));
	}

    private Boolean sqlKeywordCheck=false;
    
	public Integer getLength() {
		return length;
	}



	/**
	 * @return the sqlKeywordCheck
	 */
	public Boolean getSqlKeywordCheck() {
		return sqlKeywordCheck;
	}

	/**
	 * @param sqlKeywordCheck the sqlKeywordCheck to set
	 */
	public void setSqlKeywordCheck(Boolean sqlKeywordCheck) {
		this.sqlKeywordCheck = sqlKeywordCheck;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
    @Override
	public ReplaceVariableNode clone(){
		return (ReplaceVariableNode)super.clone();
	}
}
