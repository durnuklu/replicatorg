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
 */

package replicatorg.app;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import replicatorg.app.syntax.SyntaxStyle;
import replicatorg.app.ui.MainWindow;

// TODO change this to use the Java Preferences API
// http://www.onjava.com/pub/a/onjava/synd/2001/10/17/j2se.html
// http://www.particle.kth.se/~lindsey/JavaCourse/Book/Part1/Java/Chapter10/Preferences.html

/**
 * 
 */
public class Preferences {

	// what to call the feller

	static final String PREFS_FILE = "preferences.txt";

	/**
	 * Standardized width for buttons. Mac OS X 10.3 wants 70 as its default,
	 * Windows XP needs 66, and Linux needs 76, so 76 seems proper.
	 */
	static public int BUTTON_WIDTH = 76;

	/**
	 * Standardized button height. Mac OS X 10.3 (Java 1.4) wants 29, presumably
	 * because it now includes the blue border, where it didn't in Java 1.3.
	 * Windows XP only wants 23 (not sure what default Linux would be). Because
	 * of the disparity, on Mac OS X, it will be set inside a static block.
	 */
	static public int BUTTON_HEIGHT = 24;

	/*
	 * // remove this for 0121, because quaqua takes care of it static { if
	 * (Base.isMacOS()) BUTTON_HEIGHT = 29; }
	 */

	// value for the size bars, buttons, etc
	static public final int GRID_SIZE = 33;

	// indents and spacing standards. these probably need to be modified
	// per platform as well, since macosx is so huge, windows is smaller,
	// and linux is all over the map

	static public final int GUI_BIG = 13;

	static public final int GUI_BETWEEN = 10;

	static public final int GUI_SMALL = 6;

	// gui elements

	// JDialog dialog;
	JFrame dialog;

	int wide, high;

	JTextField sketchbookLocationField;

	JCheckBox exportSeparateBox;

	JCheckBox sketchPromptBox;

	JCheckBox sketchCleanBox;

	JCheckBox externalEditorBox;

	JCheckBox memoryOverrideBox;

	JTextField memoryField;

	JTextField fontSizeField;

	// the calling editor, so updates can be applied
	MainWindow editor;

	// data model
	// we have multiple preference files, one main one and a few subsidiary
	// ones with prefixes. the preferences from the main file go in table
	// and are saved back to the main file. the preferences from the
	// subsidiary files are stored in prefixes (which maps a prefix string to
	// a Hashtable mapping unprefixed keys to values) and are not saved.
	static Hashtable<String, String> table = new Hashtable<String, String>();

	static Hashtable<String, Hashtable<String, String>> prefixes = new Hashtable<String, Hashtable<String, String>>();

	static File preferencesFile;

	static public void init() {

		// start by loading the defaults, in case something
		// important was deleted from the user prefs

		try {
			load(Base.getStream(PREFS_FILE));

		} catch (Exception e) {
			Base.showError(null, "Could not read default settings.\n"
					+ "You'll need to reinstall ReplicatorG.", e);
		}

		// check for platform-specific properties in the defaults

		String platformExtension = "."
				+ Base.platform.name().toLowerCase();
		
		int extensionLength = platformExtension.length();

		Enumeration e = table.keys(); // properties.propertyNames();
		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			if (key.endsWith(platformExtension)) {
				// this is a key specific to a particular platform
				String actualKey = key.substring(0, key.length()
						- extensionLength);
				String value = get(key);
				table.put(actualKey, value);
			}
		}

		// other things that have to be set explicitly for the defaults

		setColor("run.window.bgcolor", SystemColor.control);

		// next load user preferences file

		preferencesFile = LegacyPrefs.getSettingsFile(PREFS_FILE);

