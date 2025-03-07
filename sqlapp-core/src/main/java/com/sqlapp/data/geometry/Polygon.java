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
public class Polygon extends AbstractMultiPoint<Point> implements ToUpperDimensionType<Polygon3D>{
    /** serialVersionUID */
	private static final long serialVersionUID = 3280634501061893212L;
    
    /**
     * @param points 座標
     */
    public Polygon(Point... points){
    	this.setPoints(points);
    }

    /**
     * @param args 座標
     */
    public Polygon(double... args){
    	super(args);
    }

    /**
     */
    public Polygon(){
    }
    
	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof Polygon)){
			return false;
		}
		return true;
	}

    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public Polygon clone(){
		return (Polygon)super.clone();
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
	public Polygon3D toUpperDimension() {
		Point3D[] points=new Point3D[this.points.length];
		for(int i=0;i<points.length;i++){
			points[i]=this.points[i].toUpperDimension();
		}
		return new Polygon3D(points);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#parse(java.lang.String)
	 */
	@Override
	public Polygon setValue(String text) {
		text=text.replace("(", "").replace(")", "");
		String[] values=CommonUtils.split(text, "\\s*,\\s*");
		double[] vals=Converters.getDefault().convertObject(values, double[].class);
		this.setPoint(vals);
		return this;
	}
	
	/**
	 * Path型に変換します
	 */
	public Path toPath(){
		Path ret=new Path();
		ret.setPoints(this.getPoints());
		return ret;
	}
}
