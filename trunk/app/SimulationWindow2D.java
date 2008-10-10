/*
  Part of the ReplicatorG project - http://www.replicat.org
  Copyright (c) 2008 Zach Smith

  Forked from Arduino: http://www.arduino.cc

  Based on Processing http://www.processing.org
  Copyright (c) 2004-05 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
  
  $Id: Editor.java 370 2008-01-19 16:37:19Z mellis $
*/

package processing.app;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.*;
import java.util.*;
import javax.vecmath.*;

public class SimulationWindow2D extends SimulationWindow
{	
	private long lastRepaint = 0;
	
	// these guys are our extra components.
	protected static BuildView buildView;
	protected static HorizontalRuler hRuler;
	protected static VerticalRuler vRuler;
	
	private int rulerWidth = 30;
	
	public SimulationWindow2D ()
	{
		super();

		//some inits to our build simulation
		setTitle("2D Build Simulation");
		setBackground(Color.white);
		setForeground(Color.white);
		
		createComponents();

		this.setVisible(true);

		//setup our rendering/buffer strategy
		buildView.createBufferStrategy(2);
		hRuler.createBufferStrategy(2);
		vRuler.createBufferStrategy(2);
		
		//start us off at 0,0,0
		buildView.queuePoint(new Point3d());
	}
	
	private void createComponents()
	{
		//figure out our content pane size.
		Container pane = getContentPane();
		int width = pane.getWidth();
		int height = pane.getHeight();
		
		//make our components with those sizes.
		hRuler = new HorizontalRuler(width-rulerWidth, rulerWidth, rulerWidth);
		vRuler = new VerticalRuler(rulerWidth, height-rulerWidth, rulerWidth);
		buildView = new BuildView(width-rulerWidth, height-rulerWidth);

		//lay them out in their proper places.
		pane.add(hRuler, BorderLayout.PAGE_START);
		pane.add(vRuler, BorderLayout.LINE_START);
		pane.add(buildView, BorderLayout.CENTER);
	}
	
	synchronized public void queuePoint(Point3d point)
	{
		buildView.queuePoint(point);

		doRender();
	}

	public void doRender()
	{
		//repaint all our areas.
		hRuler.doRender();
		vRuler.doRender();
		buildView.doRender();
	}
}

class MyComponent extends Canvas
{
	public MyComponent(int width, int height)
	{
		//try and configure size.
		setPreferredSize(new Dimension(width, height));
		setMinimumSize(new Dimension(width, height));
		setMaximumSize(new Dimension(width, height));
		setSize(width, height);
		
		//set our colors and stuff
		setBackground(Color.white);
		setForeground(Color.white);
		this.setVisible(true);
	}
}

class GenericRuler extends MyComponent
{
	protected int machinePosition = 0;
	protected int mousePosition = 0;
	protected int rulerWidth = 30;
	
	protected double increments[];

	public GenericRuler(int width, int height, int rWidth)
	{
		super(width, height);
		
		rulerWidth = rWidth;
		
		//these are for drawing increments on our ruler
		increments = new double[] {
			0.0001, 0.0002, 0.0005,
			0.001, 0.002, 0.005,
			0.01, 0.02, 0.05,
			0.1, 0.2, 0.5,
			1, 2, 5,
			10, 20, 50,
			100, 200, 500,
			1000, 2000, 5000,
			10000, 20000, 50000
		};
	}
	
	public void setMousePosition(int i)
	{
		//only do work if needed!
		if (mousePosition != i)
		{
			mousePosition = i;
			doRender();
		}
	}
	
	public void setMachinePosition(int i)
	{
		//only do work if needed!
		if (mousePosition != i)
		{
			machinePosition = i;
			doRender();
		}
	}
	
	public void doRender()
	{
	}
	
	protected double getIncrement(int length, double range)
	{
		double scale = length / range / 100;
		double increment = increments[0];
		
		for (int i=0; i<increments.length; i++)
		{
			increment = increments[i];
			if (increment > scale)
				break;
		}
		
		return increment;
	}
}

