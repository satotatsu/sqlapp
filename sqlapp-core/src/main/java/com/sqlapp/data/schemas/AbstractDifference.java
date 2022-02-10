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

import java.io.Serializable;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.HashCodeBuilder;
import com.sqlapp.util.ToStringBuilder;

/**
 * オブジェクトの変更状態抽象クラス
 * 
 */
public abstract class AbstractDifference<T> implements Serializable,
		Difference<T>, Comparable<Difference<?>> {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -200061059583091166L;
	/**
	 * 比較元のオブジェクト
	 */
	private final T original;
	/**
	 * 比較先のオブジェクト
	 */
	private final T target;
	/**
	 * プロパティ名
	 */
	private String propertyName = null;

	private State state = State.Unchanged;
	/**
	 * 比較元の親オブジェクトの参照
	 */
	private DbCommonObject<?> originalParent;
	/**
	 * 比較先の親オブジェクトの参照
	 */
	private DbCommonObject<?> targetParent;
	/**
	 * 比較の親オブジェクト
	 */
	private Difference<?> parentDifference;
	/**
	 * 比較用のハンドラー
	 */
	private EqualsHandler equalsHandler;

	@SuppressWarnings("unchecked")
	protected AbstractDifference(String propertyName, final T original,
			final T target, final EqualsHandler equalsHandler) {
		this.propertyName = propertyName;
		this.original = (T) original;
		this.target = (T) target;
		if (original instanceof HasParent) {
			this.setOriginalParent(((HasParent<DbCommonObject<?>>) original)
					.getParent());
		}
		if (target instanceof HasParent) {
			this.setTargetParent(((HasParent<DbCommonObject<?>>) target)
					.getParent());
		}
		this.equalsHandler = equalsHandler;
	}

	protected AbstractDifference(final T original, final T target,
			final EqualsHandler equalsHandler) {
		this.original = (T) original;
		this.target = (T) target;
		this.equalsHandler = equalsHandler;
	}

	protected AbstractDifference(String propertyName,
			DbCommonObject<?> originalParent, final T original,
			DbCommonObject<?> targetParent, final T target,
			final EqualsHandler equalsHandler) {
		this.propertyName = propertyName;
		this.originalParent = originalParent;
		this.original = (T) original;
		this.targetParent = targetParent;
		this.target = (T) target;
		this.equalsHandler = equalsHandler;
	}

	protected AbstractDifference(DbCommonObject<?> originalParent,
			final T original, DbCommonObject<?> targetParent, final T target,
			final EqualsHandler equalsHandler) {
		this.originalParent = originalParent;
		this.original = original;
		this.targetParent = targetParent;
		this.target = target;
		this.equalsHandler = equalsHandler;
	}

	@Override
	public T getOriginal() {
		return original;
	}

	@Override
	public T getTarget() {
		return target;
	}

	/**
	 * @param ownParent
	 *            the ownParent to set
	 */
	protected void setOriginalParent(DbCommonObject<?> originalParent) {
		this.originalParent = originalParent;
	}

	/**
	 * @param targetParent
	 *            the targetParent to set
	 */
	protected void setTargetParent(DbCommonObject<?> targetParent) {
		this.targetParent = targetParent;
	}

	/**
	 * @return the propertyName
	 */
	protected String getPropertyName() {
		return propertyName;
	}

	/**
	 * @return the ownParent
	 */
	@Override
	public DbCommonObject<?>getOriginalParent() {
		return originalParent;
	}

	/**
	 * @return the targetParent
	 */
	@Override
	public DbCommonObject<?> getTargetParent() {
		return targetParent;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.schemas.Difference#getState()
	 */
	@Override
	public State getState() {
		return state;
	}

	/**
	 * @return the equalsHandler
	 */
	protected EqualsHandler getEqualsHandler() {
		return equalsHandler;
	}

	/**
	 * @param state
	 *            the state to set
	 */
	protected void setState(State state) {
		this.state = state;
	}

	/**
	 * @param parentDifference
	 *            the parentDifference to set
	 */
	protected void setParentDifference(Difference<?> parentDifference) {
		this.parentDifference = parentDifference;
		if (parentDifference != null) {
			this.setOriginalParent((DbCommonObject<?>) parentDifference
					.getOriginal());
			this.setTargetParent((DbCommonObject<?>) parentDifference
					.getTarget());
		}
	}

	/**
	 * @return the parentDifference
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <S extends Difference<?>> S getParentDifference() {
		return (S) parentDifference;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		HashCodeBuilder builder = new HashCodeBuilder();
		builder.append(original);
		builder.append(originalParent);
		builder.append(target);
		builder.append(targetParent);
		return builder.hashCode();
	}
	
	@Override
	public boolean equals(Object obj){
		if (obj==null){
			return false;
		}
		if (!(obj instanceof AbstractDifference)){
			return false;
		}
		AbstractDifference<?> cst=(AbstractDifference<?>)obj;
		if (this.original!=cst.original){
			return false;
		}
		if (this.originalParent!=cst.originalParent){
			return false;
		}
		if (this.target!=cst.target){
			return false;
		}
		if (this.targetParent!=cst.targetParent){
			return false;
		}
		if (this.state!=cst.state){
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		if (state == State.Unchanged) {
			return "";
		}
		ToStringBuilder builder = new ToStringBuilder();
		// builder.setOpenQuate("").setCloseQuate("").setSeparator("\n");
		builder.setOpenQuate("").setCloseQuate("").setSeparator("\n");
		toString(builder);
		return builder.toString();
	}

	protected abstract void toString(ToStringBuilder builder);

	protected static boolean isDbObject(Object... args) {
		for (Object obj : args) {
			if (obj instanceof DbCommonObject) {
				return true;
			}
		}
		return false;
	}

	protected static boolean isDbObjectCollection(Object... args) {
		for (Object obj : args) {
			if (obj instanceof DbObjectCollection) {
				return true;
			}
		}
		return false;
	}

	protected abstract void diff();

	/**
	 * DbObjectDifferenceに変換します
	 * 
	 */
	public DbObjectDifference toDifference() {
		return (DbObjectDifference) this;
	}

	/**
	 * DbObjectDifferenceCollectionに変換します
	 * 
	 */
	public DbObjectDifferenceCollection toDifferenceCollection() {
		return (DbObjectDifferenceCollection) this;
	}

	/**
	 * 変更の階層を返します。
	 * 
	 */
	protected int getLevel() {
		return level(this, 0);
	}

	private int level(Difference<?> difference, int level) {
		if (difference == null) {
			return level;
		}
		if (difference.getParentDifference() == null
				|| difference.getParentDifference() == this) {
			return level;
		}
		if (difference.getParentDifference() instanceof DbObjectDifferenceCollection) {
			return level(difference.getParentDifference(), level);
		} else {
			return level(difference.getParentDifference(), level + 1);
		}
	}

	protected String getStateText(State state) {
		if (state == null) {
			return " ";
		}
		switch (state) {
		case Added:
			return "+";
		case Modified:
			return "C";
		case Deleted:
			return "-";
		default:
			return " ";
		}
	}
	
	protected String format(State state, Object value) {
		if (value instanceof AbstractDbObject){
			return format(state, ((AbstractDbObject<?>)value));
		}
		int indentSize = this.getLevel();
		String indent = CommonUtils.getString('\t', indentSize);
		return "\n" + indent + getStateText(state) + ":" + value;
	}

	protected String format(State state, AbstractDbObject<?> value) {
		int indentSize = this.getLevel();
		String indent = CommonUtils.getString('\t', indentSize);
		return "\n" + indent + getStateText(state) + ":" + value.toStringSimple();
	}

	protected String getSimpleName(Object obj){
		if (obj instanceof AbstractDbObject){
			return ((AbstractDbObject<?>)obj).getSimpleName();
		} else if (obj instanceof Row){
			return ((Row)obj).getSimpleName();
		} else{
			return obj.getClass().getSimpleName();
		}
	}
}
