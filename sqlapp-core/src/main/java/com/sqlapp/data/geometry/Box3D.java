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

import com.sqlapp.data.converter.Converters;
import com.sqlapp.util.CommonUtils;

/**
 * 矩形
 *
 */
public final class Box3D extends AbstractMultiPoint<Point3D> implements ToLowerDimensionType<Box>{

    /**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 7043949685038533776L;

	/**
     * @param point1 開始座標
     * @param point2 終了座標
     */
    public Box3D(Point3D point1, Point3D point2){
    	super(point1, point2);
    }

    /**
     * @param x1 開始X座標
     * @param y1 開始Y座標
     * @param x2 終了X座標
     * @param y2 終了Y座標
     */
    public Box3D(double x1, double y1, double z1, double x2, double y2, double z2){
    	super(x1, y1, z1, x2, y2, z2);
    }

    /**
     */
    public Box3D(){
    }
    
	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof Box3D)){
			return false;
		}
		return true;
	}
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public Box3D clone(){
    	return (Box3D)super.clone();
    }

	@Override
	protected Point3D[] newArray(int size) {
		return new Point3D[size];
	}

	@Override
	protected Point3D newPoint(double... a) {
		return new Point3D(a[0], a[1], a[2]);
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#getDimension()
	 */
	@Override
	public int getDimension() {
		return 3;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.ToLowerDimensionType#toLowerDimension()
	 */
	@Override
	public Box toLowerDimension() {
		Point[] points=new Point[this.points.length];
		for(int i=0;i<points.length;i++){
			points[i]=this.points[i].toLowerDimension();
		}
		return new Box(points[0], points[1]);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#parse(java.lang.String)
	 */
	@Override
	public Box3D setValue(String text) {
		text=text.replace("(", "").replace(")", "");
		String[] values=CommonUtils.split(text, "\\s*,\\s*");
		double[] vals=Converters.getDefault().convertObject(values, double[].class);
		this.setPoint(vals);
		return this;
	}

	/**
	 * Line3D型に変換します
	 */
	public Line3D toLine(){
		Line3D ret=new Line3D(this.getPoints()[0].clone(), this.getPoints()[1].clone());
		return ret;
	}

	/**
	 * Line3D型に変換します
	 */
	public Lseg3D toLseg(){
		Lseg3D ret=new Lseg3D(this.getPoints()[0].clone(), this.getPoints()[1].clone());
		return ret;
	}
}
