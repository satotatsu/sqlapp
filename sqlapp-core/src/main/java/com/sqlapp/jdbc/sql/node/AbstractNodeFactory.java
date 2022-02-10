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

import static com.sqlapp.util.CommonUtils.treeMap;

import java.util.Map;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.util.Factory;

public abstract class AbstractNodeFactory<T extends Node> implements Factory<T> {
	/**
	 * SQLを正規表現で解析してマッチした全てのノードを取得する
	 * 
	 * @param sql
	 *            解析対象のSQL
	 * @return 開始位置をキーとしたノードのマップ
	 */
	public SortedMap<Integer, T> parseSql(String sql) {
		SortedMap<Integer, T> result = treeMap();
		for (Pattern pattern : getMatchPatterns()) {
			SortedMap<Integer, T> map = parseSql(pattern, sql);
			for (Map.Entry<Integer, T> entry : map.entrySet()) {
				if (!result.containsKey(entry.getKey())) {
					result.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return result;
	}

	/**
	 * SQLを正規表現で解析してマッチした全てのノードを取得する
	 * 
	 * @param sql
	 *            解析対象のSQL
	 * @return 開始位置をキーとしたノードのマップ
	 */
	protected SortedMap<Integer, T> parseSql(Pattern pattern, String sql) {
		SortedMap<Integer, T> result = treeMap();
		Matcher matcher = pattern.matcher(sql);
		while (matcher.find()) {
			boolean reject = false;
			for (Pattern rejectPattern : getRejectPatterns()) {
				Matcher rMatcher = rejectPattern.matcher(matcher.group(0));
				if (rMatcher.matches()) {
					reject = true;
					break;
				}
			}
			if (reject) {
				continue;
			}
			int start = matcher.start();
			int end = matcher.end();
			T node = newInstance();
			node.setIndex(start);
			setNodeValue(node, matcher);
			node.setSql(sql.substring(start,end));
			initialize(node);
			result.put(Integer.valueOf(node.getIndex()), node);
		}
		return result;
	}

	protected abstract void setNodeValue(T node, Matcher matcher);

	/**
	 * マッチするパターンの配列を返します
	 * 
	 */
	protected abstract Pattern[] getMatchPatterns();

	/**
	 * マッチしないパターンの配列を返します
	 * 
	 */
	protected Pattern[] getRejectPatterns() {
		return new Pattern[0];
	};

	/**
	 * 生成したインスタンスの初期化を行います
	 * 
	 * @param t
	 */
	protected void initialize(T t) {

	}
}
