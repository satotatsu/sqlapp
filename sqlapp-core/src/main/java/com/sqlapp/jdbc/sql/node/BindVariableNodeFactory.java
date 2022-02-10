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

import java.util.regex.Pattern;
/**
 * バインド変数のノードのファクトリ
 * @author satoh
 *
 */
public class BindVariableNodeFactory extends AbstractColumnNodeFactory<BindVariableNode>{

	protected final Pattern[] MATCH_PATTERNS=new Pattern[]{
		  Pattern.compile(OPEREATOR_PATTERN+"(?<value>\\s*/\\*(?<expression>[^+*#$\\s][\\S]*?)\\*/(N'.+?'|'.+?'))", Pattern.CASE_INSENSITIVE)
		, Pattern.compile(OPEREATOR_PATTERN+"(?<value>\\s*/\\*(?<expression>[^+*#$\\s][\\S]*?)\\*/(TRUE|False))", Pattern.CASE_INSENSITIVE)
		, Pattern.compile(OPEREATOR_PATTERN+"(?<value>\\s*/\\*(?<expression>[^+*#$\\s][\\S]*?)\\*/(TIME\\s*'.*?'|TIMESTAMP\\s*'.*?'|TIMESTAMP\\s+WITH\\s+TIMEZONE\\s*'.*?'|DATE\\s*'.*?'|DATETIME\\s*'.*?'))", Pattern.CASE_INSENSITIVE)
		, Pattern.compile(OPEREATOR_PATTERN+"(?<value>\\s*/\\*(?<expression>[^+*#$\\s][\\S]*?)\\*/(INTERVAL\\s*[^\\s]+?'\\s*(YEAR|MONTH|DAY|HOUR|MINUTE|SECOUND)\\s*(//([0-9]+//)){0,1}\\s*TO\\s+(MONTH|DAY|HOUR|MINUTE|SECOUND)))", Pattern.CASE_INSENSITIVE)
		, Pattern.compile(OPEREATOR_PATTERN+"(?<value>\\s*/\\*(?<expression>[^+*#$\\s][\\S]*?)\\*/(INTERVAL\\s*[^\\s]+?'\\s*(YEAR|MONTH|DAY|HOUR|MINUTE|SECOUND)(//([0-9]+//)){0,1}))", Pattern.CASE_INSENSITIVE)
		, Pattern.compile(OPEREATOR_PATTERN+"(?<value>\\s*/\\*(?<expression>[^+*#$\\s][\\S]*?)\\*/(INTERVAL\\s*[^\\s]+?'))", Pattern.CASE_INSENSITIVE)
		, Pattern.compile(OPEREATOR_PATTERN+"(?<value>\\s*/\\*(?<expression>[^+*#$\\s][\\S]*?)\\*/([^()\\r\\t\\n\\f\\s,]+))", Pattern.CASE_INSENSITIVE)
	};

	@Override
	public BindVariableNode newInstance() {
		return new BindVariableNode();
	}

	@Override
	protected Pattern[] getMatchPatterns() {
		return MATCH_PATTERNS;
	}
}