class HorizontalRuler extends GenericRuler
{
	public HorizontalRuler(int width, int height, int rWidth)
	{
		super(width, height, rWidth);
	}

	public void doRender()
	{
		//setup our graphics objects
		BufferStrategy myStrategy = getBufferStrategy();
		Graphics g = myStrategy.getDrawGraphics();

		//clear it
		g.setColor(Color.white);
		Rectangle bounds = g.getClipBounds();
		g.fillRect(0, 0, getWidth(), getHeight());

		//draw our border.
		g.setColor(Color.gray);
		g.drawRect(rulerWidth-1, 0, getWidth()-1-rulerWidth, getHeight()-1);

		//init our graphics object
	    Graphics2D g2 = (Graphics2D) g;

		//draw our machine indicator
		g2.setPaint(Color.green);
		Polygon xTriangle = new Polygon();
		xTriangle.addPoint(machinePosition + rulerWidth, rulerWidth - 1);
		xTriangle.addPoint(machinePosition - rulerWidth/4 + rulerWidth, rulerWidth - rulerWidth/4 - 1);
		xTriangle.addPoint(machinePosition + rulerWidth/4 + rulerWidth, rulerWidth - rulerWidth/4 - 1);
		g2.fill(xTriangle);

		//draw our mouse indicator
		g2.setPaint(Color.black);
		xTriangle = new Polygon();
		xTriangle.addPoint(mousePosition + rulerWidth, rulerWidth - 1);
		xTriangle.addPoint(mousePosition - rulerWidth/4 + rulerWidth, rulerWidth - rulerWidth/4 - 1);
		xTriangle.addPoint(mousePosition + rulerWidth/4 + rulerWidth, rulerWidth - rulerWidth/4 - 1);
		g2.fill(xTriangle);
		
		//draw our tick marks
		drawTicks(g);

		//this shows our buffer!
		myStrategy.show();
	}
	
	private void drawTicks(Graphics g)
	{
		//setup font rendering stuff.
	    Graphics2D g2 = (Graphics2D) g;
	    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setPaint(Color.black);

		//draw some helper text.
	    g.setFont(new Font("SansSerif", Font.PLAIN, 12));
		g.setColor(Color.black);
			
		double range = SimulationWindow2D.buildView.getXRange();
		int width = getWidth() - 30;
		double increment = getIncrement(width, range);
		
		//loop thru all positive increments while we're in bounds
		int i = 0;
		double realX;
		int pointX;
		int length;
		
		do {
			realX = i * increment;
			pointX = SimulationWindow2D.buildView.convertRealXToPointX(realX) + 30;
			
			if (i % 5 == 0)
			{
				length = 15;
				g.drawString(Double.toString(realX), pointX, 15);
			}
			else
			{
				length = 10;
			}
			
			g.drawLine(pointX, rulerWidth, pointX, rulerWidth - length);
			
			i++;
		} while (pointX < getWidth());
	}
}

class VerticalRuler extends GenericRuler
{
	public VerticalRuler(int width, int height, int rWidth)
	{
		super(width, height, rWidth);
	}

