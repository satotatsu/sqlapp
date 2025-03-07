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

package com.sqlapp.data.geometry;

import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.HashCodeBuilder;

/**
 * 複数点型抽象クラス
 *
 */
public abstract class AbstractMultiPoint<T extends AbstractPoint2D> extends AbstractGeometry {
	/** serialVersionUID */
	private static final long serialVersionUID = 3280634501061893212L;
	protected T points[];

	protected abstract T[] newArray(int size);

	protected abstract T newPoint(double... a);

	/**
	 * コンストラクタ
	 * 
	 * @param points
	 *            座標
	 */
	@SafeVarargs
	protected AbstractMultiPoint(T... points) {
		this.points = this.newArray(points.length);
		for (int i = 0; i < points.length; i++) {
			this.points[i] = points[i];
		}
	}

	/**
	 * コンストラクタ
	 * 
	 * @param args
	 *            座標
	 */
	protected AbstractMultiPoint(double... args) {
		setPoint(args);
	}

	protected void setPoint(double... args) {
		int size = args.length / this.getDimension();
		this.points = this.newArray(size);
		double[] values = new double[this.getDimension()];
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < this.getDimension(); j++) {
				values[j] = args[this.getDimension() * i + j];
			}
			this.points[i] = newPoint(values);
		}
	}

	/**
	 * コンストラクタ
	 */
	protected AbstractMultiPoint() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(getOpen());
		for (int i = 0; i < this.points.length; i++) {
			if (i != 0) {
				builder.append(",");
			}
			builder.append(this.points[i]);
		}
		builder.append(getClose());
		return builder.toString();
	}

	@Override
	protected void hashCode(HashCodeBuilder builder) {
		for (int i = 0; i < this.points.length; i++) {
			builder.append(i);
			if (this.points[i] != null) {
				builder.append(this.points[i].hashCode());
			}
		}
	}

	protected char getOpen() {
		return '(';
	}

	protected char getClose() {
		return ')';
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.geometric.AbstractGeometry#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof AbstractMultiPoint)) {
			return false;
		}
		AbstractMultiPoint<?> cst = (AbstractMultiPoint<?>) obj;
		for (int i = 0; i < this.points.length; i++) {
			if (!CommonUtils.eq(this.points[i], cst.points[i])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @return the points
	 */
	public T[] getPoints() {
		return points;
	}

	/**
	 * @param points
	 *            the points to set
	 */
	@SuppressWarnings("unchecked")
	public void setPoints(T... points) {
		T[] newPoints = newArray(points.length);
		for (int i = 0; i < points.length; i++) {
			newPoints[i] = (T) points[i].clone();
		}
		this.points = newPoints;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public AbstractMultiPoint<?> clone() {
		try {
			return (AbstractMultiPoint<?>) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
}
