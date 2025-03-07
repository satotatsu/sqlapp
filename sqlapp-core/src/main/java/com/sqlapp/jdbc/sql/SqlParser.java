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

package com.sqlapp.jdbc.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;
import static com.sqlapp.util.CommonUtils.list;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.DataMessageReader;
import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.exceptions.SqlParseException;
import com.sqlapp.jdbc.sql.node.AbstractNodeFactory;
import com.sqlapp.jdbc.sql.node.BindVariableArrayNodeFactory;
import com.sqlapp.jdbc.sql.node.BindVariableNodeFactory;
import com.sqlapp.jdbc.sql.node.CommentNode;
import com.sqlapp.jdbc.sql.node.ElseIfNode;
import com.sqlapp.jdbc.sql.node.ElseNode;
import com.sqlapp.jdbc.sql.node.ElseNodeFactory;
import com.sqlapp.jdbc.sql.node.EndNode;
import com.sqlapp.jdbc.sql.node.EndNodeFactory;
import com.sqlapp.jdbc.sql.node.ForNodeFactory;
import com.sqlapp.jdbc.sql.node.IfNode;
import com.sqlapp.jdbc.sql.node.IfNodeFactory;
import com.sqlapp.jdbc.sql.node.InputStreamNodeFactory;
import com.sqlapp.jdbc.sql.node.NeedsEndNode;
import com.sqlapp.jdbc.sql.node.Node;
import com.sqlapp.jdbc.sql.node.OutputStreamNodeFactory;
import com.sqlapp.jdbc.sql.node.OutputVariableNodeFactory;
import com.sqlapp.jdbc.sql.node.ParameterMarkerNodeFactory;
import com.sqlapp.jdbc.sql.node.QueryNodeFactory;
import com.sqlapp.jdbc.sql.node.ReplaceVariableNodeFactory;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.jdbc.sql.node.SqlPartNode;
import com.sqlapp.util.CommonUtils;

/**
 * SQLのパーサー
 *
 */
public class SqlParser {

	private static final SqlParser instance = new SqlParser();

	public static SqlParser getInstance() {
		return instance;
	}

	private SqlParser() {
		nodeFactoryList = list();
		nodeFactoryList.add(new BindVariableArrayNodeFactory());
		nodeFactoryList.add(new BindVariableNodeFactory());
		nodeFactoryList.add(new IfNodeFactory());
		nodeFactoryList.add(new ElseNodeFactory());
		nodeFactoryList.add(new ForNodeFactory());
		nodeFactoryList.add(new ReplaceVariableNodeFactory());
		nodeFactoryList.add(new OutputVariableNodeFactory());
		nodeFactoryList.add(new EndNodeFactory());
		nodeFactoryList.add(new QueryNodeFactory());
		nodeFactoryList.add(new ParameterMarkerNodeFactory());
		nodeFactoryList.add(new InputStreamNodeFactory());
		nodeFactoryList.add(new OutputStreamNodeFactory());
	}

	private List<AbstractNodeFactory<?>> nodeFactoryList = null;

	/**
	 * SQLの解析メソッド
	 */
	public SqlNode parse(final String sql) {
		SqlNode rootNode = new SqlNode();
		SortedMap<Integer, Node> sortedNodes = createNodes(sql);
		Map<Integer, Integer> keyMap = createKeyMap(sortedNodes);
		parseSql(rootNode, sortedNodes, keyMap, 0, sortedNodes.size(), 0);
		rootNode.setParameters(getParameterMarkerNodes(rootNode));
		return rootNode;
	}
	
	private Set<ParameterDefinition> getParameterMarkerNodes(SqlNode rootNode){
		Set<ParameterDefinition> parameters=CommonUtils.linkedSet();
		setParameterDefinitions(rootNode, parameters);
		Set<ParameterDefinition> result=CommonUtils.linkedSet();
		for(ParameterDefinition parameterDefinition:parameters){
			List<ParameterDefinition> list=convert(parameterDefinition);
			if (list!=null){
				result.addAll(list);
			}
		}
		return result;
	}

	private static Pattern PARAMETER_NAME_FILTER=Pattern.compile("(true|false|null|[0-9]+)");

	private static Pattern PARAMETER_EQ_FILTER=Pattern.compile("is(Not)?Empty\\(([^)]+)\\)");

	private static Pattern PARAMETER_PATH_FILTER=Pattern.compile("(.*)\\.(\\(.*?\\))");

	private List<ParameterDefinition> convert(ParameterDefinition parameterDefinition){
		parameterDefinition.setName(CommonUtils.trim(CommonUtils.unwrap(parameterDefinition.getName(), '(', ')')));
		String[] splits=parameterDefinition.getName().split("(\\|\\||&&)");
		List<ParameterDefinition> result=CommonUtils.list();
		if (splits.length==1){
			Matcher matcher=PARAMETER_EQ_FILTER.matcher(parameterDefinition.getName());
			if (matcher.matches()){
				result.add(new ParameterDefinition(matcher.group(2)));
				return result;
			}
		}
		for(String val:splits){
			val=CommonUtils.unwrap(CommonUtils.trim(val), '(', ')');
			String[] eqSplit=val.split("(==|\\|\\||!=|<=?|>=?)");
			for(String eqVal:eqSplit){
				eqVal=CommonUtils.unwrap(CommonUtils.trim(eqVal), '(', ')');
				Matcher matcher=PARAMETER_NAME_FILTER.matcher(eqVal);
				if (matcher.matches()){
					continue;
				}
				if (isString(eqVal)){
					continue;
				}
				matcher=PARAMETER_EQ_FILTER.matcher(eqVal);
				if (matcher.matches()){
					result.add(new ParameterDefinition(matcher.group(2)));
					continue;
				}
				matcher=PARAMETER_PATH_FILTER.matcher(eqVal);
				if (matcher.matches()){
					result.add(new ParameterDefinition(matcher.group(1)));
					continue;
				}
				result.add(new ParameterDefinition(eqVal));
			}
		}
		return result;
	}
	
