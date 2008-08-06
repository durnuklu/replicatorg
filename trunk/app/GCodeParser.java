/*
  GCodeParser.java

  Handles parsing GCode.
  
  Part of the ReplicatorG project - http://www.replicat.org
  Copyright (c) 2008 Zach Smith

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
*/

package processing.app;

import java.util.*;
import java.util.regex.*;
import javax.vecmath.*;
import javax.swing.JOptionPane;

public class GCodeParser
{
	// command to parse
	protected String command;

	//our code data storage guys.
	protected Hashtable codeValues;
	protected Hashtable seenCodes;
	static protected String[] codes = {
		"D", "F", "G", "H", "I", "J", "K", "L",
		"M", "P", "Q", "R", "S", "T", "X", "Y", "Z"
	};
	
	//our curve section variables.
	public static double curveSectionInches = 0.019685;
	public static double curveSectionMM = 0.5;
	protected double curveSection = 0.0;
	
	// machine state varibles
	protected Point3d current;
	protected Point3d target;
	protected Point3d delta;
	
	//false = incremental; true = absolute
	boolean absoluteMode = false;
	
	//our feedrate variables.
	double maximumFeedrate = 0.0;
	double feedrate = 0.0;

	/* keep track of the last G code - this is the command mode to use
	 * if there is no command in the current string 
	 */
	int lastGCode = -1;
	
	// current selected tool
	protected int tool = 0;
	
	// a comment passed in
	protected String comment = "";
	
	//pattern matchers.
	Pattern parenPattern;
	Pattern semiPattern;
	Pattern deleteBlockPattern;
	
	//unit variables.
	public static int UNITS_MM = 1;
	public static int UNITS_INCHES = 1;
	protected int units;

	/**
	  * Creates the driver object.
	  */
	public GCodeParser()
	{
		//we default to millimeters
		units = UNITS_MM;
		curveSection = curveSectionMM;
		
		//precompile regexes for speed
		parenPattern = Pattern.compile("\\((.*)\\)");
		semiPattern = Pattern.compile(";(.*)");
		deleteBlockPattern = Pattern.compile("^(\\.*)");
		
		//setup our points.
		current = new Point3d();
		target = new Point3d();
		delta = new Point3d();
		
		//init our value tables.
		codeValues = new Hashtable(codes.length, 1);
		seenCodes = new Hashtable(codes.length, 1);
	}
	
	/**
	 * Parses a line of GCode, sets up the variables, etc.
	 * @param String cmd a line of GCode to parse
	 */
	public boolean parse(String cmd)
	{
		//get ready for last one.
		cleanup();
		
		//save our command
		command = cmd;

		//handle comments.
		parseComments();
		stripComments();
		
		//load all codes
		for (int i=0; i<codes.length; i++)
		{
			double value = parseCode(codes[i]);
			codeValues.put(new String(codes[i]), new Double(value));
		}
		
		// if no command was seen, but parameters were, 
		// then use the last G code as the current command
		if (!hasCode("G") && (hasCode("X") || hasCode("Y") || hasCode("Z")))
		{
			seenCodes.put(new String("G"), new Boolean(true));
			codeValues.put(new String("G"), new Double(lastGCode));
		}
		
		return true;
	}
	
	private boolean findCode(String code)
	{
		if (command.indexOf(code) >= 0)
			return true;
		else
			return false;
	}
	
	public double getCodeValue(String c)
	{
		Double d = (Double)codeValues.get(c);
		
		if (d != null)
			return d.doubleValue();
		else
			return -1.0;
	}
	
	/**
	 * Checks to see if our current line of GCode has this particular code
	 * @param char code the code to check for (G, M, X, etc.)
	 * @return boolean if the code was found or not
	 */
	private boolean hasCode(String code)
	{
		Boolean b = (Boolean)seenCodes.get(code);
		
		if (b != null)
			return b.booleanValue();
		else
			return false;
	}
	
	/**
	 * Finds out the value of the code we're looking for
	 * @param char code the code whose value we're looking up
	 * @return double the value of the code, -1 if not found.
	 */
	private double parseCode(String code)
	{
		Pattern myPattern = Pattern.compile(code + "([0-9.+-]+)");
		Matcher myMatcher = myPattern.matcher(command);
		
		if (findCode(code))
		{
			seenCodes.put(code, new Boolean(true));

			if (myMatcher.find())
			{
				String match = myMatcher.group(1);
				double number = Double.parseDouble(match);

				return number;
			}
			//send a 0 to that its noted somewhere.
			else
				return 0;
		}
		
		//bail/fail with a -1
		return -1;
	}
	