	public void doRender()
	{
		//setup our graphics objects
		BufferStrategy myStrategy = getBufferStrategy();
		Graphics g = myStrategy.getDrawGraphics();

		//clear it
		g.setColor(Color.white);
		Rectangle bounds = g.getClipBounds();
		g.fillRect(0, 0, getWidth(), getHeight());

		//draw our border.
		g.setColor(Color.gray);
		g.drawRect(0, 0, getWidth()-1, getHeight()-1);

		//setup graphics objecs.
	    Graphics2D g2 = (Graphics2D) g;
		g2.setPaint(Color.black);

		//draw our machine indicator
		g2.setPaint(Color.black);
		Polygon yTriangle = new Polygon();
		yTriangle.addPoint(rulerWidth - 1, mousePosition);
		yTriangle.addPoint(rulerWidth - rulerWidth/4 - 1, mousePosition - rulerWidth/4);
		yTriangle.addPoint(rulerWidth - rulerWidth/4 - 1, mousePosition + rulerWidth/4);
		g2.fill(yTriangle);

		//draw our mouse indicator
		g2.setPaint(Color.green);
		yTriangle = new Polygon();
		yTriangle.addPoint(rulerWidth - 1, machinePosition);
		yTriangle.addPoint(rulerWidth - rulerWidth/4 - 1, machinePosition - rulerWidth/4);
		yTriangle.addPoint(rulerWidth - rulerWidth/4 - 1, machinePosition + rulerWidth/4);
		g2.fill(yTriangle);


/*		
		//draw our y ticks
		for (int i=1; i<yIncrements; i++)
		{
			double yReal = i * yIncrement;
			int yPoint = convertRealYToPointY(yReal);
			
			g.drawLine(ySpacing, getHeight()-ySpacing-yPoint, ySpacing+10, getHeight()-ySpacing-yPoint);
		}
*/
		//this shows our buffer!
		myStrategy.show();
	}
}

class BuildView extends MyComponent implements MouseMotionListener
{
	private Point3d minimum;
	private Point3d maximum;
	private Point3d current;
	private double currentZ;
	
	private int mouseX = 0;
	private int mouseY = 0;
	
	private double ratio = 1.0;

	private Vector points;

	public BuildView(int width, int height)
	{
		super(width, height);

		//setup our listeners.
		addMouseMotionListener(this);

		//init our bounds.
		minimum = new Point3d();
		maximum = new Point3d();
		currentZ = 0.0;
		
		//initialize our vector
		points = new Vector();
	}
	
	public void mouseMoved(MouseEvent e)
	{
		mouseX = e.getX();
		mouseY = e.getY();
		
		SimulationWindow2D.hRuler.setMousePosition(mouseX);
		SimulationWindow2D.vRuler.setMousePosition(mouseY);
		
		doRender();
	}

	public void mouseDragged(MouseEvent e)
	{
		mouseX = e.getX();
		mouseY = e.getY();
		
		SimulationWindow2D.hRuler.setMousePosition(mouseX);
		SimulationWindow2D.vRuler.setMousePosition(mouseY);

		doRender();
	}
	
	public Point3d getMinimum()
	{
		return minimum;
	}
	
	public Point3d getMaximum()
	{
		return maximum;
	}
	
	public void queuePoint(Point3d point)
	{
		current = new Point3d(point);
		
		//System.out.println("queued: " + point.toString());

		if (points.size() == 0)
		{
			minimum = new Point3d(point);
			maximum = new Point3d(point);
		}
		else
		{
			if (point.x < minimum.x)
				minimum.x = point.x;
			if (point.y < minimum.y)
				minimum.y = point.y;
			if (point.z < minimum.z)
				minimum.z = point.z;

			if (point.x > maximum.x)
				maximum.x = point.x;
			if (point.y > maximum.y)
				maximum.y = point.y;
			if (point.z > maximum.z)
				maximum.z = point.z;
		}
			
		Point3d myPoint = new Point3d(point);
		points.addElement(myPoint);
		
		currentZ = point.z;

		calculateRatio();

		//set our machine position
		SimulationWindow2D.hRuler.setMachinePosition(convertRealXToPointX(point.x));
		SimulationWindow2D.vRuler.setMachinePosition(convertRealYToPointY(point.y));

		doRender();
	}
	
	public void doRender()
	{
		//setup our graphics objects
		BufferStrategy myStrategy = getBufferStrategy();
		Graphics g = myStrategy.getDrawGraphics();

		//clear it
		g.setColor(Color.white);
		Rectangle bounds = g.getClipBounds();
		g.fillRect(0, 0, getWidth(), getHeight());

		//dra our text
		drawHelperText(g);

		//draw our main stuff
		drawLastPoints(g);
		
		//this shows our buffer!
		myStrategy.show();
	}
	
