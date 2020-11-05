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
 * POINT型
 *
 */
public class Point extends AbstractPoint2D implements ToUpperDimensionType<Point3D>{
    /** serialVersionUID */
	private static final long serialVersionUID = 3280634501061893212L;

    /**
     * コンストラクタ
     */
    public Point(){
    }

    /**
     * @param x X座標
     * @param y Y座標
     */
    public Point(double x, double y){
    	super(x, y);
    }
    
	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof Point)){
			return false;
		}
		return true;
	}

	/* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public Point clone(){
    	return new Point(this.getX(), this.getY());
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
	public Point3D toUpperDimension() {
		return new Point3D(this.getX(), this.getY(), 0d);
	}
	
	/**
	 * java.awt.Pointへ変換します
	 */
    public java.awt.Point toJava2D(){
    	java.awt.Point point=new java.awt.Point((int)this.getX(), (int)this.getY());
    	return point;
    }

	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#parse(java.lang.String)
	 */
	@Override
	public Point setValue(String text) {
		text=CommonUtils.unwrap(text, "(", ")");
		String[] values=CommonUtils.split(text, "\\s*,\\s*");
		double[] vals=Converters.getDefault().convertObject(values, double[].class);
		this.setX(vals[0]);
		this.setY(vals[1]);
		return this;
	}

}
