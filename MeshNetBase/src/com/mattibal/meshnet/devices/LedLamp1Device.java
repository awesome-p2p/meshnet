package com.mattibal.meshnet.devices;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import com.mattibal.meshnet.Device;
import com.mattibal.meshnet.utils.color.AbsoluteColor;
import com.mattibal.meshnet.utils.color.Chromaticity;
import com.mattibal.meshnet.utils.color.LightSource;
import com.mattibal.meshnet.utils.color.MulticolorSourceCalculator;
import com.mattibal.meshnet.utils.color.gui.ChromaticityJFrame;
import com.mattibal.meshnet.utils.color.gui.ChromaticityUCSJFrame;
import com.mattibal.meshnet.utils.color.gui.CieXYZColorSelectedListener;
import com.mattibal.meshnet.utils.color.gui.CiexyYColorSelectedListener;
import com.mattibal.meshnet.utils.color.gui.HuslChooserJFrame;
import com.mattibal.meshnet.utils.color.gui.LabChooserJFrame;
import com.mattibal.meshnet.utils.color.gui.PlanckianLocusJFrame;

/**
 * This is a lamp made with very high power RGBAW LEDs.
 * 
 * The circuit has also a temperature sensor and a light sensor.
 */
public class LedLamp1Device extends Device {

	public static final int DEVICE_TYPE = 91235; 
	
	private static final int SET_RGBAW_LEDS_PWM_COMMAND = 1;
	
	
	private MulticolorSourceCalculator colorCalc;
	private LightSource red;
	private LightSource green;
	private LightSource blue;
	private LightSource amber;
	private LightSource white;
	
	private ChromaticityJFrame frame;
	
	
	public LedLamp1Device(int uniqueDeviceId) {
		super(uniqueDeviceId, DEVICE_TYPE);
		initColorCalculator();
	}
	
	
	/**
	 * Set the color that the LED lamp should produce.
	 */
	public synchronized void setColor(AbsoluteColor color) throws IOException{
		
		HashMap<LightSource, Double> map = colorCalc.getSourceLumiForColor(color);
		Set<Entry<LightSource, Double>> entries = map.entrySet();
		int r=0, g=0, b=0, a=0, w=0;
		for(Entry<LightSource, Double> entry : entries){
			LightSource source = entry.getKey();
			double lumi = entry.getValue();
			int pwm = source.getPwmValue(lumi, 255);
			if(source==red){
				r = pwm;
			} else if(source==green){
				g = pwm;
			} else if(source==blue){
				b = pwm;
			} else if(source==amber){
				a = pwm;
			} else if(source==white){
				w = pwm;
			}
		}
		setLedPwmState(r, b, g, a, w); // TODO wrong order in my prototype wirings!! 
		//setLedPwmState(255, 255, 255, 255, 255);
	}
	
	
	
	/**
	 * Sets the PWM duty cycle of each led (in the 0-254 range)
	 */
	public synchronized void setLedPwmState(int red, int green, int blue,
				int amber, int white) throws IOException{
		System.out.println("Setting pwm state: r="+red+" g="+green+" b="+blue+" a="+amber+" w="+white);
		ByteBuffer data = ByteBuffer.allocate(5);
		data.order(ByteOrder.LITTLE_ENDIAN);
		data.put((byte)(red & 0xFF));
		data.put((byte)(green & 0xFF));
		data.put((byte)(blue & 0xFF));
		data.put((byte)(amber & 0xFF));
		data.put((byte)(white & 0xFF));
		sendCommand(SET_RGBAW_LEDS_PWM_COMMAND, data.array());
	}
	
	
	/**
	 * Initializes the color calculation objects with the settings needed by
	 * the LEDs of this lamp.
	 */
	private void initColorCalculator(){
		
		// Create the LightSource object, one for each led type
		
		// LedEngin LZ9-K0WW00-0030 White LED - 2,1 Ampere total
		// http://www.ledengin.com/files/products/LZ9/LZ9-00WW00.pdf
		white = new LightSource(0.434, 0.403, 1350 / 2); // TODO this is for 700 mA!!
		
		// LedEngin LZ4-20MA00-0000 RGBA LED - 700 mA for each LED
		// http://www.ledengin.com/files/products/LZ4/LZ4-00MA00.pdf
		// Chromaticity data taken from:
		// http://www.nichia.co.jp/en/product/led_color.html
		red = new LightSource(0.68, 0.3, 100 * 4);   // 4 LEDs
		green = new LightSource(0.18, 0.7, 160 * 4); // 4 LEDs
		blue = new LightSource(0.13, 0.06, 30 * 4);  // 4 LEDs
		amber = new LightSource(0.57, 0.43, 90 * 2); // 2 LEDs
		
		// Sources in order of decrescent priority
		LightSource[] sources = {
			white, green, red, blue, amber
		};
		
		colorCalc = new MulticolorSourceCalculator(sources);
		
		
		frame = new ChromaticityJFrame(new CiexyYColorSelectedListener() {
			@Override
			public void onCiexyYColorSelected(double x, double y, double Y) {
				try {
					// Set the color I clicked
					setColor(new AbsoluteColor(new Chromaticity(x, y), Y));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		frame.setVisible(true);
		// Display light sources chromaticities
		for(LightSource s : sources){
			frame.addChromaticityPoint(s.getx(), s.gety());
		}
		
		
		// LAB color chooser frame
		LabChooserJFrame frame2 = new LabChooserJFrame(new CieXYZColorSelectedListener() {
			@Override
			public void onCieXYZColorSelected(double X, double Y, double Z) {
				try {
					double[] XYZ = {X, Y, Z};
					// XYZ to xyY conversion
					double x = XYZ[0]/(XYZ[0]+XYZ[1]+XYZ[2]);
					double y = XYZ[1]/(XYZ[0]+XYZ[1]+XYZ[2]);
					Y = Y*10;
					setColor(new AbsoluteColor(new Chromaticity(x, y), Y));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		frame2.setVisible(true);
		
		
		// LAB color chooser frame
		HuslChooserJFrame frame3 = new HuslChooserJFrame(new CieXYZColorSelectedListener() {
			@Override
			public void onCieXYZColorSelected(double X, double Y, double Z) {
				try {
					double[] XYZ = {X, Y, Z};
					// XYZ to xyY conversion
					double x = XYZ[0]/(XYZ[0]+XYZ[1]+XYZ[2]);
					double y = XYZ[1]/(XYZ[0]+XYZ[1]+XYZ[2]);
					Y = Y*5000;
					setColor(new AbsoluteColor(new Chromaticity(x, y), Y));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
		frame3.setVisible(true);
		
		
		ChromaticityUCSJFrame frame4 = new ChromaticityUCSJFrame(new CiexyYColorSelectedListener() {
			@Override
			public void onCiexyYColorSelected(double x, double y, double Y) {
				try {
					// Set the color I clicked
					setColor(new AbsoluteColor(new Chromaticity(x, y), Y));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		frame4.setVisible(true);
		// Display light sources chromaticities
		for(LightSource s : sources){
			frame4.addChromaticityPoint(s.getx(), s.gety());
		}
		
		
		// Show Planckian Locus selector
		PlanckianLocusJFrame planckFrame = new PlanckianLocusJFrame(new CiexyYColorSelectedListener() {
			@Override
			public void onCiexyYColorSelected(double x, double y, double Y) {
				try {
					// Set the color I clicked
					setColor(new AbsoluteColor(new Chromaticity(x, y), Y));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		planckFrame.setVisible(true);
	}

}
