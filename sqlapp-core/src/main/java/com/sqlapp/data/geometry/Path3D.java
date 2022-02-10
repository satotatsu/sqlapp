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

package com.sqlapp.data.geometry;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.util.CommonUtils;

/**
 * 多角形抽象クラス
 *
 */
public class Path3D extends  AbstractMultiPoint<Point3D> implements ToLowerDimensionType<Path>{
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
    public Path3D(boolean open, Point3D... points){
        this.open=open;
    	this.setPoints(points);
    }
    
    /**
     * @param points 座標
     */
    public Path3D(Point3D... points){
    	this.setPoints(points);
    }

    /**
     * @param points 座標
     */
    public Path3D(double... points){
    	this(false, points);
    }

    /**
     * @param open 開いているか?
     * @param points 座標
     */
    public Path3D(boolean open, double... points){
    	super(points);
    	this.open=open;
    }

    /**
     */
    public Path3D(){
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
		if (!(obj instanceof Path3D)){
			return false;
		}
		Path3D cst=(Path3D)obj;
		if (!CommonUtils.eq(this.open, cst.open)){
			return false;
		}
		return true;
	}

    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public Path3D clone(){
		return (Path3D)super.clone();
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
	public Path toLowerDimension() {
		Point[] points=new Point[this.points.length];
		for(int i=0;i<points.length;i++){
			points[i]=this.points[i].toLowerDimension();
		}
		return new Path(points);
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#parse(java.lang.String)
	 */
	@Override
	public Path3D setValue(String text) {
		boolean open=false;
		if (text.contains("[")){
			text=text.replace("[", "").replace("]", "");
			open=true;
		} else{
			text=text.replace("(", "").replace(")", "");
		}
		String[] values=CommonUtils.split(text, "\\s*,\\s*");
		double[] vals=Converters.getDefault().convertObject(values, double[].class);
		this.open=open;
    	setPoint(vals);
		return this;
	}
	
	
	/**
	 * Polygon3D型に変換します
	 */
	public Polygon3D toPolygon(){
		Polygon3D ret=new Polygon3D();
		ret.setPoints(this.getPoints());
		return ret;
	}
}
