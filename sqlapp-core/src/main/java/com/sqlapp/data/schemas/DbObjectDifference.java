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

package com.sqlapp.data.schemas;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.properties.NameGetter;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.ToStringBuilder;

/**
 * オブジェクトの変更状態
 * 
 * @author 竜夫
 * 
 */
public class DbObjectDifference extends AbstractDifference<DbObject<?>> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6634332562149007807L;
	/**
	 * 変更されたプロパティ
	 */
	private Map<String, Difference<?>> properties = CommonUtils.caseInsensitiveTreeMap();

	protected DbObjectDifference(DbObject<?> original, DbObject<?> target) {
		this(original, target, new EqualsHandler());
	}

	protected DbObjectDifference(String propertyName, DbObject<?> original,
			DbObject<?> target, EqualsHandler equalsHandler) {
		this(propertyName, original, target, equalsHandler, false);
	}

	protected DbObjectDifference(String propertyName, DbObject<?> original,
			DbObject<?> target, EqualsHandler equalsHandler, boolean skipDiff) {
		super(propertyName, original, target, equalsHandler);
		if (!skipDiff) {
			diff();
		}
	}

	protected DbObjectDifference(DbObject<?> original, DbObject<?> target,
			EqualsHandler equalsHandler) {
		this(null, original, target, equalsHandler, false);
	}

	protected DbObjectDifference(final DbCommonObject<?> originalParent,
			final DbObject<?> original, final DbCommonObject<?> targetParent,
			final DbObject<?> target, final EqualsHandler equalsHandler,
			boolean skipDiff) {
		super(null, originalParent, original, targetParent, target,
				equalsHandler);
		if (!skipDiff) {
			diff();
		}
	}

	protected DbObjectDifference(String propertyName,
			final DbCommonObject<?> originalParent, final DbObject<?> original,
			final DbCommonObject<?> targetParent, final DbObject<?> target,
			final EqualsHandler equalsHandler, boolean skipDiff) {
		super(propertyName, originalParent, original, targetParent, target,
				equalsHandler);
		if (!skipDiff) {
			diff();
		}
	}

	@Override
	public DbObjectDifference reverse(){
		DbObjectDifference reverse=new DbObjectDifference(this.getPropertyName(), this.getTargetParent(), this.getTarget(), this.getOriginalParent(), this.getOriginal(), this.getEqualsHandler(), false);
		return reverse;
	}
	
	@Override
	protected void diff() {
		if (!isDbObject(this.getOriginal(), this.getTarget())) {
			return;
		}
		if (this.getOriginal() == null||this.getTarget() == null) {
			this.setState(State.getState(this.getOriginal(), this.getTarget()));
		} else {
			((DbCommonObject<?>) this.getOriginal()).equals(
					this.getTarget(),
					new DifferenceEqualsHandler(this, this
							.getEqualsHandler()));
			for (Map.Entry<String, Difference<?>> entry : getProperties()
					.entrySet()) {
				if (entry.getValue().getState().isChanged()) {
					this.setState(State.Modified);
					break;
				}
			}
		}
	}

	/**
	 * プロパティのマップを返します
	 * 
	 * @return プロパティのマップ
	 */
	public Map<String, Difference<?>> getProperties() {
		return properties;
	}

	/**
	 * 指定したステータスのプロパティのマップを返します
	 * 
	 * @param states
	 *            ステータス
	 * @return 指定したステータスのプロパティのマップ
	 */
	public Map<String, Difference<?>> getProperties(State... states) {
		Set<State> set = CommonUtils.set(states);
		return getPropertiesInternal((state->set.contains(state)));
	}

	/**
	 * 変更されたプロパティのマップを返します
	 * 
	 * @return 変更されたプロパティのマップ
	 */
	public Map<String, Difference<?>> getChangedProperties() {
		return getPropertiesInternal((state->state.isChanged()));
	}
	
	/**
	 * 変更されたプロパティのマップを返します
	 * 
	 * @return 変更されたプロパティのマップ
	 */
	private Map<String, Difference<?>> getPropertiesInternal(Predicate<State> p) {
		Map<String, Difference<?>> result = CommonUtils.caseInsensitiveTreeMap();
		for (Map.Entry<String, Difference<?>> entry : properties.entrySet()) {
			if (p.test(entry.getValue().getState())){
				result.put(entry.getKey(), entry.getValue());
			}
		}
		return result;
	}

	/**
	 * 指定したステータスのプロパティのマップを返します
	 * 
	 * @param states
	 *            ステータス
	 * @return 指定したステータスのプロパティのマップ
	 */
	public Map<String, Difference<?>> getProperties(Dialect dialect, State... states) {
		Set<State> set = CommonUtils.set(states);
		return getPropertiesInternal(dialect, (state->set.contains(state)));
	}
	
	/**
	 * 変更されたプロパティのマップを返します
	 * 
	 * @return 変更されたプロパティのマップ
	 */
	public Map<String, Difference<?>> getChangedProperties(Dialect dialect) {
		return getPropertiesInternal(dialect, (state->state.isChanged()));
	}
	
	/**
	 * 変更されたプロパティのマップを返します
	 * 
	 * @return 変更されたプロパティのマップ
	 */
	private Map<String, Difference<?>> getPropertiesInternal(Dialect dialect, Predicate<State> p) {
		Map<String, Difference<?>> result = CommonUtils.caseInsensitiveTreeMap();
		for (Map.Entry<String, Difference<?>> entry : properties.entrySet()) {
			if (entry.getValue().getState().isChanged()) {
				if (entry.getValue() instanceof DbObjectPropertyDifference){
					if (SchemaProperties.SPECIFICS.getLabel().equals(entry.getKey())||SchemaProperties.STATISTICS.getLabel().equals(entry.getKey())){
						List<DbObjectPropertyDifference> props=((DbObjectPropertyDifference)entry.getValue()).toList();
						for(DbObjectPropertyDifference prop:props){
							if (p.test(prop.getState())){
								result.put(prop.getPropertyName(), prop);
								continue;
							}
						}
					} else{
						result.put(entry.getKey(), entry.getValue());
					}
				} else{
					result.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return result;
	}
	
	/**
	 * 変更されたプロパティを取得します
	 * @param propertyName
	 * @param clazz
	 */
	public List<DbObjectPropertyDifference> findModifiedProperties(Dialect dialect, String propertyName, Class<? extends DbObject<?>> clazz){
		List<DbObjectPropertyDifference> result=CommonUtils.list();
		if (isTargetClass(this.getOriginal(), clazz)||isTargetClass(this.getTarget(), clazz)){
			Map<String,Difference<?>> differeces=this.getProperties();
			for(Map.Entry<String, Difference<?>> entry:differeces.entrySet()){
				if (entry.getValue() instanceof DbObjectPropertyDifference){
					if (SchemaProperties.SPECIFICS.getLabel().equals(entry.getKey())||SchemaProperties.STATISTICS.getLabel().equals(entry.getKey())){
						List<DbObjectPropertyDifference> props=((DbObjectPropertyDifference)entry.getValue()).toList();
						for(DbObjectPropertyDifference prop:props){
							if (propertyName.equals(prop.getPropertyName())){
								if (prop.getState().isChanged()){
									result.add(prop);
									continue;
								}
							}
						}
					} else{
						if (propertyName.equals(entry.getKey())){
							if (entry.getValue().getState().isChanged()){
								result.add((DbObjectPropertyDifference)entry.getValue());
								continue;
							}
						}
					}
				}
			}
		} else{
			Map<String,Difference<?>> differeces=this.getProperties();
			for(Map.Entry<String, Difference<?>> entry:differeces.entrySet()){
				if (entry.getValue() instanceof DbObjectDifferenceCollection){
					DbObjectDifferenceCollection diffCollection=(DbObjectDifferenceCollection)entry.getValue();
					List<DbObjectPropertyDifference> childResult=diffCollection.findModifiedProperties(dialect, propertyName, clazz);
					result.addAll(childResult);
				} else if (entry.getValue() instanceof DbObjectDifference){
					DbObjectDifference diff=(DbObjectDifference)entry.getValue();
					result.addAll(diff.findModifiedProperties(dialect, propertyName, clazz));
				}
			}
		}
		return result;
	}
	

	private boolean isTargetClass(Object obj, Class<? extends DbObject<?>> clazz){
		if (obj==null){
			return false;
		}
		return clazz.isAssignableFrom(obj.getClass());
	}
	
	/**
	 * DIFFを行うequalsのハンドラー
	 * 
	 * @author satoh
	 * 
	 */
	protected static class DifferenceEqualsHandler extends EqualsHandler {

		private DbObjectDifference dbObjectDifference = null;

		private final EqualsHandler equalsHandler;

		protected DifferenceEqualsHandler(
				DbObjectDifference dbObjectDifference,
				EqualsHandler equalsHandler) {
			this.dbObjectDifference = dbObjectDifference;
			this.equalsHandler = equalsHandler;
		}

		@Override
		protected boolean referenceEquals(Object object1, Object object2) {
			return this.equalsHandler.referenceEquals(object1, object2);
		}

		@Override
		protected boolean valueEquals(String propertyName, Object object1,
				Object object2, Object value1, Object value2, BooleanSupplier p) {
			boolean result = this.equalsHandler.valueEquals(propertyName, object1,
					object2, value1, value2, p);
			return equalsInternal(propertyName, result, object1, object2,
					value1, value2);
		}

		protected boolean equalsInternal(String propertyName, boolean result,
				Object object1, Object object2, Object value1, Object value2) {
			if (isDbObjectCollection(value1, value2)) {
				DbObjectDifferenceCollection diff = new DbObjectDifferenceCollection(
						propertyName, (DbObjectCollection<?>) value1,
						(DbObjectCollection<?>) value2, equalsHandler, result);
				diff.setParentDifference(dbObjectDifference);
				dbObjectDifference.getProperties().put(propertyName, diff);
			} else if (isDbObject(value1, value2)) {
				DbObjectDifference diff = new DbObjectDifference(propertyName,
						(DbCommonObject<?>) object1, (DbObject<?>)value1,
						(DbCommonObject<?>) object2, (DbObject<?>)value2, equalsHandler,
						result);
				diff.setParentDifference(dbObjectDifference);
				dbObjectDifference.getProperties().put(propertyName, diff);
			} else {
				DbObjectPropertyDifference diff = new DbObjectPropertyDifference(
						propertyName, (DbCommonObject<?>) object1, value1,
						(DbCommonObject<?>) object2, value2, equalsHandler,
						result);
				diff.setParentDifference(dbObjectDifference);
				if (result) {
					diff.setState(State.Unchanged);
				} else {
					diff.setState(State.Modified);
				}
				dbObjectDifference.getProperties().put(propertyName, diff);
			}
			return true;
		}

		@Override
		protected boolean equalsResult(Object object1, Object object2) {
			return this.equalsHandler.equalsResult(object1, object2);
		}
		
		@Override
		public DifferenceEqualsHandler clone(){
			return (DifferenceEqualsHandler)super.clone();
		}
	}

	@Override
	protected void toString(ToStringBuilder builder) {
		if (this.getState() == State.Deleted) {
			builder.add(format(this.getState(), this.getOriginal()));
		} else if (this.getState() == State.Added) {
			builder.add(format(this.getState(), this.getTarget()));
		} else if (this.getState() == State.Modified) {
			toStringDetail(builder);
		}
		//
	}

	protected void toStringDetail(ToStringBuilder builder) {
		SeparatedStringBuilder sepBuilder = new SeparatedStringBuilder(", ");
		Map<String, Difference<?>> props = this.getChangedProperties();
//		Map.Entry<String, Difference> firstDiff = CommonUtils.first(props);
//		if (props.size() == 1
//				&& (firstDiff.getValue() instanceof DbObjectDifference || firstDiff
//						.getValue() instanceof DbObjectDifferenceCollection)) {
//			sepBuilder.setStart(getStateText(null) + " "
//					+ this.getSimpleName(this.getOriginal()));
//		} else {
			sepBuilder.setStart(getStateText(this.getState()) + ":"
					+ this.getSimpleName(this.getOriginal()));
//		}
		Difference<?> nameDiff = null;
		if (this.getOriginal() instanceof NameGetter) {
			nameDiff = getProperties().get(SchemaProperties.NAME.getLabel());
		}else if (this.getOriginal() instanceof PartitionParent) {
			nameDiff = getProperties().get(SchemaProperties.TABLE_NAME.getLabel());
		}
		SeparatedStringBuilder sepChildBuilder = new SeparatedStringBuilder(
				", ");
		sepChildBuilder.setStart("[").setEnd("]");
//		if (!props.containsKey(SchemaProperties.TABLE_NAME.getLabel()) && nameDiff != null) {
//			if (nameDiff.getState().isChanged()) {
//				sepChildBuilder.add(nameDiff);
//			} else {
//				if (nameDiff.getOriginal() != null) {
//					sepChildBuilder.add("tableName=" + nameDiff.getOriginal());
//				}
//			}
//		}else 
			if (!props.containsKey(SchemaProperties.NAME.getLabel()) && nameDiff != null) {
			if (nameDiff.getState().isChanged()) {
				sepChildBuilder.add(nameDiff);
			} else {
				if (nameDiff.getOriginal() != null) {
					sepChildBuilder.add("name=" + nameDiff.getOriginal());
				}
			}
		}
		int indentSize = this.getLevel();
		List<Map.Entry<String, Difference<?>>> list=sort(props);
		for (Map.Entry<String, Difference<?>> entry : list) {
			Object value = entry.getValue();
			sepChildBuilder.add(value);
		}
		if (props.size() > 0) {
			String val = sepChildBuilder.toString();
			sepBuilder.add(val);
		}
		String indent = CommonUtils.getString('\t', indentSize);
		builder.add("\n" + indent + sepBuilder.toString());
	}
	
	private List<Map.Entry<String, Difference<?>>> sort(Map<String, Difference<?>> map){
		List<Map.Entry<String, Difference<?>>> list=map.entrySet().stream().collect(Collectors.toList());
		Collections.sort(list, new Comparator<Map.Entry<String, Difference<?>>>(){
			@Override
			public int compare(Entry<String, Difference<?>> o1, Entry<String, Difference<?>> o2) {
				Integer val1=ORDER_MAP.get(o1.getKey());
				Integer val2=ORDER_MAP.get(o2.getKey());
				if (val1==null){
					if (val2==null){
						return 0;
					} else{
						if (val2.intValue()>ORDER_LEVEL){
							return -1;
						} else{
							return 1;
						}
					}
				} else{
					if (val2==null){
						if (val1.intValue()>ORDER_LEVEL){
							return 1;
						} else{
							return -1;
						}
					} else{
						return val1.compareTo(val2);
					}
				}
			}});
		return list;
	}

	private static Map<String,Integer> ORDER_MAP=CommonUtils.map();
	
	private static int ORDER_LEVEL=Integer.MAX_VALUE/10;
	
	static{
		int i=0;
		ORDER_MAP.put(SchemaProperties.CATALOG_NAME.getLabel(), i++);
		ORDER_MAP.put(SchemaProperties.SCHEMA_NAME.getLabel(), i++);
		ORDER_MAP.put(SchemaProperties.SPECIFIC_NAME.getLabel(), i++);
		ORDER_MAP.put(SchemaProperties.DISPLAY_NAME.getLabel(), i++);
		ORDER_MAP.put(SchemaProperties.NAME.getLabel(), i++);
		ORDER_MAP.put(SchemaProperties.ORDINAL.getLabel(), i++);
		i=ORDER_LEVEL;
		ORDER_MAP.put(SchemaProperties.CREATED_AT.getLabel(), i++);
		ORDER_MAP.put(SchemaProperties.LAST_ALTERED_AT.getLabel(), i++);
		ORDER_MAP.put(SchemaProperties.DISPLAY_REMARKS.getLabel(), i++);
		ORDER_MAP.put(SchemaProperties.REMARKS.getLabel(), i++);
		ORDER_MAP.put(SchemaProperties.STATISTICS.getLabel(), i++);
		ORDER_MAP.put(SchemaProperties.SPECIFICS.getLabel(), i++);
	}

	@Override
	public int compareTo(Difference<?> o) {
		if (o instanceof DbObjectPropertyDifference) {
			return 1;
		}
		if (o instanceof DbObjectDifferenceCollection) {
			return -1;
		}
		return 0;
	}

	@Override
	public void removeRecursive(BiPredicate<String, Difference<?>> predicate) {
		Set<String> set=CommonUtils.set();
		for(String key:properties.keySet()){
			Difference<?> difference=properties.get(key);
			difference.removeRecursive(predicate);
			if(predicate.test(key, difference)){
				set.add(key);
			}
		}
		for(String key:set){
			properties.remove(key);
		}
		if (this.properties.isEmpty()){
			this.setState(State.Unchanged);
		}
	}
	
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof DbObjectDifference)){
			return false;
		}
		DbObjectDifference cst=(DbObjectDifference)obj;
		if (!this.getProperties().equals(cst.getProperties())){
			return false;
		}
		return true;
	}
}