	private void parseComments()
	{
		Matcher parenMatcher = parenPattern.matcher(command);
		Matcher semiMatcher = semiPattern.matcher(command);

		if (parenMatcher.find())
			comment = parenMatcher.group(1);

		if (semiMatcher.find())
			comment = semiMatcher.group(1);
			
		//clean it up.
		comment = comment.trim();
		comment = comment.replace('|', '\n');

		//echo it?
		//if (comment.length() > 0)
		//	System.out.println(comment);
	}
	
	private void stripComments()
	{
		Matcher parenMatcher = parenPattern.matcher(command);
		command = parenMatcher.replaceAll("");

		Matcher semiMatcher = semiPattern.matcher(command);
		command = semiMatcher.replaceAll("");
	}
	
	public String getCommand()
	{
		return new String(command);
	}
	
	/**
	 * Actually execute the GCode we just parsed.
	 */
	public void execute(Driver driver) throws GCodeException
	{
		// Select our tool?
		if (hasCode("T"))
			driver.selectTool((int)getCodeValue("T"));
			
		// Set spindle speed?
		if (hasCode("S"))
			driver.currentTool().setSpindleSpeed((int)getCodeValue("S"));
			
		//execute our other codes
		executeMCodes(driver);
		executeGCodes(driver);
	}
	
	private void executeMCodes(Driver driver) throws GCodeException
	{
		//find us an m code.
		if (hasCode("M"))
		{
			switch ((int)getCodeValue("M"))
			{
				//stop codes... handled by handleStops();
				case 0:
				case 1:
				case 2:
					break;
					
				//spindle on, CW
				case 3:
					driver.currentTool().setSpindleDirection(ToolDriver.MOTOR_CLOCKWISE);
					driver.currentTool().enableSpindle();
					break;
					
				//spindle on, CCW
				case 4:
					driver.currentTool().setSpindleDirection(ToolDriver.MOTOR_COUNTER_CLOCKWISE);
					driver.currentTool().enableSpindle();
					break;
					
				//spindle off
				case 5:
					driver.currentTool().disableSpindle();
					break;
					
				//tool change
				case 6:
					if (hasCode("T"))
						driver.requestToolChange((int)getCodeValue("T"));
					else
						throw new GCodeException("The T parameter is required for tool changes. (M6)");
					break;

				//coolant A on (flood coolant)
				case 7:
					driver.currentTool().enableFloodCoolant();
					break;

				//coolant B on (mist coolant)
				case 8:
					driver.currentTool().enableMistCoolant();
					break;
				
				//all coolants off
				case 9:
					driver.currentTool().disableFloodCoolant();
					driver.currentTool().disableMistCoolant();
					break;
					
				//close clamp
				case 10:
					if (hasCode("Q"))
						driver.closeClamp((int)getCodeValue("Q"));
					else
						throw new GCodeException("The Q parameter is required for clamp operations. (M10)");
					break;

				//open clamp
				case 11:
					if (hasCode("Q"))
						driver.openClamp((int)getCodeValue("Q"));
					else
						throw new GCodeException("The Q parameter is required for clamp operations. (M10)");
					break;

				//spindle CW and coolant A on
				case 13:
					driver.currentTool().setSpindleDirection(ToolDriver.MOTOR_CLOCKWISE);
					driver.currentTool().enableSpindle();
					driver.currentTool().enableFloodCoolant();
					break;

				//spindle CW and coolant A on
				case 14:
					driver.currentTool().setSpindleDirection(ToolDriver.MOTOR_COUNTER_CLOCKWISE);
					driver.currentTool().enableSpindle();
					driver.currentTool().enableFloodCoolant();
					break;
					
				//enable drives
				case 17:
					driver.enableDrives();
					break;
					
				//disable drives
				case 18:
					driver.disableDrives();
					break;
					
				//open collet
				case 21:
					driver.currentTool().openCollet();

				//open collet
				case 22:
					driver.currentTool().closeCollet();

				// M40-M46 = change gear ratios
				case 40:
					driver.changeGearRatio(0);
					break;
				case 41:
					driver.changeGearRatio(1);
					break;
				case 42:
					driver.changeGearRatio(2);
					break;
				case 43:
					driver.changeGearRatio(3);
					break;
				case 44:
					driver.changeGearRatio(4);
					break;
				case 45:
					driver.changeGearRatio(5);
					break;
				case 46:
					driver.changeGearRatio(6);
					break;
					
				//M48, M49: i dont understand them yet.
				
				//read spindle speed
				case 50:
					driver.currentTool().readSpindleSpeed();
					
				//subroutine functions... will implement later
				//case 97: jump
				//case 98: jump to subroutine
				//case 99: return from sub
						
				//turn extruder on, forward
				case 101:
					driver.currentTool().setMotorDirection(ToolDriver.MOTOR_CLOCKWISE);
					driver.currentTool().enableMotor();
					break;

				//turn extruder on, reverse
				case 102:
					driver.currentTool().setMotorDirection(ToolDriver.MOTOR_COUNTER_CLOCKWISE);
					driver.currentTool().enableMotor();
					break;

				//turn extruder off
				case 103:
					driver.currentTool().disableMotor();
					break;

				//custom code for temperature control
				case 104:
					if (hasCode("S"))
						driver.currentTool().setTemperature(getCodeValue("S"));
					break;

				//custom code for temperature reading
				case 105:
					driver.currentTool().readTemperature();
					break;

				//turn fan on
				case 106:
					driver.currentTool().enableFan();					
					break;

					//turn fan off
				case 107:
					driver.currentTool().disableFan();					
					break;

				//set max extruder speed, RPM
				case 108:
					driver.currentTool().setMotorSpeed(getCodeValue("S"));
					break;
				
				//valve open
				case 126:
					driver.currentTool().openValve();
					break;

				//valve close
				case 127:
					driver.currentTool().closeValve();
					break;

				default:
					throw new GCodeException("Unknown M code: M" + (int)getCodeValue("M"));
			}
		}
	}
	
