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

import static com.sqlapp.util.CommonUtils.map;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.util.AbstractIterator;
import com.sqlapp.util.SimpleBeanUtils;

/**
 * SQLコメントのFor要素
 * <p>
 * Loop variables shadow variables with the same name in the input context.
 * The input context is not modified.
 * 
 */
public class ForNode extends NeedsEndNode implements Cloneable {

	/** serialVersionUID */
	private static final long serialVersionUID = -1261063598891910651L;
	/**
	 * 変数名
	 */
	private String variableName = null;

	/** Local writes with the original Map's lookup behavior for other names. */
	private static final class LoopContext extends AbstractMap<Object, Object> {
		private final Map<?, ?> source;
		private final Map<Object, Object> locals = new LinkedHashMap<>();
		private final Set<Object> removed = new HashSet<>();
		private boolean cleared;

		LoopContext(Map<?, ?> source) {
			this.source = source;
		}

		@Override
		public Object get(Object key) {
			if (locals.containsKey(key)) return locals.get(key);
			return cleared || removed.contains(key) ? null : source.get(key);
		}

		@Override
		public boolean containsKey(Object key) {
			return locals.containsKey(key) || (!cleared && !removed.contains(key) && source.containsKey(key));
		}

		@Override
		public Object put(Object key, Object value) {
			Object previous = get(key);
			removed.remove(key);
			locals.put(key, value);
			return previous;
		}

		@Override
		public Object remove(Object key) {
			Object previous = get(key);
			locals.remove(key);
			removed.add(key);
			return previous;
		}

		@Override
		public void clear() {
			locals.clear();
			removed.clear();
			cleared = true;
		}

		private Set<Object> visibleKeys() {
			Set<Object> keys = new LinkedHashSet<>();
			if (!cleared) keys.addAll(source.keySet());
			keys.removeAll(removed);
			keys.addAll(locals.keySet());
			return keys;
		}

		@Override
		public Set<Entry<Object, Object>> entrySet() {
			return new AbstractSet<>() {
				@Override
				public int size() { return visibleKeys().size(); }
				@Override
				public void clear() { LoopContext.this.clear(); }
				@Override
				public Iterator<Entry<Object, Object>> iterator() {
					Iterator<Object> keys = new ArrayList<>(visibleKeys()).iterator();
					return new Iterator<>() {
						private Object current;
						private boolean canRemove;
						@Override
						public boolean hasNext() { return keys.hasNext(); }
						@Override
						public Entry<Object, Object> next() {
							Object key = keys.next();
							current = key;
							canRemove = true;
							return new SimpleEntry<>(key, LoopContext.this.get(key)) {
								@Override
								public Object setValue(Object value) {
									Object previous = LoopContext.this.put(key, value);
									super.setValue(value);
									return previous;
								}
							};
						}
						@Override
						public void remove() {
							if (!canRemove) throw new IllegalStateException();
							LoopContext.this.remove(current);
							canRemove = false;
						}
					};
				}
			};
		}
	}

	@Override
	public boolean eval(Object context, SqlParameterCollection sqlParameters) {
		Object val = evalExpression(this.getExpression(), context);
		if (context instanceof ParametersContext) {
			execLoop((ParametersContext) context, sqlParameters, val);
		} else if (context instanceof Map) {
			execLoop((Map<?, ?>) context, sqlParameters, val);
		} else if (context != null) {
			Map<String, Object> map = SimpleBeanUtils.getInstance(context.getClass()).toMap(context);
			execLoop(map, sqlParameters, val);
		} else {
			Map<String, Object> map = map();
			execLoop(map, sqlParameters, val);
		}
		return true;
	}

	private void execLoop(final ParametersContext context, final SqlParameterCollection sqlParameters, Object val) {
		final String variableName = this.getVariableName();
		final String indexName = getIndexName();
		final ParametersContext copyContext = context.clone();
		AbstractIterator<Object> itr = new AbstractIterator<Object>(this.getEvaluator()) {
			@Override
			protected void handle(Object obj, int index) {
				copyContext.put(indexName, index);
				copyContext.put(variableName, obj);
				evalChilds(copyContext, sqlParameters);
			}

			@Override
			protected void executeFinally() {
				copyContext.remove(variableName);
				copyContext.remove(indexName);
			}
		};
		try {
			itr.execute(val);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void execLoop(final Map<?, ?> context, final SqlParameterCollection sqlParameters, Object val) {
		final String variableName = this.getVariableName();
		final String indexName = getIndexName();
		final Map<Object, Object> copyContext = new LoopContext(context);
		AbstractIterator<Object> itr = new AbstractIterator<Object>(this.getEvaluator()) {
			@Override
			protected void handle(Object obj, int index) {
				copyContext.put(indexName, index);
				copyContext.put(variableName, obj);
				evalChilds(copyContext, sqlParameters);
			}

			@Override
			protected void executeFinally() {
				copyContext.remove(variableName);
				copyContext.remove(indexName);
			}
		};
		try {
			itr.execute(val);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected String getIndexName() {
		return this.getVariableName() + "_index";
	}

	/**
	 * @return the variableName
	 */
	public String getVariableName() {
		return variableName;
	}

	/**
	 * @param variableName the variableName to set
	 */
	protected void setVariableName(String variableName) {
		this.variableName = variableName;
		this.setParameterDefinition(new ParameterDefinition(variableName));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public ForNode clone() {
		return (ForNode) super.clone();
	}
}
