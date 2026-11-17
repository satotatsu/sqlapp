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
public final class Line extends AbstractMultiPoint<Point> implements ToUpperDimensionType<Line3D>{

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
	 * B が 0 でない場合は x=0 と x=1 の点、B が 0 の場合(垂直線)は y=0 と y=1 の点を使用します。 2点で表すため、元の係数の大きさ・符号は保存されず、直線としての意味のみ保持されます。
	 * </p>
	 * 
	 * @param a
	 *            Aの係数
	 * @param b
	 *            Bの係数
	 * @param c
	 *            Cの係数
	 * @throws IllegalArgumentException
	 *             A と B がともに 0 の場合
	 */
	public static Line fromCoefficients(final double a, final double b, final double c) {
		if (a == 0d && b == 0d) {
			throw new IllegalArgumentException("Invalid line coefficients: A and B cannot both be zero. {" + a + ","
					+ b + "," + c + "}");
		}
		// "+ 0d" は -0.0 を 0.0 にそろえるため
		if (b != 0d) {
			return new Line(0d, -c / b + 0d, 1d, -(a + c) / b + 0d);
		}
		final double x = -c / a + 0d;
		return new Line(x, 0d, x, 1d);
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
	 * @see com.sqlapp.data.geometric.ToUpperDimensionType#toUpperDimension()
	 */
	@Override
	public Line3D toUpperDimension() {
		Point3D[] points=new Point3D[this.points.length];
		for(int i=0;i<points.length;i++){
			points[i]=this.points[i].toUpperDimension();
		}
		return new Line3D(points[0], points[1]);
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
