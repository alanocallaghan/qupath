/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020, 2025 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.geom;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A 2D point (x &amp; y coordinates).
 * 
 * @author Pete Bankhead
 *
 */
public class Point2 extends AbstractPoint implements Externalizable, Comparable<Point2> {

	private static final Logger logger = LoggerFactory.getLogger(Point2.class);

	private double x;
	private double y;

	// Transient to avoid serializing the hashcode
	private transient int hashCode = 0;

	/**
	 * Default constructor for a point at location (0,0).
	 */
	public Point2() {
		this(0, 0);
	}
	
	/**
	 * Point constructor.
	 * @param x
	 * @param y
	 */
	public Point2(final double x, final double y) {
		this.x = x;
		this.y = y;
	}
	
	/**
	 * Get the x coordinate of this point.
	 * @return
	 */
	public double getX() {
		return x;
	}

	/**
	 * Get the y coordinate of this point.
	 * @return
	 */
	public double getY() {
		return y;
	}

	/**
	 * Calculate the squared distance between this point and a specified x and y location.
	 * @param x
	 * @param y
	 * @return
	 */
	public double distanceSq(final double x, final double y) {
		double dx = this.x - x;
		double dy = this.y - y;
		return dx * dx + dy * dy;
	}
	
	/**
	 * Calculate the distance between this point and a specified x and y location.
	 * @param x
	 * @param y
	 * @return
	 */
	public double distance(final double x, final double y) {
		return Math.sqrt(distanceSq(x, y));
	}
	
	/**
	 * Calculate the distance between this point and another point.
	 * @param p
	 * @return
	 */
	public double distance(final Point2 p) {
		return distance(p.getX(), p.getY());
	}

	@Override
	public double get(int dim) {
		if (dim == 0)
			return x;
		else if (dim == 1)
			return y;
		throw new IllegalArgumentException("Requested dimension " + dim + " for Point2 - allowable values are 0 and 1");
	}

	@Override
	public int dim() {
		return 2;
	}
	
	
	@Override
	public String toString() {
		return "Point: " + x + ", " + y;
	}

	@Override
	public int hashCode() {
		// Conceivably the hashcode *could* be zero, but it's unlikely
		if (hashCode == 0)
			hashCode = computeHashCode();
		return hashCode;
	}

	private int computeHashCode() {
		final int prime = 31;
		int result = Double.hashCode(x);
		result = prime * result + Double.hashCode(y);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof Point2 p) {
			return x == p.x && y == p.y;
		} else {
			return false;
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(1); // Version
		out.writeDouble(x);
		out.writeDouble(y);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		in.skipBytes(4); // Version
		x = in.readDouble();
		y = in.readDouble();
	}

	@Override
	public int compareTo(Point2 other) {
		if (y < other.y)
			return -1;
		if (y > other.y)
			return 1;
		if (x < other.x)
			return -1;
		if (x > other.x)
			return 1;
		return 0;
	}
}
