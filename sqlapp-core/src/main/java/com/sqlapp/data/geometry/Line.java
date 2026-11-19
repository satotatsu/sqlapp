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


/**
 * 無限の直線型
 *
 */
public final class Line extends AbstractMultiPoint<Point>{

    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7043949685038533776L;

	/**
     * @param point1 開始座標
     * @param point2 終了座標
     */
    public Line(Point point1, Point point2){
    	super(point1, point2);
    }

    /**
     * @param x1 開始X座標
     * @param y1 開始Y座標
     * @param x2 終了X座標
     * @param y2 終了Y座標
     */
    public Line(double x1, double y1, double x2, double y2){
    	super(x1, y1, x2, y2);
    }

    
    /**
     */
    public Line(){
    }

	/**
	 * 直線の方程式 Ax + By + C = 0 の係数から、直線上の異なる2点を持つ Line を作成します。
	 * <p>
	 * 係数の大きさに応じて軸と点の間隔を選択し、除算のオーバーフローや丸めによる傾きの消失を避けます。
	 * 2点で表すため、元の係数の大きさ・符号は保存されず、直線としての意味のみ保持されます。
	 * </p>
	 * 
	 * @param a
	 *            Aの係数
	 * @param b
	 *            Bの係数
	 * @param c
	 *            Cの係数
	 * @throws IllegalArgumentException
	 *             係数が有限でない場合、A と B がともに 0 の場合、または有限の2点で表せない場合
	 */
	public static Line fromCoefficients(final double a, final double b, final double c) {
		if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c) || (a == 0d && b == 0d)) {
			throw new IllegalArgumentException("Invalid line coefficients: coefficients must be finite and A and B cannot both be zero. {" + a + ","
					+ b + "," + c + "}");
		}
		// Solve for the coordinate with the larger coefficient, so |slope| <= 1.
		final boolean solveY = Math.abs(b) >= Math.abs(a);
		final double divisor = solveY ? b : a;
		final double slope = -(solveY ? a : b) / divisor;
		final double intercept = -c / divisor + 0d;
		if (!Double.isFinite(intercept) || (slope == 0d && (solveY ? a : b) != 0d)) {
			throw new IllegalArgumentException("Line coefficients cannot be represented by finite points.");
		}
		// Use a larger independent coordinate when a unit step would lose the slope.
		double step = slope == 0d ? 1d : Math.max(1d, Math.abs(intercept));
		final double sign = (slope > 0d) == (intercept > 0d) && intercept != 0d ? -1d : 1d;
		double signedStep = Math.copySign(step, sign);
		double end = Math.fma(slope, signedStep, intercept) + 0d;
		if (slope != 0d && end == intercept) {
			// Even a very small slope can be represented if the independent
			// coordinate is increased enough to move several ULPs at the intercept.
			step = (Math.ulp(intercept) / Math.abs(slope)) * 4d;
			signedStep = Math.copySign(step, sign);
			end = Math.fma(slope, signedStep, intercept) + 0d;
		}
		if (!Double.isFinite(signedStep) || !Double.isFinite(end) || (slope != 0d && end == intercept)) {
			throw new IllegalArgumentException("Line coefficients cannot be represented by distinct finite points.");
		}
		return solveY ? new Line(0d, intercept, signedStep, end)
				: new Line(intercept, 0d, end, signedStep);
	}
    
	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof Line)){
			return false;
		}
		return true;
	}
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public Line clone(){
    	return (Line)super.clone();
    }

	@Override
	protected Point[] newArray(int size) {
		return new Point[size];
	}

	@Override
	protected Point newPoint(double... a) {
		return new Point(a[0], a[1]);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#getDimension()
	 */
	@Override
	public int getDimension() {
		return 2;
	}

	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#parse(java.lang.String)
	 */
	@Override
	public Line setValue(String text) {
		if (GeometryTextParser.isCoefficientForm(text)) {
			final double[] coefficients = GeometryTextParser.parseCoefficients(text, "Line");
			final Line line = fromCoefficients(coefficients[0], coefficients[1], coefficients[2]);
			this.points = line.points;
			return this;
		}
		this.setPointText(text, true);
		return this;
	}

	/**
	 * 2点のみ設定できます。
	 */
	@Override
	protected int getMinPointSize() {
		return 2;
	}

	/**
	 * 2点のみ設定できます。
	 */
	@Override
	protected int getMaxPointSize() {
		return 2;
	}

	/**
	 * 2点の座標が同じ場合は直線を定められないため拒否します。
	 */
	@Override
	protected void validatePoints(final Point[] points) {
		super.validatePoints(points);
		if (points[0].equals(points[1])) {
			throw new IllegalArgumentException("Line requires two distinct points. points=" + points[0] + ","
					+ points[1]);
		}
	}


	/**
	 * Lseg型に変換します
	 */
	public Lseg toLseg(){
		Lseg ret=new Lseg(this.getPoints()[0].clone(), this.getPoints()[1].clone());
		return ret;
	}

	/**
	 * Box型に変換します
	 */
	public Box toBox(){
		Box ret=new Box(this.getPoints()[0].clone(), this.getPoints()[1].clone());
		return ret;
	}
}
