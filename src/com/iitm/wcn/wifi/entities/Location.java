package com.iitm.wcn.wifi.entities;

public class Location {
	/* X and Y co-ordinates */
	private int x;
	private int y;
	
	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public Location(double x2, double y2) {
		this.x = (int)x2;
		this.y = (int)y2;
	}
	
	/* Some useful methods */
	public double distanceTo(Location loc) {
		return (Math.abs(Math.sqrt(Math.pow(this.x - loc.getX(), 2) + Math.pow((this.y - loc.getY() ), 2))));
	}

	@Override
	public String toString() {
		return x + ", " + y;
	}

}
