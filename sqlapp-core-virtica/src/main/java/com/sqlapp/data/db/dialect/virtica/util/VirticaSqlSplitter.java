/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.virtica.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;
import com.sqlapp.data.db.dialect.util.StringHolder;

public class VirticaSqlSplitter extends SqlSplitter{

	public VirticaSqlSplitter(Dialect dialect) {
		super(dialect);
	}
	
	private static final Pattern CREATE_PATTERN=Pattern.compile("\\s*(CREATE|ALTER)\\s*.*", Pattern.MULTILINE+Pattern.CASE_INSENSITIVE+Pattern.DOTALL);

	private static final Pattern BEGIN_PATTERN=Pattern.compile("\\s*BEGIN\\s*", Pattern.MULTILINE+Pattern.CASE_INSENSITIVE+Pattern.DOTALL);

	private static final Pattern END_PATTERN=Pattern.compile("\\s*END\\s*(WITH\\s+CACHE\\s+RETENTION\\s+[0-9]+\\s*)?(OF\\s+[^;]+?)?;?", Pattern.MULTILINE+Pattern.CASE_INSENSITIVE+Pattern.DOTALL);

	@Override
	protected SqlTokenizer createSqlTokenizer(String input){
		return new SqlTokenizer(input){
			@Override
			protected boolean isStartStatement(String text, StringHolder stringHolder){
				Matcher matcher=CREATE_PATTERN.matcher(text);
				if (matcher.matches()){
					int delPos=stringHolder.indexOf(this.getCurrentDelimiter(), stringHolder.getPosition()+10);
					int beginPos=stringHolder.searchLineOf(BEGIN_PATTERN, stringHolder.getPosition()+10);
					if (beginPos<0){
						return false;
					}
					if (delPos<beginPos){
						return false;
					}
					int i=beginPos;
					while(true){
						int ePos=stringHolder.searchLineOf(END_PATTERN, i+6);
						if (ePos<0){
							stringHolder.throwInvalidTextException("[END] element not found.["+stringHolder.substringAt()+"]");
						}
						int bPos=stringHolder.searchLineOf(BEGIN_PATTERN, i+6);
						if (bPos>=0){
							if (bPos<ePos){
								i=ePos+3;
								continue;
							} else{
								int last=stringHolder.indexOf(this.getCurrentDelimiter(), ePos+3);
								setPosition(last);
								return true;
							}
						} else{
							int last=stringHolder.indexOf(this.getCurrentDelimiter(), ePos+3);
							setPosition(last);
							return true;
						}
					}
				}
				return false;
			}
		};
	}
}