	private void drawHelperText(Graphics g)
	{
		//init some prefs
	    Graphics2D g2 = (Graphics2D) g;
	    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setPaint(Color.black);

		//draw some helper text.
	    g.setFont(new Font("SansSerif", Font.BOLD, 14));
	    g.setColor(Color.black);
		g.drawString("Layer at z: " + currentZ, 10, 20);

		//draw our mouse position
		double mouseRealX = convertPointXToRealX(mouseX);
		double mouseRealY = convertPointYToRealY(mouseY);
		//g.drawString("Mouse: " + mouseRealX + ", " + mouseRealY + " (" + mouseX + ", " + mouseY + " ratio: " + ratio + ")", 10, 40);
		g.drawString("Mouse: " + mouseRealX + ", " + mouseRealY, 10, 40);

		//draw our mouse position
	    g.setColor(Color.green);
	    g.drawString("Machine: " + current.x + ", " + current.y, 10, 60);
	}
	
	private void drawLastPoints(Graphics g)
	{
		Color aboveColor = new Color(255, 0, 0);
		Color currentColor = new Color(0, 255, 0);
		Color belowColor = new Color(0, 0, 255);

		java.util.List lastPoints = getLastPoints(1000);
		
		Point3d start;
		Point3d end;
		
		double belowZ = currentZ;
		double aboveZ = currentZ;

		//color coding.
		int aboveTotal = 0;
		int belowTotal = 0;
		int currentTotal = 0;
		int aboveCount = 0;
		int belowCount = 0;
		int currentCount = 0;
		
		//draw our toolpaths.
		if (lastPoints.size() > 0)
		{
			start = (Point3d)lastPoints.get(0);

			//start from the most recent line backwards to find the above/below layers.
			for (int i=lastPoints.size()-1; i>=0; i--)
			{
				end = (Point3d)lastPoints.get(i);
				
				if (!start.equals(end))
				{
					//line below current plane
					if (end.z < currentZ)
					{
						//we only want one layer up/down
						if (end.z < belowZ && belowZ != currentZ)
							continue;
							
						belowZ = end.z;
						belowTotal++;
					}				
					//line above current plane
					else if (end.z > currentZ)
					{
						//we only want one layer up/down
						if (end.z > aboveZ && aboveZ != currentZ)
							continue;
						
						aboveZ = end.z;
						aboveTotal++;
					}
					//current line.
					else if (end.z == currentZ)
					{
						currentTotal++;
					}
					else
						continue;

					start = new Point3d(end);
				}
			}
			
			//draw all our lines now!
			for (ListIterator li = lastPoints.listIterator(); li.hasNext();)
			{
				end = (Point3d)li.next();

				//we have to move somewhere!
				if (!start.equals(end))
				{
					int startX = convertRealXToPointX(start.x);
					int startY = convertRealYToPointY(start.y);
					int endX = convertRealXToPointX(end.x);
					int endY = convertRealYToPointY(end.y);
					int colorValue;
					
					//line below current plane
					if (end.z < currentZ && end.z >= belowZ)
					{
						belowCount++;
						
						colorValue = 255-3*(belowTotal-belowCount);
						colorValue = Math.max(0, colorValue);
						colorValue = Math.min(255, colorValue);

						belowColor = new Color(0, 0, colorValue);
						g.setColor(belowColor);
					}
					//line above current plane
					if (end.z > currentZ && end.z <= aboveZ)
					{
						aboveCount++;

						colorValue = 255-3*(aboveTotal-aboveCount);
						colorValue = Math.max(0, colorValue);
						colorValue = Math.min(255, colorValue);

						aboveColor = new Color(colorValue, 0, 0);
						g.setColor(aboveColor);
					}
					//line in current plane
					else if (end.z == currentZ)
					{
						currentCount++;

						colorValue = 255-3*(currentTotal-currentCount);
						colorValue = Math.max(0, colorValue);
						colorValue = Math.min(255, colorValue);

						currentColor = new Color(0, colorValue, 0);
						g.setColor(currentColor);
					}
					//bail, your'e not on our plane.
					else
						continue;

					//draw up arrow
					if (end.z > start.z)
					{
						g.setColor(Color.red);
						g.drawOval(startX-5, startY-5, 10, 10);
						g.drawLine(startX-5, startY, startX+5, startY);
						g.drawLine(startX, startY-5, startX, startY+5);
					}
					//draw down arrow
					else if (end.z < start.z)
					{
						g.setColor(Color.blue);
						g.drawOval(startX-5, startY-5, 10, 10);
						g.drawOval(startX-1, startY-1, 2, 2);
					}
					//normal XY line - only draw lines on current layer or above.
					else if (end.z >= currentZ)
					{
						g.drawLine(startX, startY, endX, endY);
					}

					start = new Point3d(end);
				}
			}
			
			/*
			System.out.println("counts:");
			System.out.println(belowCount + " / " + belowTotal);
			System.out.println(aboveCount + " / " + aboveTotal);
			System.out.println(currentCount + " / " + currentTotal);
			*/
		}
	}