	private boolean isString(String value){
		if (value.startsWith("\"")&&value.endsWith("\"")&&value.contains("\"")){
			return true;
		}
		if (value.startsWith("'")&&value.endsWith("'")&&value.contains("'")){
			return true;
		}
		return false;
	}

	private void setParameterDefinitions(Node node, Set<ParameterDefinition> parameters){
		if (node instanceof CommentNode){
			ParameterDefinition def=((CommentNode) node).getParameterDefinition();
			if (def!=null&&def.getName()!=null){
				parameters.add(def);
			}
		}
		for(Node child:node.getChildNodes()){
			setParameterDefinitions(child, parameters);
		}
	}
	
	
	private void parseSql(Node node, SortedMap<Integer, Node> sortedNodes,
			Map<Integer, Integer> keyMap, int start, int end, int nestedLevel) {
		Node childNode = null;
		for (int i = start; i < end; i++) {
			int index = keyMap.get(i);
			childNode = sortedNodes.get(index);
			if (childNode instanceof NeedsEndNode) {
				i = parseNeedsEndNodes((NeedsEndNode) childNode, sortedNodes,
						keyMap, i + 1, end, nestedLevel + 1);
			}
			childNode.setNestedLevel(nestedLevel);
			if (!(childNode instanceof EndNode)) {
				node.addChildNode(childNode);
			}
		}
	}

	private Map<Integer, Integer> createKeyMap(
			SortedMap<Integer, Node> sortedNodes) {
		Map<Integer, Integer> keyMap = new HashMap<Integer, Integer>();
		int i = 0;
		for (Integer key : sortedNodes.keySet()) {
			keyMap.put(i++, key);
		}
		return keyMap;
	}

	/**
	 * 終了要素(end)を含む要素の解析
	 * 
	 * @param node
	 * @param sortedNodes
	 * @param keyMap
	 * @param start
	 * @param end
	 * @param nestedLevel
	 */
	private int parseNeedsEndNodes(NeedsEndNode node,
			SortedMap<Integer, Node> sortedNodes, Map<Integer, Integer> keyMap,
			int start, int end, int nestedLevel) {
		Node childNode = null;
		int i = 0;
		for (i = start; i < end; i++) {
			int index = keyMap.get(i);
			childNode = sortedNodes.get(index);
			if (childNode instanceof NeedsEndNode) {
				i = parseNeedsEndNodes((NeedsEndNode) childNode, sortedNodes,
						keyMap, i + 1, end, nestedLevel++);
				node.addChildNode(childNode);
			} else if (childNode instanceof EndNode) {
				return i;
			} else if (node instanceof IfNode
					&& childNode instanceof ElseIfNode) {
				childNode.setNestedLevel(nestedLevel);
				((IfNode) node).getElseIfNodes().add((ElseIfNode) childNode);
			} else if (node instanceof IfNode && childNode instanceof ElseNode) {
				childNode.setNestedLevel(nestedLevel);
				((IfNode) node).setElseNode((ElseNode) childNode);
			} else {
				childNode.setNestedLevel(nestedLevel + 1);
				node.addChildNode(childNode);
			}
		}
		String message = DataMessageReader.getInstance().getMessage(
				"ESQL00002", node.getMatchText());
		throw new SqlParseException(message);
	}

	private SortedMap<Integer, Node> createNodes(final String sql) {
		SortedMap<Integer, Node> sortedNodes = new TreeMap<Integer, Node>();
		for (AbstractNodeFactory<?> factory : nodeFactoryList) {
			sortedNodes.putAll(factory.parseSql(sql));
		}
		// SQL
		parseSql(sql, sortedNodes);
		return sortedNodes;
	}

	private void parseSql(final String sql, SortedMap<Integer, Node> sortedNodes) {
		int pos = 0;
		int len = 0;
		List<SqlPartNode> sqlNodes = new ArrayList<SqlPartNode>();
		int index = 0;
		CommentNode commentNode = null;
		SqlPartNode sqlNode = new SqlPartNode();
		sqlNode.setIndex(0);
		if (sortedNodes.size() > 0) {
			index = sortedNodes.firstKey();
			commentNode = (CommentNode) sortedNodes.get(index);
			len = commentNode.getIndex() - pos;
			sqlNode.setSql(sql.substring(pos, len));
			pos = index + commentNode.getMatchText().length();
		} else {
			len = sql.length();
			sqlNode.setSql(sql.substring(pos, len));
			pos = len;
		}
		sqlNodes.add(sqlNode);
		boolean first = true;
		for (Map.Entry<Integer, Node> entry : sortedNodes.entrySet()) {
			if (!first) {
				index = entry.getKey();
				commentNode = (CommentNode) entry.getValue();
				if (index >= pos) {
					sqlNode = new SqlPartNode();
					sqlNode.setSql(sql.substring(pos, index));
					sqlNode.setIndex(pos);
					sqlNodes.add(sqlNode);
					pos = index + commentNode.getMatchText().length();
				}
			}
			first = false;
		}
		if (pos < sql.length()) {
			sqlNode = new SqlPartNode();
			sqlNode.setSql(sql.substring(pos));
			sqlNode.setIndex(pos);
			sqlNodes.add(sqlNode);
		}
		for (SqlPartNode ele : sqlNodes) {
			if (!isEmpty(ele.getSql())) {
				sortedNodes.put(ele.getIndex(), ele);
			}
		}
	}

}
