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

package com.sqlapp.data.schemas;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.sqlapp.data.schemas.DbInfo.DiffValue;
import com.sqlapp.util.AbstractIterator;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DeltaUtils;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.ToStringBuilder;

/**
 * オブジェクトの変更状態
 * 
 * @author 竜夫
 * 
 */
public class DbObjectPropertyDifference extends AbstractDifference<Object> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6634332562149007807L;

	protected DbObjectPropertyDifference(String propertyName, DbCommonObject<?> original, DbCommonObject<?> target) {
		this(propertyName, original, target, new EqualsHandler());
	}

	protected DbObjectPropertyDifference(DbCommonObject<?> original, DbCommonObject<?> target) {
		this(original, target, new EqualsHandler());
	}

	protected DbObjectPropertyDifference(String propertyName, DbCommonObject<?> original, DbCommonObject<?> target,
			EqualsHandler equalsHandler) {
		this(propertyName, original, target, equalsHandler, false);
	}

	protected DbObjectPropertyDifference(String propertyName, DbCommonObject<?> original, DbCommonObject<?> target,
			EqualsHandler equalsHandler, boolean skipDiff) {
		super(propertyName, original, target, equalsHandler);
		if (!skipDiff) {
			diff();
		}
	}

	protected DbObjectPropertyDifference(DbCommonObject<?> original, DbCommonObject<?> target,
			EqualsHandler equalsHandler) {
		this(null, original, target, equalsHandler, false);
	}

	protected DbObjectPropertyDifference(final DbCommonObject<?> originalParent, final Object original,
			final DbCommonObject<?> targetParent, final Object target, final EqualsHandler equalsHandler,
			boolean skipDiff) {
		super(null, originalParent, original, targetParent, target, equalsHandler);
		if (!skipDiff) {
			diff();
		}
	}

	protected DbObjectPropertyDifference(String propertyName, final DbCommonObject<?> originalParent,
			final Object original, final DbCommonObject<?> targetParent, final Object target, boolean skipDiff) {
		super(propertyName, originalParent, original, targetParent, target, new EqualsHandler());
		if (!skipDiff) {
			diff();
		}
	}

	protected DbObjectPropertyDifference(String propertyName, final DbCommonObject<?> originalParent,
			final Object original, final DbCommonObject<?> targetParent, final Object target,
			final EqualsHandler equalsHandler, boolean skipDiff) {
		super(propertyName, originalParent, original, targetParent, target, equalsHandler);
		if (!skipDiff) {
			diff();
		}
	}

	protected DbObjectPropertyDifference(final DbCommonObject<?> originalParent, final Object original,
			final DbCommonObject<?> targetParent, final Object target, final EqualsHandler equalsHandler) {
		this(null, originalParent, original, targetParent, target, equalsHandler, true);
	}

	@Override
	public DbObjectPropertyDifference reverse() {
		DbObjectPropertyDifference reverse = new DbObjectPropertyDifference(this.getPropertyName(),
				this.getTargetParent(), this.getTarget(), this.getOriginalParent(), this.getOriginal(),
				this.getEqualsHandler(), false);
		return reverse;
	}

	@Override
	protected void diff() {
	}

	/**
	 * DIFFを行うequalsのハンドラー
	 * 
	 * @author satoh
	 * 
	 */
	protected static class DifferenceEqualsHandler extends EqualsHandler {

		private DbObjectPropertyDifference dbObjectDifference = null;

		private final EqualsHandler equalsHandler;

		protected DifferenceEqualsHandler(DbObjectPropertyDifference dbObjectDifference, EqualsHandler equalsHandler) {
			this.dbObjectDifference = dbObjectDifference;
			this.equalsHandler = equalsHandler;
		}

		@Override
		protected boolean referenceEquals(Object object1, Object object2) {
			return this.equalsHandler.referenceEquals(object1, object2);
		}

		@Override
		protected boolean valueEquals(String propertyName, Object object1, Object object2, Object value1, Object value2,
				BooleanSupplier p) {
			boolean result = this.equalsHandler.valueEquals(propertyName, object1, object2, value1, value2, p);
			return equalsInternal(propertyName, result, object1, object2, value1, value2);
		}

		protected boolean equalsInternal(String propertyName, boolean result, Object object1, Object object2,
				Object value1, Object value2) {
			DbObjectPropertyDifference diff = new DbObjectPropertyDifference(propertyName, (DbCommonObject<?>) object1,
					value1, (DbCommonObject<?>) object2, value2, equalsHandler, result);
			diff.setParentDifference(dbObjectDifference);
			if (result) {
				diff.setState(State.Unchanged);
			} else {
				diff.setState(State.Modified);
			}
			return true;
		}

		@Override
		protected boolean equalsResult(Object object1, Object object2) {
			return this.equalsHandler.equalsResult(object1, object2);
		}

		@Override
		public DifferenceEqualsHandler clone() {
			return (DifferenceEqualsHandler) super.clone();
		}
	}

	@Override
	protected void toString(ToStringBuilder builder) {
		if (this.getState() != State.Modified) {
			return;
		}
		if (this.getOriginal() instanceof List<?>) {
			buildDiff(builder, (List<?>) this.getOriginal(), (List<?>) this.getTarget());
		} else if (this.getOriginal() instanceof DbInfo && this.getTarget() instanceof DbInfo) {
			buildDiff(builder, (DbInfo) this.getOriginal(), (DbInfo) this.getTarget());
		} else if (this.getOriginal() instanceof byte[] && this.getTarget() instanceof byte[]) {
			buildDiff(builder, (byte[]) this.getOriginal(), (byte[]) this.getTarget());
		} else if (this.getOriginalParent() instanceof Row && this.getTargetParent() instanceof Row
				&& Row.VALUES.equals(this.getPropertyName())) {
			buildDiffRowValue(builder, (Row) this.getOriginalParent(), (Row) this.getTargetParent());
		} else {
			buildDiff(builder, (Object) this.getOriginal(), this.getTarget());
		}
	}

	protected List<DbObjectPropertyDifference> toList() {
		List<DbObjectPropertyDifference> list = CommonUtils.list();
		if (this.getOriginal() instanceof DbInfo || this.getTarget() instanceof DbInfo) {
			DbInfo original = (DbInfo) this.getOriginal();
			DbInfo target = (DbInfo) this.getTarget();
			Map<String, DiffValue> diffValueMap = null;
			boolean reverse = false;
			if (original == null) {
				if (target == null) {
					list.add(this);
					return list;
				} else {
					diffValueMap = target.getDiffDbInfo(original);
					reverse = true;
				}
			} else {
				diffValueMap = original.getDiffDbInfo(target);
			}
			for (Map.Entry<String, DiffValue> entry : diffValueMap.entrySet()) {
				DiffValue diffValue = entry.getValue();
				DbObjectPropertyDifference diff = new DbObjectPropertyDifference(entry.getKey(),
						this.getOriginalParent(), diffValue.getOriginal(), this.getTargetParent(),
						diffValue.getTarget(), true);
				if (reverse) {
					diff.setState(diffValue.getState().reverse());
				} else {
					diff.setState(diffValue.getState());
				}
				list.add(diff);
			}
		} else {
			list.add(this);
		}
		return list;
	}

	private void buildDiffRowValue(ToStringBuilder builder, Row val1, Row val2) {
		Row oRow1 = val1;
		Row oRow2 = val2;
		buildDiffForKeyMap(builder, oRow1.getValuesAsMapWithKey(), oRow2.getValuesAsMapWithKey());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void buildDiff(ToStringBuilder builder, List val1, List val2) {
		String space = CommonUtils.getString("\t", this.getLevel());
		StringBuilder bld = new StringBuilder("(");
		Patch<?> patch = DiffUtils.diff(val1, val2);
		List list1 = (List<?>) val1;
		List list2 = (List<?>) val2;
		int size1 = list1.size();
		int size2 = list2.size();
		boolean isString = false;
		if (!CommonUtils.isEmpty(list1)) {
			for (Object obj : list1) {
				if (obj instanceof String) {
					isString = true;
					break;
				}
			}
		} else if (!CommonUtils.isEmpty(list2)) {
			for (Object obj : list2) {
				if (obj instanceof String) {
					isString = true;
					break;
				}
			}
		}
		int max = Math.max(size1, size2);
		for (AbstractDelta<?> delta : patch.getDeltas()) {
			bld.append("\n");
			bld.append(space);
			// bld.append(toTabString(DeltaUtils.toString(delta), space));
			if (isString) {
				bld.append(toTabString(DeltaUtils.toStringLineNumber(delta, max), space));
			} else {
				bld.append(toTabString(DeltaUtils.toStringLine(delta), space));
			}
		}
		bld.append("\n");
		bld.append(space);
		bld.append(")");
		builder.add(this.getPropertyName(), bld);
	}

	private void buildDiff(ToStringBuilder builder, String val1, String val2) {
		if (val1 != null && val2 != null) {
			List<String> txt1 = CommonUtils.list(val1.split("\n"));
			List<String> txt2 = CommonUtils.list(val2.split("\n"));
			buildDiff(builder, txt1, txt2);
		} else {
			buildDiff(builder, (Object) this.getOriginal(), this.getTarget());
		}
	}

	private void buildDiff(ToStringBuilder builder, Object val1, Object val2) {
		if (val1 != null && val2 != null && val1.getClass().isArray() && val2.getClass().isArray()) {
			buildDiffArray(builder, this.getOriginal(), this.getTarget());
		} else {
			builder.add(this.getPropertyName(), "(" + this.getOriginal() + " -> " + this.getTarget() + ")");
		}
	}

	private void buildDiff(ToStringBuilder builder, byte[] val1, byte[] val2) {
		if (this.getOriginalParent() instanceof AssemblyFile && this.getTargetParent() instanceof AssemblyFile) {
			AssemblyFile org = (AssemblyFile) this.getOriginalParent();
			AssemblyFile tgt = (AssemblyFile) this.getTargetParent();
			if (org.isSourceFile() && tgt.isSourceFile()
					&& SchemaProperties.CONTENT.getLabel().equals(this.getPropertyName())) {
				try {
					buildDiff(builder, org.getContentAsSource(), tgt.getContentAsSource());
				} catch (UnsupportedEncodingException e) {
					throw new RuntimeException(e);
				}
				return;
			}
		}
		builder.add(this.getPropertyName() + ".length",
				"(" + length((byte[]) this.getOriginal()) + " -> " + length((byte[]) this.getTarget()) + ")");
	}

	private void buildDiffArray(ToStringBuilder builder, Object val1, Object val2) {
		final List<Object> list1 = CommonUtils.list();
		final List<Object> list2 = CommonUtils.list();
		AbstractIterator<Object> itr1 = new AbstractIterator<Object>() {
			@Override
			protected void handle(Object obj, int index) throws Exception {
				list1.add(obj);
			}
		};
		AbstractIterator<Object> itr2 = new AbstractIterator<Object>() {
			@Override
			protected void handle(Object obj, int index) throws Exception {
				list2.add(obj);
			}
		};
		try {
			itr1.execute(val1);
			itr2.execute(val2);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		buildDiff(builder, list1, list2);
	}

	private int length(byte[] bytes) {
		return bytes == null ? 0 : bytes.length;
	}

	private void buildDiff(ToStringBuilder builder, DbInfo map1, DbInfo map2) {
		StringBuilder bld = new StringBuilder("{");
		SeparatedStringBuilder sepChildBuilder = new SeparatedStringBuilder(", ");
		Set<String> props = CommonUtils.linkedSet();
		if (map1 != null) {
			props.addAll(map1.keySet());
		}
		if (map2 != null) {
			props.addAll(map2.keySet());
		}
		for (String prop : props) {
			String value1 = null;
			if (map1 != null) {
				value1 = map1.get(prop);
			}
			String value2 = null;
			if (map2 != null) {
				value2 = map2.get(prop);
			}
			if (!CommonUtils.eq(value1, value2)) {
				sepChildBuilder.add(prop + "=(" + value1 + " -> " + value2 + ")");
			}
		}
		bld.append(sepChildBuilder.toString());
		if (bld.length() > 1) {
			bld.append("}");
			builder.add(this.getPropertyName(), bld);
		}
	}

	private void buildDiffForKeyMap(ToStringBuilder builder, Map<String, Object> map1, Map<String, Object> map2) {
		StringBuilder bld = new StringBuilder();
		if (!CommonUtils.eq(map1, map2)) {
			SeparatedStringBuilder sepChildBuilder = new SeparatedStringBuilder(", ");
			sepChildBuilder.setStart("[");
			sepChildBuilder.setEnd("]");
			Set<String> props = CommonUtils.linkedSet();
			props.addAll(map1.keySet());
			props.addAll(map2.keySet());
			for (String prop : props) {
				Object value1 = map1.get(prop);
				Object value2 = map2.get(prop);
				if (!CommonUtils.eq(value1, value2)) {
					sepChildBuilder.add(prop + "=(" + value1 + " -> " + value2 + ")");
				} else {
					if (prop.endsWith("(PK)") || prop.endsWith("(UK)")) {
						sepChildBuilder.add(prop + "=" + value1);
					}
				}
			}
			bld.append(sepChildBuilder.toString());
		}
		if (bld.length() > 1) {
			builder.add("C:" + this.getPropertyName(), bld);
		}
	}

	private String toTabString(String text, String space) {
		StringBuilder builder = new StringBuilder();
		String[] values = text.split("\n");
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				builder.append("\n");
				builder.append(space);
			}
			builder.append(values[i]);
		}
		return builder.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Difference<?> o) {
		if (o instanceof DbObjectDifference) {
			return -1;
		}
		if (o instanceof DbObjectDifferenceCollection) {
			return -1;
		}
		DbObjectPropertyDifference dop = (DbObjectPropertyDifference) o;
		if (this.getOriginal() instanceof List<?> || this.getTarget() instanceof List<?>) {
			if (dop.getOriginal() instanceof List<?> || dop.getTarget() instanceof List<?>) {
				return 0;
			} else {
				return 1;
			}
		} else {
			if (dop.getOriginal() instanceof List<?> || dop.getTarget() instanceof List<?>) {
				return -1;
			} else {
				return 0;
			}
		}
	}

	@Override
	public void removeRecursive(BiPredicate<String, Difference<?>> predicate) {
	}
}
