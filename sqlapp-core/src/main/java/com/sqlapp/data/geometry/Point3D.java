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
 * POINT 3D型
 *
 */
public class Point3D extends AbstractPoint2D implements ToLowerDimensionType<Point>{
    /** serialVersionUID */
	private static final long serialVersionUID = 3280634501061893212L;
    /** Z座標  */
    public double z=0d;

    /**
     * @param x X座標
     * @param y Y座標
     * @param z Z座標
     */
    public Point3D(double x, double y, double z){
    	super(x,y);
        this.z = z;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString(){
    	StringBuilder builder=new StringBuilder();
    	builder.append("(").append(this.getX()).append(",").append(this.getY()).append(",").append(this.getZ()).append(")");
    	return builder.toString();
    }
    
	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof Point3D)){
			return false;
		}
		Point3D cst=(Point3D)obj;
		if (!CommonUtils.eq(this.getZ(), cst.getZ())){
			return false;
		}
		return true;
	}

	
	/**
	 * @return the z
	 */
	public double getZ() {
		return z;
	}

	/**
	 * @param z the z to set
	 */
	public void setZ(double z) {
		this.z = z;
	}

	/* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public Point3D clone(){
    	return new Point3D(this.getX(), this.getY(), this.getZ());
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
	public Point toLowerDimension() {
		return new Point(this.getX(), this.getY());
	}


	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#parse(java.lang.String)
	 */
	@Override
	public Point3D setValue(String text) {
		text=CommonUtils.unwrap(text, "(", ")");
		String[] values=CommonUtils.split(text, "\\s*,\\s*");
		double[] vals=Converters.getDefault().convertObject(values, double[].class);
		this.setX(vals[0]);
		this.setY(vals[1]);
		this.setZ(vals[2]);
		return this;
	}
}
