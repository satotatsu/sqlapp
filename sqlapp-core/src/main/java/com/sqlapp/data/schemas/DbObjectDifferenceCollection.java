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

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SeparatedStringBuilder;
import com.sqlapp.util.ToStringBuilder;

/**
 * オブジェクトコレクションの変更状態
 * 
 * @author 竜夫
 * 
 */
public class DbObjectDifferenceCollection extends
		AbstractDifference<DbObjectCollection<?>> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 6634332562149007807L;

	/**
	 * 子供の比較オブジェクト
	 */
	private List<DbObjectDifference> list = CommonUtils.list();

	protected DbObjectDifferenceCollection(DbObjectCollection<?> original,
			DbObjectCollection<?> target) {
		this(null, original, target, new EqualsHandler());
	}

	protected DbObjectDifferenceCollection(String propertyName,
			DbObjectCollection<?> original, DbObjectCollection<?> target) {
		this(propertyName, original, target, new EqualsHandler());
	}

	protected DbObjectDifferenceCollection(String propertyName,
			DbObjectCollection<?> original, DbObjectCollection<?> target,
			EqualsHandler equalsHandler, boolean skipDiff) {
		super(propertyName, original, target, equalsHandler);
		if (!skipDiff) {
			diff();
		}
	}

	protected DbObjectDifferenceCollection(DbObjectCollection<?> original,
			DbObjectCollection<?> target, EqualsHandler equalsHandler) {
		this(null, original, target, equalsHandler, false);
	}

	protected DbObjectDifferenceCollection(String propertyName,
			DbObjectCollection<?> original, DbObjectCollection<?> target,
			EqualsHandler equalsHandler) {
		this(propertyName, original, target, equalsHandler, false);
	}


	@Override
	public DbObjectDifferenceCollection reverse(){
		DbObjectDifferenceCollection reverse=new DbObjectDifferenceCollection(this.getPropertyName(), this.getTarget(), this.getOriginal(), this.getEqualsHandler(), true);
		return reverse;
	}
	
	/**
	 * 変更されたプロパティを取得します
	 * @param propertyName
	 * @param clazz
	 */
	public List<DbObjectPropertyDifference> findModifiedProperties(Dialect dialect, String propertyName, Class<? extends DbObject<?>> clazz){
		List<DbObjectPropertyDifference> result=CommonUtils.list();
		for(DbObjectDifference diff:this.getList()){
			List<DbObjectPropertyDifference> childResult=diff.findModifiedProperties(dialect, propertyName, clazz);
			result.addAll(childResult);
		}
		return result;
	}
	
	@Override
	protected void diff() {
		if (!isDbObject(this.getOriginal(), this.getTarget())) {
			return;
		}
		if (isColumns(this.getOriginal(), this.getTarget())) {
			diffColumns();
			return;
		}
		diffObjects();
	}

	@SuppressWarnings("unchecked")
	protected void diffObjects() {
		DbObjectDiff<DbObject<?>> diff = new DbObjectDiff<DbObject<?>>(
				(List<DbObject<?>>) this.getOriginal(),
				(List<DbObject<?>>) this.getTarget(), this.getEqualsHandler());
		int osize = this.getOriginal().size();
		int tsize = this.getTarget().size();
		int i = 0, j = 0;
		while (i < osize || j < tsize) {
			DbObject<?> oobj = null;
			DbObject<?> tobj = null;
			while(i < osize){
				oobj = this.getOriginal().get(i);
				i++;
				if (diff.getLcs1().containsKey(i-1)){
					break;
				}
				DbObjectDifference dbObjectDifference = new DbObjectDifference(
						oobj, null, this.getEqualsHandler());
				addDbObjectDifference(dbObjectDifference);
				oobj=null;
			}
			while(j < tsize){
				if (oobj!=null){
					if (this.getTarget() instanceof AbstractNamedObjectCollection){
						tobj=((AbstractNamedObjectCollection<?>)this.getTarget()).find((AbstractNamedObject<?>)oobj);
					}
					if (tobj!=null){
						break;
					}
				}
				tobj = this.getTarget().get(j);
				j++;
				if (diff.getLcs2().containsKey(j-1)){
					break;
				}
				DbObjectDifference dbObjectDifference = new DbObjectDifference(
						null, tobj, this.getEqualsHandler());
				addDbObjectDifference(dbObjectDifference);
			}
			if (oobj!=null&&tobj != null){
				DbObjectDifference dbObjectDifference = new DbObjectDifference(
						oobj, tobj, this.getEqualsHandler());
				addDbObjectDifference(dbObjectDifference);
				continue;
			}
		}
		this.setState(getState(list));
	}
	
	@SuppressWarnings("unchecked")
	protected void diffColumns() {
		if (!isColumns(this.getOriginal(), this.getTarget())) {
			return;
		}
		DbObjectDiff<AbstractColumn<?>> diff = new DbObjectDiff<AbstractColumn<?>>(
				(List<AbstractColumn<?>>) this.getOriginal(),
				(List<AbstractColumn<?>>) this.getTarget(), this.getEqualsHandler());
		int osize = this.getOriginal().size();
		int tsize = this.getTarget().size();
		int i = 0, j = 0;
		List<AbstractColumn<?>> added1=CommonUtils.list();
		List<AbstractColumn<?>> added2=CommonUtils.list();
		while (i < osize || j < tsize) {
			AbstractColumn<?> oobj = null;
			AbstractColumn<?> tobj = null;
			while(i < osize){
				oobj = (AbstractColumn<?>)this.getOriginal().get(i);
				i++;
				if (diff.getLcs1().containsKey(i-1)){
					break;
				}
				AbstractColumn<?> tColumn=getColumn((List<AbstractColumn<?>>)this.getTarget(),oobj.getName());
				if (tColumn!=null&&!diff.getLcs2().containsValue(tColumn)&&!added2.contains(tColumn)){
					DbObjectDifference dbObjectDifference = new DbObjectDifference(
							oobj, tColumn, this.getEqualsHandler());
					addDbObjectDifferenceForColumn(dbObjectDifference);
					added2.add(tColumn);
				} else{
					DbObjectDifference dbObjectDifference = new DbObjectDifference(
							oobj, null, this.getEqualsHandler());
					addDbObjectDifferenceForColumn(dbObjectDifference);
				}
			}
			while(j < tsize){
				tobj = (AbstractColumn<?>)this.getTarget().get(j);
				j++;
				if (diff.getLcs2().containsKey(j-1)) {
					break;
				}
				AbstractColumn<?> oColumn=getColumn((List<AbstractColumn<?>>)this.getOriginal(),tobj.getName());
				if (oColumn!=null&&!diff.getLcs1().containsValue(oColumn)&&!added1.contains(oColumn)){
					DbObjectDifference dbObjectDifference = new DbObjectDifference(
							oColumn, tobj, this.getEqualsHandler());
					addDbObjectDifferenceForColumn(dbObjectDifference);
					added1.add(oColumn);
				} else{
					DbObjectDifference dbObjectDifference = new DbObjectDifference(
							null, tobj, this.getEqualsHandler());
					addDbObjectDifferenceForColumn(dbObjectDifference);
				}
			}
			if (!added1.contains(oobj)&&!added2.contains(tobj)){
				if (oobj!=null&&tobj != null){
					added1.add(oobj);
					added2.add(tobj);
					DbObjectDifference dbObjectDifference = new DbObjectDifference(
							oobj, tobj, this.getEqualsHandler());
					addDbObjectDifferenceForColumn(dbObjectDifference);
					continue;
				}
			}
		}
		this.setState(getState(list));
	}
	
	private AbstractColumn<?> getColumn(Collection<AbstractColumn<?>> columns, String name){
		for(AbstractColumn<?> column:columns){
			if (name.equals(column.getName())){
				return column;
			}
		}
		return null;
	}
	
	protected boolean isColumns(Object... args) {
		for (Object obj : args) {
			if (obj instanceof AbstractSchemaObjectCollection) {
				for(AbstractSchemaObject<?> o:(AbstractSchemaObjectCollection<?>)obj){
					if (o instanceof AbstractColumn){
						return true;
					}
				}
			}
		}
		return false;
	}

	
	private void addDbObjectDifference(DbObjectDifference dbObjectDifference) {
		dbObjectDifference.setParentDifference(this);
		getList().add(dbObjectDifference);
	}

	private void addDbObjectDifferenceForColumn(DbObjectDifference dbObjectDifference) {
		dbObjectDifference.setParentDifference(this);
		if (!getList().contains(dbObjectDifference)){
			getList().add(dbObjectDifference);
		}
	}

	/**
	 * 指定したオブジェクト差分コレクションのステートを取得します
	 * 
	 * @param c
	 */
	public static State getState(Collection<DbObjectDifference> c) {
		Set<State> stateSet = EnumSet.noneOf(State.class);
		for (DbObjectDifference obj : c) {
			if (obj.getState().isChanged()) {
				stateSet.add(obj.getState());
			}
		}
		if (stateSet.size() == 0) {
			return State.Unchanged;
		}
		// if (stateSet.size() == 1) {
		// return CommonUtils.first(stateSet);
		// }
		return State.Modified;
	}

	/**
	 * 指定したステータスの要素を取得します
	 * 
	 * @param states
	 */
	public static List<DbObjectDifference> getByStates(
			Collection<DbObjectDifference> c, State... states) {
		Set<State> set = CommonUtils.set(states);
		List<DbObjectDifference> result = CommonUtils.list();
		for (DbObjectDifference diff : c) {
			if (set.contains(diff.getState())) {
				result.add(diff);
			}
		}
		return result;
	}

	/**
	 * 要素を取得します
	 * 
	 */
	public List<DbObjectDifference> getList() {
		return (List<DbObjectDifference>) list;
	}

	/**
	 * 指定したステータスの要素を取得します
	 * 
	 * @param states
	 */
	public List<DbObjectDifference> getList(State... states) {
		return getByStates(this.list, states);
	}

	public DbObjectDifference find(java.util.function.Function<DbObjectDifference, Boolean> func){
		for(DbObjectDifference obj:list){
			if (func.apply(obj)){
				return obj;
			}
		}
		return null;
	}
	
	public Map<String, DbObjectDifference> toMap(){
		return toMap(obj->true);
	}
	
	public Map<String, DbObjectDifference> toMap(java.util.function.Function<DbObjectDifference, Boolean> func){
		Map<String, DbObjectDifference> map=CommonUtils.linkedMap();
		list.stream().filter(obj->func.apply(obj)).forEach(obj->{
			Object original=obj.getOriginal();
			Object target=obj.getTarget();
			if (original instanceof NameProperty){
				NameProperty<?> nameProperty=(NameProperty<?>)original;
				map.put(nameProperty.getName(), obj);
			}
			if (target instanceof NameProperty){
				NameProperty<?> nameProperty=(NameProperty<?>)original;
				map.put(nameProperty.getName(), obj);
			}
		});
		return map;
	}
	
	@Override
	protected void toString(ToStringBuilder builder) {
		SeparatedStringBuilder sepBuilder = new SeparatedStringBuilder("");
		for (DbObjectDifference diff : list) {
			if (diff.getState().isChanged()) {
				sepBuilder.add(diff);
			}
		}
		builder.add(sepBuilder.toString());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Difference<?> o) {
		if (o instanceof DbObjectDifference) {
			return 1;
		}
		if (o instanceof DbObjectPropertyDifference) {
			return 1;
		}
		return 0;
	}
	
	@Override
	public void removeRecursive(BiPredicate<String, Difference<?>> predicate) {
		Set<DbObjectDifference> children=CommonUtils.set();
		for(DbObjectDifference child:this.list){
			if (predicate.test(null, child)){
				children.add(child);
			}
		}
		for(DbObjectDifference child:children){
			this.list.remove(child);
		}
		for(DbObjectDifference child:this.list){
			if (!child.getState().isChanged()){
				children.add(child);
			}
		}
		for(DbObjectDifference child:children){
			this.list.remove(child);
		}
		if (this.list.size()==0){
			this.setState(State.Unchanged);
		}
	}

	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof DbObjectDifferenceCollection)){
			return false;
		}
		DbObjectDifferenceCollection cst=(DbObjectDifferenceCollection)obj;
		if (!this.getList().equals(cst.getList())){
			return false;
		}
		return true;
	}
}