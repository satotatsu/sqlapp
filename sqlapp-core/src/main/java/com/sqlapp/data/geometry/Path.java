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

/**
 * 多角形抽象クラス
 *
 */
public class Path extends  AbstractMultiPoint<Point> implements ToUpperDimensionType<Path3D>{
    /** serialVersionUID */
	private static final long serialVersionUID = 3280634501061893212L;
    /**
     * 開いているか?
     */
    public boolean open=false;
    
    /**
     * @param open 開いているか?
     * @param points 座標
     */
    public Path(boolean open, Point... points){
        this.open=open;
    	this.setPoints(points);
    }
    
    /**
     * @param points 座標
     */
    public Path(Point... points){
    	this.setPoints(points);
    }

    /**
     * @param points 座標
     */
    public Path(double... points){
    	this(false, points);
    }

    /**
     * @param open 開いているか?
     * @param points 座標
     */
    public Path(boolean open, double... points){
    	super(points);
    	this.open=open;
    }

    /**
     */
    public Path(){
    }

    @Override
    protected char getOpen(){
    	if (this.open){
    		return '[';
    	}else{
    		return '(';
    	}
    }

    @Override
    protected char getClose(){
    	if (this.open){
    		return ']';
    	}else{
    		return ')';
    	}
    }

	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof Path)){
			return false;
		}
		Path cst=(Path)obj;
		if (!CommonUtils.eq(this.open, cst.open)){
			return false;
		}
		return true;
	}

    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public Path clone(){
		return (Path)super.clone();
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
	public Path3D toUpperDimension() {
		Point3D[] points=new Point3D[this.points.length];
		for(int i=0;i<points.length;i++){
			points[i]=this.points[i].toUpperDimension();
		}
		return new Path3D(this.open, points);
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#parse(java.lang.String)
	 */
	@Override
	public Path setValue(String text) {
		final GeometryTextParser.PointList list = this.setPointText(text, true);
		this.open = list.enclosure == GeometryTextParser.Enclosure.BRACKET;
		return this;
	}
	
	/**
	 * 面積を求めます。
	 * <p>
	 * 閉じたPathを多角形とみなし、靴紐公式で面積を求めます。点が3点未満の場合は0を返します。 開いたPathは面積を持たないため
	 * {@link Double#NaN} を返します。
	 * </p>
	 */
	public double getArea(){
		if (this.open) {
			return Double.NaN;
		}
		if (points == null || points.length < 3) {
			return 0d;
		}
		double total = 0d;
		for (int i = 0; i < points.length; i++) {
			final Point p = points[i];
			final Point q = points[(i + 1) % points.length];
			total += p.getX() * q.getY() - q.getX() * p.getY();
		}
		return Math.abs(total) / 2d;
	}
	
	/**
	 * Polygon型に変換します
	 */
	public Polygon toPolygon(){
		Polygon ret=new Polygon();
		ret.setPoints(this.getPoints());
		return ret;
	}
}