	private void executeGCodes(Driver driver) throws GCodeException
	{
		//start us off at our current position...
		Point3d temp = driver.getCurrentPosition();
		
		//absolute just specifies the new position
		if (absoluteMode)
		{
			if (hasCode("X"))
				temp.x = getCodeValue("X");
			if (hasCode("Y"))
				temp.y = getCodeValue("Y");
			if (hasCode("Z"))
				temp.z = getCodeValue("Z");
		}
		//relative specifies a delta
		else
		{
			if (hasCode("X"))
				temp.x += getCodeValue("X");
			if (hasCode("Y"))
				temp.y += getCodeValue("Y");
			if (hasCode("Z"))
				temp.z += getCodeValue("Z");
		}

		// Get feedrate if supplied
		if (hasCode("F"))
		{
			feedrate = getCodeValue("F");
			driver.setFeedrate(feedrate);
		}

		//did we get a gcode?
		if (hasCode("G"))
		{
			switch ((int)getCodeValue("G"))
			{
				//Linear Interpolation
				//these are basically the same thing.
				case 0:
					driver.setFeedrate(maximumFeedrate);
					setTarget(temp, driver);
					break;

				//Rapid Positioning
				case 1:
					//set our target.
					driver.setFeedrate(feedrate);
					setTarget(temp, driver);
					break;

				//Clockwise arc
				case 2:
				//Counterclockwise arc
				case 3:
				{
					Point3d center = new Point3d();

					// Centre coordinates are always relative
					if (hasCode("I"))
						center.x = current.x + getCodeValue("I");
					else
						center.x = current.x;
					
					if (hasCode("J"))
						center.y = current.y + getCodeValue("J");
					else
						center.y = current.y;

					double angleA, angleB, angle, radius, length, aX, aY, bX, bY;

					aX = current.x - center.x;
					aY = current.y - center.y;
					bX = temp.x - center.x;
					bY = temp.y - center.y;

					// Clockwise
					if ((int)getCodeValue("G") == 2)
					{
						angleA = Math.atan2(bY, bX);
						angleB = Math.atan2(aY, aX);
					}
					// Counterclockwise
					else
					{
						angleA = Math.atan2(aY, aX);
						angleB = Math.atan2(bY, bX);
					}

					// Make sure angleB is always greater than angleA
					// and if not add 2PI so that it is (this also takes
					// care of the special case of angleA == angleB,
					// ie we want a complete circle)
					if (angleB <= angleA)
						angleB += 2 * Math.PI;
					angle = angleB - angleA;

					radius = Math.sqrt(aX * aX + aY * aY);
					length = radius * angle;
				
					int steps, s, step;

					// Maximum of either 2.4 times the angle in radians
					// or the length of the curve divided by the constant
					// specified in _init.pde
					steps = (int) Math.ceil(Math.max(angle * 2.4, length / curveSection));

					Point3d newPoint = new Point3d();
					double arc_start_z = current.z;
					for (s = 1; s <= steps; s++)
					{
						if (getCodeValue("G") == 3)
							step = s;
						// Work backwards for CW
						else
							step = steps - s;

						//calculate our waypoint.
						newPoint.x = center.x + radius * Math.cos(angleA + angle * ((double) step / steps));
						newPoint.y = center.y + radius * Math.sin(angleA + angle * ((double) step / steps));
						newPoint.z = arc_start_z + (temp.z - arc_start_z) * s / steps;

						//start the move
						setTarget(newPoint, driver);
					}
				}
				break;

				//Inches for Units
				case 20:
					units = UNITS_INCHES;
					curveSection = curveSectionInches;
					break;

				//mm for Units
				case 21:
					units = UNITS_MM;
					curveSection = curveSectionMM;
					break;

				//go home to your limit switches
				case 28:
					//home all axes?
					if (hasCode("X") && hasCode("Y") && hasCode("Z"))
						driver.homeXYZ();
					else
					{
						//x and y?
						if (hasCode("X") && hasCode("Y"))
							driver.homeXY();
						//just x?
						else if (hasCode("X"))
							driver.homeX();
						//just y?
						else if (hasCode("Y"))
							driver.homeY();
						//just z?
						else if (hasCode("Z"))
							driver.homeZ();
					}
					break;

				// Drilling canned cycles
				case 81: // Without dwell
				case 82: // With dwell
				case 83: // Peck drilling
				
					double retract = getCodeValue("R");

					if (!absoluteMode)
						retract += current.z;

					// Retract to R position if Z is currently below this
					if (current.z < retract)
					{
						driver.setFeedrate(maximumFeedrate);
						setTarget(new Point3d(current.x, current.y, retract), driver);
					}
					
					// Move to start XY
					driver.setFeedrate(maximumFeedrate);
					setTarget(new Point3d(temp.x, temp.y, current.z), driver);

					// Do the actual drilling
					double target_z = retract;
					double delta_z;

					// For G83 move in increments specified by Q code
					// otherwise do in one pass
					if ((int)getCodeValue("G") == 83)
						delta_z = getCodeValue("Q");
					else
						delta_z = retract - temp.z;

					do
					{
						// Move rapidly to bottom of hole drilled so far
						// (target Z if starting hole)
						driver.setFeedrate(maximumFeedrate);
						setTarget(new Point3d(temp.x, temp.y, target_z), driver);

						// Move with controlled feed rate by delta z
						// (or to bottom of hole if less)
						target_z -= delta_z;
						if (target_z < temp.z)
							target_z = temp.z;
						
						driver.setFeedrate(feedrate);
						setTarget(new Point3d(temp.x, temp.y, target_z), driver);

						// Dwell if doing a G82
						if ((int)getCodeValue("G") == 82)
							driver.delay((int)getCodeValue("P"));

						// Retract
						driver.setFeedrate(maximumFeedrate);
						
						setTarget(new Point3d(temp.x, temp.y, retract), driver);
					} while (target_z > temp.z);
					break;

				case 90: //Absolute Positioning
					absoluteMode = true;
					break;


				case 91: //Incremental Positioning
					absoluteMode = false;
					break;


				case 92: //Set as home
					current = new Point3d();
					driver.setCurrentPosition(new Point3d());
					break;

				default:
					throw new GCodeException("Unknown G code: G" + (int)getCodeValue("G"));
			}
		}
	}
	