	private java.util.List getLastPoints(int count)
	{
		int index = Math.max(0, points.size()-count);		

		java.util.List mypoints = points.subList(index, points.size());
		
		return mypoints;
	}
	
	private void drawToolpaths(Graphics g)
	{
		Vector toolpaths = getLayerPaths(currentZ);
		Point3d start = new Point3d();
		Point3d end = new Point3d();
		
		//System.out.println("toolpaths:" + toolpaths.size());

		//draw our toolpaths.
		if (toolpaths.size() > 0)
		{
			for (Enumeration e = toolpaths.elements(); e.hasMoreElements();)
			{
				Vector path = (Vector)e.nextElement();
				//System.out.println("path points:" + path.size());

				if (path.size() > 1)
				{
					g.setColor(Color.black);
					start = (Point3d)path.firstElement();

					for (Enumeration e2 = path.elements(); e2.hasMoreElements();)
					{
						end = (Point3d)e2.nextElement();

						int startX = convertRealXToPointX(start.x);
						int startY = convertRealYToPointY(start.y);
						int endX = convertRealXToPointX(end.x);
						int endY = convertRealYToPointY(end.y);

						//System.out.println("line from: " + startX + ", " + startY + " to " + endX + ", " + endY);
						g.drawLine(startX, startY, endX, endY);

						start = new Point3d(end);
					}
				}
			}
		}
	}
	
	private Vector getLayerPaths(double layerZ)
	{
		Vector paths = new Vector();
		Vector path = new Vector();
		Point3d p;
		int i;
		
		for (Enumeration e = points.elements(); e.hasMoreElements();)
		{
			p = (Point3d)e.nextElement();
			
			//is this on our current layer?
			if (p.z == layerZ)
			{
				path.addElement(p);
				//System.out.println("added: " + p.toString());
			}
			//okay, not on layer... did we find a path?
			else if (path.size() > 0)
			{
				//System.out.println("added path of size " + path.size());
				paths.addElement(path);
				path = new Vector();
			}
		}
		
		//did we end on our current path?
		if (path.size() > 0)
			paths.addElement(path);
		
		return paths;
	}
	
	private void calculateRatio()
	{
		//calculate the ratios that will keep us inside our box
		double yRatio = (getWidth()) / (maximum.y - minimum.y);
		double xRatio = (getHeight()) / (maximum.x - minimum.x);
		
		//which one is smallest?
		ratio = Math.min(yRatio, xRatio);
	}
	
	public double getXRange()
	{
		return maximum.x - minimum.x;
	}
	
	public double getYRange()
	{
		return maximum.y - minimum.y;
	}
	
	public int convertRealXToPointX(double x)
	{
		return (int)((x - minimum.x) * ratio);
	}
	
	public double convertPointXToRealX(int x)
	{
		return (Math.round(((x / ratio) - minimum.x) * 100) / 100);
	}

	public int convertRealYToPointY(double y)
	{
		// subtract from getheight to get a normal origin.
		return (getHeight() - (int)((y - minimum.y) * ratio));
	}

	public double convertPointYToRealY(int y)
	{
		return (Math.round((maximum.y - (y / ratio)) * 100) / 100);
	}
}
