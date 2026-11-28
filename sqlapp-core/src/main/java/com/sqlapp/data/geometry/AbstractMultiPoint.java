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

import java.util.Arrays;

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
		final T[] newPoints = this.newArray(points.length);
		for (int i = 0; i < points.length; i++) {
			newPoints[i] = points[i];
		}
		validatePoints(newPoints);
		this.points = newPoints;
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

	/**
	 * 座標の並びから点を設定します。
	 * 
	 * @param args
	 *            座標(点ごとに {@link #getDimension()} 個ずつ並べたもの)
	 * @throws IllegalArgumentException
	 *             座標数が次元で割り切れない場合、または点数が型の要件を満たさない場合
	 */
	protected void setPoint(double... args) {
		final int dimension = this.getDimension();
		if (args.length % dimension != 0) {
			throw new IllegalArgumentException(this.getClass().getSimpleName() + " requires " + dimension
					+ " coordinates per point, but " + args.length + " coordinates were given.");
		}
		final int size = args.length / dimension;
		final T[] newPoints = this.newArray(size);
		final double[] values = new double[dimension];
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < dimension; j++) {
				values[j] = args[dimension * i + j];
			}
			newPoints[i] = newPoint(values);
		}
		validatePoints(newPoints);
		this.points = newPoints;
	}

	/**
	 * コンストラクタ
	 */
	protected AbstractMultiPoint() {
	}

	/**
	 * 最小の点数
	 */
	protected int getMinPointSize() {
		return 1;
	}

	/**
	 * 最大の点数
	 */
	protected int getMaxPointSize() {
		return Integer.MAX_VALUE;
	}

	/**
	 * 設定する点を検証します。
	 * 
	 * @throws IllegalArgumentException
	 *             点数が型の要件を満たさない場合、またはnullの点を含む場合
	 */
	protected void validatePoints(final T[] points) {
		final int min = getMinPointSize();
		final int max = getMaxPointSize();
		if (points.length < min || points.length > max) {
			final String expected;
			if (min == max) {
				expected = String.valueOf(min);
			} else if (max == Integer.MAX_VALUE) {
				expected = min + " or more";
			} else {
				expected = min + " to " + max;
			}
			throw new IllegalArgumentException(this.getClass().getSimpleName() + " requires " + expected
					+ " points, but " + points.length + " points were given.");
		}
		for (final T point : points) {
			if (point == null) {
				throw new IllegalArgumentException(this.getClass().getSimpleName() + " can't contain null point.");
			}
		}
	}

	/**
	 * 文字列の点の列を解析して設定します。解析に失敗した場合は値を変更しません。
	 * 
	 * @param text
	 *            文字列
	 * @param allowBracket
	 *            "[...]" 形式を受け付けるか
	 * @return 解析結果
	 */
	protected GeometryTextParser.PointList setPointText(final String text, final boolean allowBracket) {
		final GeometryTextParser.PointList list = GeometryTextParser.parsePoints(text, this.getDimension(),
				getMinPointSize(), getMaxPointSize(), allowBracket, this.getClass().getSimpleName());
		this.setPoint(list.flatten(this.getDimension()));
		return list;
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
		if (this.points != null) {
			for (int i = 0; i < this.points.length; i++) {
				if (i != 0) {
					builder.append(",");
				}
				builder.append(this.points[i]);
			}
		}
		builder.append(getClose());
		return builder.toString();
	}

	@Override
	protected void hashCode(HashCodeBuilder builder) {
		if (this.points == null) {
			return;
		}
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
		return Arrays.equals(this.points, cst.points);
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
	 * @throws IllegalArgumentException
	 *             点数が型の要件を満たさない場合
	 */
	@SuppressWarnings("unchecked")
	public void setPoints(T... points) {
		T[] newPoints = newArray(points.length);
		for (int i = 0; i < points.length; i++) {
			newPoints[i] = points[i] == null ? null : (T) points[i].clone();
		}
		validatePoints(newPoints);
		this.points = newPoints;
	}

	/**
	 * 配列と各点を複製します。
	 * 
	 * @see java.lang.Object#clone()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public AbstractMultiPoint<?> clone() {
		final AbstractMultiPoint<T> clone;
		try {
			clone = (AbstractMultiPoint<T>) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
		if (this.points != null) {
			clone.points = newArray(this.points.length);
			for (int i = 0; i < this.points.length; i++) {
				clone.points[i] = this.points[i] == null ? null : (T) this.points[i].clone();
			}
		}
		return clone;
	}
}
