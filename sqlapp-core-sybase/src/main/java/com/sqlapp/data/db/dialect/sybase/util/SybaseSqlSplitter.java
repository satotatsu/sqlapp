/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sybase.
 *
 * sqlapp-core-sybase is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sybase is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sybase.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.sybase.util;

import java.util.List;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;
import com.sqlapp.util.CommonUtils;

public class SybaseSqlSplitter extends SqlSplitter{

	public SybaseSqlSplitter(Dialect dialect){
		super(dialect);
	}
	
	private static final Pattern GO_PATTERN=Pattern.compile("GO\\s*(?<itr>[0-9]+)?\\s*", Pattern.CASE_INSENSITIVE);

	private int previousGoPosition=0;

	private Integer itrCount=null;

	private boolean go=false;
	
	protected void handleGOIterate(StringHolder stringHolder, Integer itrCount){
		this.itrCount=itrCount;
		this.go=true;
	}
	
	protected void addStatementAfter(){
		if (!go){
			return;
		}
		this.go=false;
		if (this.getStatements().size()>0&&itrCount!=null){
			List<SplitResult> statements=getIterateStatements();
			for(int i=0;i<itrCount-1;i++){
				this.getStatements().addAll(statements);
			}
		}
		itrCount=null;
		previousGoPosition=this.getStatements().size();
		
	}

	private List<SplitResult> getIterateStatements(){
		return this.getStatements().subList(previousGoPosition, this.getStatements().size());
	}
	
	@Override
	protected SqlTokenizer createSqlTokenizer(String input){
		return new SqlTokenizer(input){

			@Override
			protected void handleElse(StringHolder stringHolder){
				int pos=stringHolder.searchLineOf(GO_PATTERN, stringHolder.getPosition(), true, (i, matcher)->{
					handleSimpleStatement(i, stringHolder);
					stringHolder.setPosition(i+matcher.group().length()+1);
					String val=matcher.group("itr");
					if (!CommonUtils.isEmpty(val)){
						Integer itrCount=Integer.valueOf(val);
						handleGOIterate(stringHolder, itrCount);
					} else{
						handleGOIterate(stringHolder, null);
					}
					return true;
				});
				if (pos<0){
					setPosition(pos);
				}
			}
			
		};
		
	}

	

}