	private void setTarget(Point3d p, Driver driver)
	{
		driver.queuePoint(p);
		current = p;
	}

	public void handleStops() throws JobRewindException, JobEndException, JobCancelledException
	{
		String message = "";
		int result = 0;
		int mCode = (int)getCodeValue("M");
		
		if (mCode == 0)
		{
			if (comment.length() > 0)
				message = "Automatic Halt: " + comment;
			else
				message = "Automatic Halt";
				
			if (!showContinueDialog(message))
				throw new JobCancelledException();
		}
		else if (mCode == 1 && Preferences.getBoolean("machine.optionalstops"))
		{
			if (comment.length() > 0)
				message = "Optional Halt: " + comment;
			else
				message = "Optional Halt";

			if (!showContinueDialog(message))
				throw new JobCancelledException();
		}
		else if (mCode == 2)
		{
			if (comment.length() > 0)
				message = "Program End: " + comment;
			else
				message = "Program End";
		
			JOptionPane.showMessageDialog(null, message);
			
			throw new JobEndException();
		}
		else if (mCode == 30)
		{
			if (comment.length() > 0)
				message = "Program Rewind: " + comment;
			else
				message = "Program Rewind";
		
			if (!showContinueDialog(message))
				throw new JobCancelledException();
			
			cleanup();
			throw new JobRewindException();
		}
	}
	
	protected boolean showContinueDialog(String message)
	{
		int result = JOptionPane.showConfirmDialog(null, message, "Continue Build?", JOptionPane.YES_NO_OPTION);
		
		if (result == JOptionPane.YES_OPTION)
			return true;
		else
			return false;
	}

	/**
	 * Prepare us for the next gcode command to come in.
	 */
	public void cleanup()
	{
		//move us to our target.
		current = target;
		delta = new Point3d();
		
		//save our gcode
		if (hasCode("G"))
			lastGCode = (int)getCodeValue("G");

		//clear our gcodes.
		codeValues.clear();
		seenCodes.clear();
		
		//empty comments		
		comment = "";
	}
}
