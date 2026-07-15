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
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.sqlapp.data.DataMessageReader;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.ExpressionExecutionException;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.BindParameterHolder;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.SqlRegistry;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.eval.CachedEvaluator;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

public abstract class Node implements Comparator<Node>, Serializable, Cloneable, Comparable<Node> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 2889174869094179897L;

	private Dialect dialect;

	public Dialect getDialect() {
		if (parent != null) {
			Dialect val = parent.getDialect();
			if (val != null) {
				return val;
			}
		}
		return dialect;
	}

	public void setDialect(Dialect dialect) {
		this.dialect = dialect;
	}

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
	private transient CachedEvaluator evaluator = CachedMvelEvaluator.getInstance();

	public List<Node> getChildNodes() {
		return childNodeList;
	}

	private int nestedLevel = 0;

	private int index = 0;

	public void addChildNode(Node node) {
		node.setParent(this);
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
	 * evalした結果のパラメタを取得します
	 * 
	 * @param context
	 * @return SqlParameterCollection
	 */
	public SqlParameterCollection eval(Object context) {
		return eval(context, paran -> {
		});
	}

	/**
	 * evalした結果のパラメタを取得します
	 * 
	 * @param context
	 * @param initializer
	 * @return SqlParameterCollection
	 */
	public SqlParameterCollection eval(Object context, Consumer<SqlParameterCollection> initializer) {
		SqlParameterCollection sqlParameters = new SqlParameterCollection(dialect);
		sqlParameters.setTable(getTable(context));
		List<Row> rows = this.getRowList(context);
		initializer.accept(sqlParameters);
		if (sqlParameters.getTable() != null) {
			if (sqlParameters.getSqlSignature() == null) {
				sqlParameters.setSqlSignature(new SqlSignature(sqlParameters.getTable(),
						rows != null ? rows : sqlParameters.getTable().getRows()));
			}
		}
		this.eval(context, sqlParameters);
		return sqlParameters;
	}

	/**
	 * evalの結果を取得します
	 * 
	 * @param context
	 * @param dialect
	 * @return SqlParameterCollection
	 */
	public SqlParameterCollection eval(Number number) {
		SqlParameterCollection sqlParameters = new SqlParameterCollection(dialect);
		Map<String, Object> context = CommonUtils.map();
		context.put("context", number);
		this.eval(context, sqlParameters);
		return sqlParameters;
	}

	/**
	 * SqlParameterCollectionを作成します
	 * 
	 * @param dialect
	 * @return SqlParameterCollection
	 */
	public SqlParameterCollection createSqlParameters() {
		SqlParameterCollection sqlParameters = new SqlParameterCollection(dialect);
		Map<String, Object> context = CommonUtils.map();
		this.eval(context, sqlParameters);
		return sqlParameters;
	}

	/**
	 * バインドパラメタの再評価
	 * 
	 * @param context
	 * @param sqlParameters
	 * @return SQL再評価が不要か?
	 */
	public boolean reEval(Object context, SqlParameterCollection sqlParameters) {
		int cnt = 0;
		for (final BindParameterHolder bindParameterHolder : sqlParameters.getBindParameters()) {
			if (bindParameterHolder.getBindParameter() != null) {
				final BindParameter bindParameter = bindParameterHolder.getBindParameter();
				final Object value = evalValueAndSetDataType(context, bindParameter);
				bindParameter.setValue(value);
				cnt++;
			} else {
				final List<Row> rows = getRowList(context);
				int rowSize = rows.size();
				int paramSize = bindParameterHolder.getBindParameters().size();
				int columnSize = paramSize / rowSize;
				for (int i = 0; i < rowSize; i++) {
					Row row = rows.get(i);
					for (int j = i * columnSize; j < ((i + 1) * columnSize); j++) {
						final BindParameter bindParameter = bindParameterHolder.getBindParameters().get(j);
						if (isRowNumber(bindParameter)) {
							bindParameter.setValue(row.getRowId());
							cnt++;
						} else {
							final Object value = row.get(bindParameter.getColumn());
							bindParameter.setValue(value);
							cnt++;
						}
					}
				}
			}
		}
		return sqlParameters.getParameterSize() == cnt;
	}

	protected Object evalValueAndSetDataType(Object context, final BindParameter parameter) {
		Column column = this.getColumn(context, parameter.getName());
		Object val;
		if (column != null) {
			parameter.setDataType(column.getDataType());
			if (context instanceof Row) {
				final Row row = (Row) context;
				val = row.get(column);
			} else {
				val = evalExpression(parameter.getName(), context);
			}
		} else {
			val = evalExpression(parameter.getName(), context);
		}
		return val;
	}

	protected Column getColumn(Row row, String key) {
		if (row == null) {
			return null;
		}
		if (row.getParent() != null && row.getParent().getParent() != null) {
			return row.getParent().getParent().getColumns().get(key);
		}
		return null;
	}

	protected Column getColumn(Object context, String key) {
		if (context == null) {
			return null;
		}
		if (context instanceof ParametersContext) {
			ParametersContext param = (ParametersContext) context;
			return param.getColumn(key);
		} else if (context instanceof Table) {
			Table param = (Table) context;
			return param.getColumns().get(key);
		} else if (context instanceof Row) {
			return getColumn((Row) context, key);
		} else if (context instanceof List) {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Object first = CommonUtils.first((List) context);
			return getColumn(first, key);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	protected Table getTable(final Object context) {
		Table table = getTableFromContext(context);
		if (table != null) {
			return table;
		}
		if (context instanceof Map<?, ?>) {
			for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) context).entrySet()) {
				table = getTableFromContext(entry.getValue());
				if (table != null) {
					return table;
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	protected List<Row> getRowList(final Object context) {
		List<Row> rows = getRowListFromContext(context);
		if (rows != null) {
			return rows;
		}
		if (context instanceof Map<?, ?>) {
			for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) context).entrySet()) {
				rows = getRowListFromContext(entry.getValue());
				if (rows != null) {
					return rows;
				}
			}
		}
		return null;
	}

	protected boolean hasRowNo(final List<Row> rows) {
		for (Row row : rows) {
			if (SchemaUtils.getInternalRowId(row) != null) {
				return true;
			}
		}
		return false;
	}

	protected String getQuestionText(int size) {
		SeparatedStringBuilder builder = new SeparatedStringBuilder(",");
		for (int i = 0; i < size; i++) {
			builder.add("?");
		}
		return builder.toString();
	}

	public static final String ROW_NO = "__row_no";

	private boolean isRowNumber(BindParameter param) {
		return ROW_NO.equals(param.getValue());
	}

	protected BindParameter createRowNo(Row row) {
		BindParameter dbParameter = new BindParameter();
		dbParameter.setName(ROW_NO);
		dbParameter.setValue(SchemaUtils.getInternalRowId(row));
		dbParameter.setDataType(DataType.BIGINT);
		return dbParameter;
	}

	protected ColumnCollection getColumns(RowCollection rows) {
		if (rows.getParent() == null) {
			return new Table().getColumns();
		}
		return rows.getParent().getColumns();
	}

	protected Table getTableFromContext(final Object context) {
		if (context instanceof Table) {
			return (Table) context;
		} else if (context instanceof RowCollection) {
			return ((RowCollection) context).getParent();
		} else if (context instanceof Row) {
			Row row = (Row) context;
			if (row.getParent() != null) {
				return row.getParent().getParent();
			}
		} else if (context instanceof List) {
			List<?> list = (List<?>) context;
			Object obj = CommonUtils.first(list);
			if (list.isEmpty()) {
				return null;
			} else if (obj instanceof Row) {
				Row row = (Row) obj;
				if (row.getParent() != null) {
					return row.getParent().getParent();
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	protected List<Row> getRowListFromContext(final Object context) {
		if (context instanceof RowCollection) {
			return (RowCollection) context;
		} else if (context instanceof Table) {
			return ((Table) context).getRows();
		} else if (context instanceof Row) {
			List<Row> rows = CommonUtils.list(1);
			rows.add((Row) context);
			return rows;
		} else if (context instanceof List) {
			List<?> list = (List<?>) context;
			Object obj = CommonUtils.first(list);
			if (list.isEmpty()) {
				return new Table().getRows();
			} else if (obj instanceof Row) {
				return (List<Row>) list;
			}
		}
		return null;
	}

	public abstract boolean eval(Object context, SqlParameterCollection sqlParameters);

	protected Object evalExpression(String expression, Object context) {
		try {
			return getEvaluator().eval(expression, context);
		} catch (ExpressionExecutionException e) {
			throw handleExceptrion(e);
		}
	}

	protected ExpressionExecutionException handleExceptrion(Exception e) {
		String message = DataMessageReader.getInstance().getMessage("ESQL00003", this.getLine(), this.getOffset(),
				this.getSql());
		ExpressionExecutionException ee = new ExpressionExecutionException(message, e);
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
	 * @param sql the sql to set
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
	 * @param parent the parent to set
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
			if (this.getIndex() <= sql.length()) {
				String sub = sql.substring(0, this.getIndex() - 1);
				return sub.split("[\\n]").length - 1;
			} else {
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
	 * @param sqlRegistry the sqlRegistry to set
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
		int start = this.getIndex();
		if (start >= this.getSql().length()) {
			start = this.getSql().length() - 1;
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
			Node clone = (Node) super.clone();
			if (this.childNodeList != null) {
				clone.childNodeList.clear();
				for (Node node : childNodeList) {
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
	public String toString() {
		return this.getSql();
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.getClass(), sql, this.getChildNodes().size());
	}
}
