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
 * 多角形抽象クラス
 *
 */
public class Polygon3D extends AbstractMultiPoint<Point3D> implements ToLowerDimensionType<Polygon>{
    /** serialVersionUID */
	private static final long serialVersionUID = 3280634501061893212L;
    
    /**
     * @param points 座標
     */
    public Polygon3D(final Point3D... points){
    	this.setPoints(points);
    }

    /**
     * @param args 座標
     */
    public Polygon3D(double... args){
    	super(args);
    }

    /**
     */
    public Polygon3D(){
    }
    
	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof Polygon3D)){
			return false;
		}
		return true;
	}

    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public Polygon3D clone(){
		return (Polygon3D)super.clone();
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
	public Polygon toLowerDimension() {
		Point[] points=new Point[this.points.length];
		for(int i=0;i<points.length;i++){
			points[i]=this.points[i].toLowerDimension();
		}
		return new Polygon(points);
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#parse(java.lang.String)
	 */
	@Override
	public Polygon3D setValue(String text) {
		text=text.replace("(", "").replace(")", "");
		String[] values=CommonUtils.split(text, "\\s*,\\s*");
		double[] vals=Converters.getDefault().convertObject(values, double[].class);
		this.setPoint(vals);
		return this;
	}
	
	/**
	 * Path型に変換します
	 */
	public Path3D toPath(){
		Path3D ret=new Path3D();
		ret.setPoints(this.getPoints());
		return ret;
	}

}
