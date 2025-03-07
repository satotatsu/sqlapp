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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.sqlapp.data.DataMessageReader;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.exceptions.ExpressionExecutionException;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlRegistry;
import com.sqlapp.util.eval.CachedEvaluator;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

public abstract class Node implements Comparator<Node>, Serializable,
		Cloneable, Comparable<Node> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 2889174869094179897L;

	private List<Node> childNodeList = new ArrayList<Node>();

	private String sql = null;

	private transient SqlRegistry sqlRegistry = null;
	/**
	 * 親ノード
	 */
	private Node parent = null;
	/**
	 * Eval実行クラス
	 */
	private transient CachedEvaluator evaluator = CachedMvelEvaluator
			.getInstance();

	public List<Node> getChildNodes() {
		return childNodeList;
	}

	private int nestedLevel = 0;

	private int index = 0;

	public void addChildNode(Node node) {
		childNodeList.add(node);
	}

	@Override
	public int compare(Node x, Node y) {
		if (x.getIndex() == y.index) {
			return 0;
		}
		if (x.getIndex() > y.index) {
			return 1;
		}
		return -1;
	}

	/**
	 * パラメタの取得
	 * 
	 * @param context
	 */
	public SqlParameterCollection eval(Object context) {
		return eval(context, (Dialect) null);
	}

	/**
	 * パラメタの取得
	 * 
	 * @param context
	 * @param dialect
	 */
	public SqlParameterCollection eval(Object context, Dialect dialect) {
		SqlParameterCollection sqlParameters = new SqlParameterCollection(
				dialect);
		this.eval(context, sqlParameters);
		return sqlParameters;
	}

	/**
	 * バインドパラメタの再評価
	 * 
	 * @param context
	 * @param sqlParameters
	 */
	public void reEval(Object context, SqlParameterCollection sqlParameters) {
		List<BindParameter> bindParameters = sqlParameters.getBindParameters();
		int size = bindParameters.size();
		for (int i = 0; i < size; i++) {
			BindParameter bindParameter = bindParameters.get(i);
			Object value = evalExpression(bindParameter.getName(), context);
			bindParameter.setValue(value);
		}
	}

	public abstract boolean eval(Object context,
			SqlParameterCollection sqlParameters);

	protected Object evalExpression(String expression, Object context) {
		try {
			return getEvaluator().getEvalExecutor(expression).eval(context);
		} catch (ExpressionExecutionException e) {
			throw handleExceptrion(e);
		}
	}

	protected ExpressionExecutionException handleExceptrion(Exception e) {
		String message = DataMessageReader.getInstance().getMessage(
				"ESQL00003", this.getLine(), this.getOffset(), this.getSql());
		ExpressionExecutionException ee = new ExpressionExecutionException(
				message, e);
		throw ee;
	}

	protected CachedEvaluator getEvaluator() {
		if (evaluator == null) {
			evaluator = CachedMvelEvaluator.getInstance();
		}
		return evaluator;
	}

	protected void setEvaluator(CachedEvaluator evaluator) {
		this.evaluator = evaluator;
	}

	public int getNestedLevel() {
		return nestedLevel;
	}

	public void setNestedLevel(int nestedLevel) {
		this.nestedLevel = nestedLevel;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	/**
	 * @return the sql
	 */
	public String getSql() {
		return sql;
	}

	/**
	 * @param sql
	 *            the sql to set
	 */
	public void setSql(String sql) {
		this.sql = sql;
	}

	/**
	 * @return the parent
	 */
	public Node getParent() {
		return parent;
	}

	/**
	 * @param parent
	 *            the parent to set
	 */
	public void setParent(Node parent) {
		this.parent = parent;
	}

	/**
	 * このノードのSQL上の行数を取得します。
	 * 
	 */
	public int getLine() {
		if (this.getIndex() == 0) {
			return 0;
		} else {
			if (this.getIndex()<=sql.length()){
				String sub = sql.substring(0, this.getIndex() - 1);
				return sub.split("[\\n]").length - 1;
			} else{
				return sql.split("[\\n]").length - 1;
			}
		}
	}

	/**
	 * @return the sqlRegistry
	 */
	protected SqlRegistry getSqlRegistry() {
		if (this.getParent() != null) {
			return this.getParent().getSqlRegistry();
		}
		return sqlRegistry;
	}

	/**
	 * @param sqlRegistry
	 *            the sqlRegistry to set
	 */
	public void setSqlRegistry(SqlRegistry sqlRegistry) {
		this.sqlRegistry = sqlRegistry;
	}

	/**
	 * このノードのSQL上の位置を取得します。
	 * 
	 */
	public int getOffset() {
		int i = 0;
		int start=this.getIndex();
		if (start>=this.getSql().length()){
			start=this.getSql().length()-1;
		}
		for (i = start; i >= 0; i--) {
			if (this.getSql().charAt(i) == '\n') {
				break;
			}
		}
		return (start - i - 1);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Node clone() {
		try {
			Node clone= (Node) super.clone();
			if (this.childNodeList!=null){
				clone.childNodeList.clear();
				for(Node node:childNodeList){
					clone.addChildNode(node.clone());
				}
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new InternalError(e.toString());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Node o) {
		if (this.getIndex() > o.getIndex()) {
			return 1;
		} else if (this.getIndex() < o.getIndex()) {
			return -1;
		}
		return 0;
	}
	
	@Override
	public String toString(){
		return this.getSql();
	}
}