		if (!preferencesFile.exists()) {
			// create a new preferences file if none exists
			// saves the defaults out to the file
			save();

		} else {
			// load the previous preferences file

			try {
				load(new FileInputStream(preferencesFile));

			} catch (Exception ex) {
				Base.showError("Error reading preferences",
						"Error reading the preferences file. "
								+ "Please delete (or move)\n"
								+ preferencesFile.getAbsolutePath()
								+ " and restart ReplicatorG.", ex);
			}
		}
	}

	public Preferences() {

		// setup dialog for the prefs

		// dialog = new JDialog(editor, "Preferences", true);
		dialog = new JFrame("Preferences");
		dialog.setResizable(false);

		Container pain = dialog.getContentPane();
		pain.setLayout(null);

		int top = GUI_BIG;
		int left = GUI_BIG;
		int right = 0;

		JLabel label;
		JButton button;
		Dimension d, d2;
		int h, vmax;

		// [ ] Prompt for name and folder when creating new sketch

		sketchPromptBox = new JCheckBox(
				"Prompt for name when opening or creating a sketch");
		pain.add(sketchPromptBox);
		d = sketchPromptBox.getPreferredSize();
		sketchPromptBox.setBounds(left, top, d.width, d.height);
		right = Math.max(right, left + d.width);
		top += d.height + GUI_BETWEEN;

		// [ ] Delete empty sketches on Quit

		sketchCleanBox = new JCheckBox("Delete empty sketches on Quit");
		pain.add(sketchCleanBox);
		d = sketchCleanBox.getPreferredSize();
		sketchCleanBox.setBounds(left, top, d.width, d.height);
		right = Math.max(right, left + d.width);
		top += d.height + GUI_BETWEEN;

		// Sketchbook location:
		// [...............................] [ Browse ]

		label = new JLabel("Sketchbook location:");
		pain.add(label);
		d = label.getPreferredSize();
		label.setBounds(left, top, d.width, d.height);
		top += d.height; // + GUI_SMALL;

		sketchbookLocationField = new JTextField(40);
		pain.add(sketchbookLocationField);
		d = sketchbookLocationField.getPreferredSize();

		button = new JButton("Browse");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				/*
				 * JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new
				 * File(sketchbookLocationField.getText()));
				 * fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				 * 
				 * int returned = fc.showOpenDialog(new JDialog()); if (returned ==
				 * JFileChooser.APPROVE_OPTION) { File file =
				 * fc.getSelectedFile();
				 * sketchbookLocationField.setText(file.getAbsolutePath()); }
				 */
				File dflt = new File(sketchbookLocationField.getText());
				File file = Base.selectFolder("Select new sketchbook location",
						dflt, dialog);
				if (file != null) {
					sketchbookLocationField.setText(file.getAbsolutePath());
				}
			}
		});
		pain.add(button);
		d2 = button.getPreferredSize();

		// take max height of all components to vertically align em
		vmax = Math.max(d.height, d2.height);
		// label.setBounds(left, top + (vmax-d.height)/2,
		// d.width, d.height);

		// h = left + d.width + GUI_BETWEEN;
		sketchbookLocationField.setBounds(left, top + (vmax - d.height) / 2,
				d.width, d.height);
		h = left + d.width + GUI_SMALL; // GUI_BETWEEN;
		button.setBounds(h, top + (vmax - d2.height) / 2, d2.width, d2.height);

		right = Math.max(right, h + d2.width + GUI_BIG);
		top += vmax + GUI_BETWEEN;

		// MainWindow font size [ ]

		Container box = Box.createHorizontalBox();
		label = new JLabel("MainWindow font size: ");
		box.add(label);
		fontSizeField = new JTextField(4);
		box.add(fontSizeField);
		label = new JLabel("  (requires restart of ReplicatorG)");
		box.add(label);
		pain.add(box);
		d = box.getPreferredSize();
		box.setBounds(left, top, d.width, d.height);
		Font editorFont = Preferences.getFont("editor.font");
		fontSizeField.setText(String.valueOf(editorFont.getSize()));
		top += d.height + GUI_BETWEEN;

		// [ ] Use external editor

		externalEditorBox = new JCheckBox("Use external editor");
		pain.add(externalEditorBox);
		d = externalEditorBox.getPreferredSize();
		externalEditorBox.setBounds(left, top, d.width, d.height);
		right = Math.max(right, left + d.width);
		top += d.height + GUI_BETWEEN;

		// More preferences are in the ...

		label = new JLabel(
				"More preferences can be edited directly in the file");

		pain.add(label);
		d = label.getPreferredSize();
		label.setForeground(Color.gray);
		label.setBounds(left, top, d.width, d.height);
		right = Math.max(right, left + d.width);
		top += d.height; // + GUI_SMALL;

		label = new JLabel(preferencesFile.getAbsolutePath());
		pain.add(label);
		d = label.getPreferredSize();
		label.setBounds(left, top, d.width, d.height);
		right = Math.max(right, left + d.width);
		top += d.height;

		label = new JLabel("(edit only when ReplicatorG is not running)");
		pain.add(label);
		d = label.getPreferredSize();
		label.setForeground(Color.gray);
		label.setBounds(left, top, d.width, d.height);
		right = Math.max(right, left + d.width);
		top += d.height; // + GUI_SMALL;

		// [ OK ] [ Cancel ] maybe these should be next to the message?

		button = new JButton("OK");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				applyFrame();
				disposeFrame();
			}
		});
		pain.add(button);
		d2 = button.getPreferredSize();
		BUTTON_HEIGHT = d2.height;

		h = right - (BUTTON_WIDTH + GUI_SMALL + BUTTON_WIDTH);
		button.setBounds(h, top, BUTTON_WIDTH, BUTTON_HEIGHT);
		h += BUTTON_WIDTH + GUI_SMALL;

		button = new JButton("Cancel");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				disposeFrame();
			}
		});
		pain.add(button);
		button.setBounds(h, top, BUTTON_WIDTH, BUTTON_HEIGHT);

		top += BUTTON_HEIGHT + GUI_BETWEEN;

		// finish up

		wide = right + GUI_BIG;
		high = top + GUI_SMALL;
		// setSize(wide, high);

		// closing the window is same as hitting cancel button

		dialog.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				disposeFrame();
			}
		});

		ActionListener disposer = new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				disposeFrame();
			}
		};
		Base.registerWindowCloseKeys(dialog.getRootPane(), disposer);

		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		dialog.setLocation((screen.width - wide) / 2,
				(screen.height - high) / 2);

		dialog.pack(); // get insets
		Insets insets = dialog.getInsets();
		dialog.setSize(wide + insets.left + insets.right, high + insets.top
				+ insets.bottom);

		// handle window closing commands for ctrl/cmd-W or hitting ESC.

		pain.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				KeyStroke wc = MainWindow.WINDOW_CLOSE_KEYSTROKE;
				if ((e.getKeyCode() == KeyEvent.VK_ESCAPE)
						|| (KeyStroke.getKeyStrokeForEvent(e).equals(wc))) {
					disposeFrame();
				}
			}
		});
	}

	/*
	 * protected JRootPane createRootPane() { System.out.println("creating root
	 * pane esc received");
	 * 
	 * ActionListener actionListener = new ActionListener() { public void
	 * actionPerformed(ActionEvent actionEvent) { //setVisible(false);
	 * System.out.println("esc received"); } };
	 * 
	 * JRootPane rootPane = new JRootPane(); KeyStroke stroke =
	 * KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
	 * rootPane.registerKeyboardAction(actionListener, stroke,
	 * JComponent.WHEN_IN_FOCUSED_WINDOW); return rootPane; }
	 */

	public Dimension getPreferredSize() {
		return new Dimension(wide, high);
	}

	// .................................................................

	/**
	 * Close the window after an OK or Cancel.
	 */
	public void disposeFrame() {
		dialog.dispose();
	}

	/**
	 * Change internal settings based on what was chosen in the prefs, then send
	 * a message to the editor saying that it's time to do the same.
	 */
	public void applyFrame() {
		// put each of the settings into the table
		setBoolean("sketchbook.prompt", sketchPromptBox.isSelected());
		setBoolean("sketchbook.auto_clean", sketchCleanBox.isSelected());
		set("sketchbook.path", sketchbookLocationField.getText());
		setBoolean("editor.external", externalEditorBox.isSelected());

		String newSizeText = fontSizeField.getText();
		try {
			int newSize = Integer.parseInt(newSizeText.trim());
			String fontName = get("editor.font");
			if (fontName != null) {
				String pieces[] = fontName.split(",");
				pieces[2] = String.valueOf(newSize);
				StringBuffer buf = new StringBuffer();
				for (String piece : pieces) {
					if (buf.length() > 0) buf.append(",");
					buf.append(piece);
				}
				set("editor.font", buf.toString());
			}

		} catch (Exception e) {
			System.err.println("ignoring invalid font size " + newSizeText);
		}
		editor.applyPreferences();
	}

	public void showFrame(MainWindow editor) {
		this.editor = editor;

		// set all settings entry boxes to their actual status
		sketchPromptBox.setSelected(getBoolean("sketchbook.prompt"));
		sketchCleanBox.setSelected(getBoolean("sketchbook.auto_clean"));
		sketchbookLocationField.setText(get("sketchbook.path"));
		externalEditorBox.setSelected(getBoolean("editor.external"));

		dialog.setVisible(true);
	}

	// .................................................................

	static public void load(InputStream input) throws IOException {
		load(input, null);
	}

	static public void load(InputStream input, String prefix)
			throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(input));
		Hashtable<String, String> table = Preferences.table;

		if (prefix != null) {
			table = new Hashtable<String, String>();
			prefixes.put(prefix, table);
		}

		// table = new Hashtable();
		String line = null;
		while ((line = reader.readLine()) != null) {
			if ((line.length() == 0) || (line.charAt(0) == '#'))
				continue;

			// this won't properly handle = signs being in the text
			int equals = line.indexOf('=');
			if (equals != -1) {
				String key = line.substring(0, equals).trim();
				String value = line.substring(equals + 1).trim();
				table.put(key, value);
			}
		}
		reader.close();
	}

	// .................................................................

	static public void save() {
		try {
			FileOutputStream output = new FileOutputStream(preferencesFile);
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(output));

			Enumeration e = table.keys(); // properties.propertyNames();
			while (e.hasMoreElements()) {
				String key = (String) e.nextElement();
				writer.println(key + "=" + ((String) table.get(key)));
			}

			writer.flush();
			writer.close();

		} catch (IOException ex) {
			Base.showWarning(null, "Error while saving the settings file", ex);
			// e.printStackTrace();
		}
	}

	// .................................................................

	// all the information from preferences.txt

	// static public String get(String attribute) {
	// return get(attribute, null);
	// }

	static public String get(String attribute /* , String defaultValue */) {
		// if the attribute starts with a prefix used by one of our subsidiary
		// preference files, look up the attribute in that file's Hashtable
		// (don't override with or fallback to the main file). otherwise,
		// look up the attribute in the main file's Hashtable.
		Hashtable table = Preferences.table;
		if (attribute.indexOf('.') != -1) {
			String prefix = attribute.substring(0, attribute.indexOf('.'));
			if (prefixes.containsKey(prefix)) {
				table = (Hashtable) prefixes.get(prefix);
				attribute = attribute.substring(attribute.indexOf('.') + 1);
			}
		}
		return (String) table.get(attribute);
	}

	/**
	 * Get the top-level key prefixes defined in the subsidiary file loaded with
	 * the given prefix. For example, if the file contains: foo.count=1
	 * bar.count=2 baz.count=3 this will return { "foo", "bar", "baz" }.
	 */
	static public Iterator<String> getSubKeys(String prefix) {
		if (!prefixes.containsKey(prefix))
			return null;
		Set<String> subkeys = new HashSet<String>();
		for (Enumeration e = (prefixes.get(prefix)).keys(); e.hasMoreElements();) {
			String subkey = (String) e.nextElement();
			if (subkey.indexOf('.') != -1)
				subkey = subkey.substring(0, subkey.indexOf('.'));
			subkeys.add(subkey);
		}
		return subkeys.iterator();
	}

	static public void set(String attribute, String value) {
		// preferences.put(attribute, value);
		table.put(attribute, value);
	}

	static public boolean getBoolean(String attribute) {
		String value = get(attribute); // , null);
		return (new Boolean(value)).booleanValue();
	}

	static public void setBoolean(String attribute, boolean value) {
		set(attribute, value ? "true" : "false");
	}

	static public int getInteger(String attribute /* , int defaultValue */) {
		return Integer.parseInt(get(attribute));
	}

	static public void setInteger(String key, int value) {
		set(key, String.valueOf(value));
	}

	static public Color getColor(String name /* , Color otherwise */) {
		Color parsed = null;
		String s = get(name); // , null);
		// System.out.println(name + " = " + s);
		if ((s != null) && (s.indexOf("#") == 0)) {
			try {
				int v = Integer.parseInt(s.substring(1), 16);
				parsed = new Color(v);
			} catch (Exception e) {
			}
		}
		// if (parsed == null) return otherwise;
		return parsed;
	}

	static public void setColor(String attr, Color what) {
		String r = Integer.toHexString(what.getRed());
		String g = Integer.toHexString(what.getGreen());
		String b = Integer.toHexString(what.getBlue());
		set(attr, "#" + r.substring(r.length() - 2)
				+ g.substring(g.length() - 2) + b.substring(b.length() - 2));
	}

	static public Font getFont(String which /* , Font otherwise */) {
		// System.out.println("getting font '" + which + "'");
		String str = get(which);
		// if (str == null) return otherwise; // ENABLE LATER
		StringTokenizer st = new StringTokenizer(str, ",");
		String fontname = st.nextToken();
		String fontstyle = st.nextToken();
		return new Font(fontname,
				((fontstyle.indexOf("bold") != -1) ? Font.BOLD : 0)
						| ((fontstyle.indexOf("italic") != -1) ? Font.ITALIC
								: 0), Integer.parseInt(st.nextToken()));
	}

	static public SyntaxStyle getStyle(String what /* , String dflt */) {
		String str = get("editor." + what + ".style"); // , dflt);

		StringTokenizer st = new StringTokenizer(str, ",");

		String s = st.nextToken();
		if (s.indexOf("#") == 0)
			s = s.substring(1);
		Color color = new Color(Integer.parseInt(s, 16));

		s = st.nextToken();
		boolean bold = (s.indexOf("bold") != -1);
		boolean italic = (s.indexOf("italic") != -1);
		// System.out.println(what + " = " + str + " " + bold + " " + italic);

		return new SyntaxStyle(color, italic, bold);
	}
}
