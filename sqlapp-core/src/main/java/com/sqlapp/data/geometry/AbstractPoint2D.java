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
import com.sqlapp.util.HashCodeBuilder;

/**
 * POINT型
 *
 */
public abstract class AbstractPoint2D extends AbstractGeometry{
    /** serialVersionUID */
	private static final long serialVersionUID = 3280634501061893212L;
	/** X座標  */
    private double x=0d;
    /** Y座標  */
    private double y=0d;

    /**
     */
    protected AbstractPoint2D(){
    }

    /**
     * @param x X座標
     * @param y Y座標
     */
    public AbstractPoint2D(double x, double y){
        this.x = x;
        this.y = y;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString(){
    	StringBuilder builder=new StringBuilder();
    	builder.append("(").append(x).append(",").append(y).append(")");
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
		if (!(obj instanceof AbstractPoint2D)){
			return false;
		}
		AbstractPoint2D cst=(AbstractPoint2D)obj;
		if (!CommonUtils.eq(this.getX(), cst.getX())){
			return false;
		}
		if (!CommonUtils.eq(this.getY(), cst.getY())){
			return false;
		}
		return true;
	}
    
    /**
	 * @return the x
	 */
	public double getX() {
		return x;
	}

	/**
	 * @param x the x to set
	 */
	public void setX(double x) {
		this.x = x;
	}

	/**
	 * @return the y
	 */
	public double getY() {
		return y;
	}

	/**
	 * @param y the y to set
	 */
	public void setY(double y) {
		this.y = y;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	protected void hashCode(HashCodeBuilder builder){
		builder.append(x);
		builder.append(y);
	}
	
	/* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public abstract AbstractPoint2D clone();
    
    /* (non-Javadoc)
     * @see com.sqlapp.data.geometric.AbstractGeometry#getDimension()
     */
    @Override
	public int getDimension() {
		return 2;
	}
    
}
