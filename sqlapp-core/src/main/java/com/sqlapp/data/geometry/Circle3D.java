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
import com.sqlapp.util.HashCodeBuilder;

/**
 * CIRCLE型
 *
 */
public class Circle3D extends AbstractPoint2D implements ToLowerDimensionType<Circle>{
    /** serialVersionUID */
	private static final long serialVersionUID = 3280634501061893212L;
    /** Z座標  */
    public double z=0d;
	/** 半径  */
    private double r=0d;

    /**
     * @param x X座標
     * @param y Y座標
     * @param z Z座標
     * @param r 半径
     */
    public Circle3D(double x, double y, double z, double r){
    	super(x,y);
        this.z = z;
        this.r = r;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString(){
    	StringBuilder builder=new StringBuilder();
    	builder.append("<(").append(this.getX()).append(",").append(this.getY()).append(",").append(this.getZ()).append("),").append(this.getR()).append(">");
    	return builder.toString();
    }
	
	@Override
	protected void hashCode(HashCodeBuilder builder){
		builder.append(r);
		builder.append(z);
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj){
		if (!super.equals(obj)){
			return false;
		}
		if (!(obj instanceof Circle3D)){
			return false;
		}
		Circle3D cst=(Circle3D)obj;
		if (!CommonUtils.eq(this.getZ(), cst.getZ())){
			return false;
		}
		if (!CommonUtils.eq(this.getR(), cst.getR())){
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

	/**
	 * @return the r
	 */
	public double getR() {
		return r;
	}

	/**
	 * @param r the r to set
	 */
	public void setR(double r) {
		this.r = r;
	}

	/* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public Circle3D clone(){
    	return new Circle3D(this.getX(), this.getY(), this.getZ(), this.r);
    }

    /* (non-Javadoc)
     * @see com.sqlapp.data.geometric.Point3D#getDimension()
     */
    public int getDimension() {
		return 3;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.ToLowerDimensionType#toLowerDimension()
	 */
	@Override
	public Circle toLowerDimension() {
		return new Circle(this.getX(), this.getY(),this.getR());
	}
	
	/* (non-Javadoc)
	 * @see com.sqlapp.data.geometric.AbstractGeometry#parse(java.lang.String)
	 */
	@Override
	public Circle3D setValue(String text) {
		text=CommonUtils.unwrap(text, "<", ">");
		text=text.replace("(", "").replace(")", "");
		String[] values=CommonUtils.split(text, "\\s*,\\s*");
		Double[] vals=Converters.getDefault().convertObject(values, Double[].class);
		this.setX(vals[0]);
		this.setY(vals[1]);
		this.setZ(vals[2]);
		this.setR(vals[3]);
		return this;
	}
}
