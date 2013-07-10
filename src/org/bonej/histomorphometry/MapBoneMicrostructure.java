package org.bonej.histomorphometry;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.SystemColor;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

import javax.swing.BoxLayout;
import javax.swing.JTextArea;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.OvalRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.PlugInFrame;
import ij.process.ByteProcessor;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import ij.process.PolygonFiller;

/*******************************************************************************\
|
| ImageJ Plugin Map_BoneMicrostructure
| Author: Vasilis Karantzoulis
| 
| What does it do:
| Calculates standard bone histomorphometry parameters on a digitized trabecular 
| bone surface, and maps the distributions of selected parameters onto the surface
|
\*******************************************************************************/
public class MapBoneMicrostructure extends PlugInFrame implements ActionListener, TextListener, ItemListener{

/*******************************************************************************\
|
| Internal Variables 
|
\*******************************************************************************/
// Variables that refer to the original image	
	public ImagePlus imp;
	int iImageWidth, iImageHeight;	
	ImageCanvas ic;
	String imageTitle;
	ImageProcessor ip;	
	
// ipRectangle represents the bounding Rectangle of the current-Roi processed.
// It must pass through various routines as a static variable
	Rectangle ipRectangle;	

// ip2 and derivatives refer to the copy made for actual image-processing.
// ip2 has the size of the square-ROI that the original image.
// Wand ip2Wand, FloodFiller ff and PolygonFiller pf are created for every instance of ip2
// PolygonFiller pf and FloodFiller ff are needed in order to accurately fill 
// the interior of found particles (see analyzeParticles() for further details
	ImageProcessor ip2;
	Object ip2PixelsCopy;
	Wand ip2Wand;	
	FloodFiller ff;
	PolygonFiller pf;		
	
// The variables level1, level2 define the thresholding values.
// Since this plugin was designed to process black bone-trabeculae on white background,
// as default level1 = 0 
	double level1, level2;	

// Global inteface panel fields
	Panel panelDPIContainer;
		Choice DPI;
		TextField textmm, textPixels;
	
	Panel panelGridContainer;	
		TextField textSqrWidthMM;
		TextField textSqrWidthPix;
		TextField textSqrX;
		TextField textSqrY;
		Button buttonUseCurrentRoi;
		Button buttonCreateSquareRoi;
		
	Panel panelModifiersContainer;
		Checkbox chkboxSmooth;
		Checkbox chkboxAutoThreshold;
		Checkbox chkboxDespeckle;
		TextField textSpeckleSize;
	
	Panel panelMapContainer;
		Button buttonMapAllSquares;
		Button buttonMapCurrentRoi;
		Button buttonMapEntireImage;
		Label labelProgress;
		String txtProgress;		
	
	Panel panelMaskContainer;
		Button buttonGrayscale;
		Choice  grayscaleMapping;
		TextField textGrayscaleMin, textGrayscaleMax;
		String mappingValue;

	
// Global Variables calculated from user-input (from the above panel fields)
	int sqrX, sqrY, sqrWidth; // Coordinates of active Square-Roi
	int sqrXstart, sqrYstart; // Beginning of the splitting process
	int countSqrRois; // Total No of Square-Rois, that the image will be split to
	int iRoisX, iRoisY; // No of Square-Rois in the X and Y axis respectively
	
	double umperpixel, mmperpixel;
	String grayscaleMin, grayscaleMax;
	
	boolean blnSmooth;
	boolean blnAutoThreshold;
	boolean blnDespeckle;
	double speckleSize;
	
// We 'trap' the entries in main panel with textValueChanged(TextEvent)
// for various tasks, such as automatic fill-in in other fields. 
// Unfortunately, Java invokes this call-back even with 'internal' changes.
// The following flag serves to differentiate between user- and automatic field update
	boolean flagTextValueChangeInvokedInternally = false;
	
	
// maskIP holds the mask for the traced particles. It is as big as the original image
// Every traced particle is drawn on maskIP as a mask. 
// At the end of the procedure maskIP should look like the thresholded image 
	ImageProcessor maskIP;

// We define new Roi classes MyRoi, MyOvalRoi, MyPolyRoi to facilitate
// the display of the grid of squares that the image will be split to.
	
	// Definition of an arbitrary Roi-type, hoping nobody else had the same idea ...
	public static final int MYROI = 999; 
	// Flag to enable/block the overriding of MyXXXRoi.draw(..) call-back function
	boolean flagOverrideRoiDraw = true; 
	
	
// Holds the user-selected Roi
	Roi userRoi;
	
// All other variables ...	
	ResultsTable rt;
	Analyzer analyzer;
			
	double particlesTotalArea;
	double particlesTotalPerimeter;
	double currentRoiTotalArea;
	double currentRoiParticlesArea;
	double currentRoiParticlesTotalPerimeter;
	int particlesCount;	
	
	DecimalFormat df;	


	
/***************************************************************************
| 
|  Constructor
|
\**************************************************************************/
	public MapBoneMicrostructure(){
		super("Map Bone Microstructure");
		
		// Dequote to automatically open an image. 
		// Intended for debug purposes, where the plugin is invoked many consecutive times	
		// IJ.open("D:\\BONE-IMAGES\\BoneTrabeculae4800DPI.jpg");		
		
		imp = WindowManager.getCurrentImage();
		if (imp == null) {
			IJ.error("There is no active image.");
			return;
		}
		ip = imp.getProcessor();
		imageTitle = imp.getTitle();
		ic = imp.getCanvas();
		iImageWidth = ip.getWidth();
		iImageHeight = ip.getHeight();
		
		
		// invoke Thresholder
		IJ.run("Threshold...");			
			
		// invoke user interface Panel		
		CreatePanel();
	}

/***************************************************************************
| 
|  Creates the main user interface panel. 
|
\**************************************************************************/
	public void CreatePanel() {

		setBackground(SystemColor.control);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		// ------------------ SECTION WARNING ------------------------
		JTextArea txtWarning = new JTextArea("" +
				"This session of Map_BoneMicrostructure is bound to image: " + imageTitle + "\n" +
				"WARNING: The plugin is designed to process " +
				"8-bit grayscale images with black-coloured bone-trabeculae on " +
				"white background. If your image does not conform, please " +
				"apply the appropriate convertions before using.");
	    txtWarning.setWrapStyleWord(true);
	    txtWarning.setLineWrap(true);
	    txtWarning.setBackground(getBackground());
	    txtWarning.setEditable(false);
		
		add(txtWarning);
		add(new Label(""));
		
		// ------------------ SECTION CALIBRATION ------------------------
		// Panels Structure:
		// panelDPIContainer (GridLayout 1x2)
		//		-> txtDescriptionDPI (text)
		//		-> panelDPI1 (BoxLayout in Y axis)
		//			-> panelDPI (main panel)

		panelDPIContainer = new Panel();
		panelDPIContainer.setLayout(new GridLayout(1, 2, 2, 2));
		
		JTextArea txtDescriptionDPI = new JTextArea("" +
				"STEP 1. CALIBRATE: Select the DPI resolution with which the image was scanned. " +
				"Alternatively, you can manually type the relationship between pixels and millimeters");
	    txtDescriptionDPI.setWrapStyleWord(true);
	    txtDescriptionDPI.setLineWrap(true);
	    txtDescriptionDPI.setBackground(getBackground());
	    txtDescriptionDPI.setEditable(false);
		
		
		Panel panelDPI = new Panel();
		DPI = new Choice();
		DPI.add("4800DPI");
		DPI.add("9600DPI");
		DPI.add("2400DPI");
		DPI.add("Custom");
		panelDPI.add(DPI);
		DPI.addItemListener(this);
		DPI.addFocusListener(this);
			
		textPixels = new TextField(Integer.toString(1));
		panelDPI.add(textPixels);	
		panelDPI.add(new Label("pixels equal:"));
		textPixels.addTextListener(this);
		textPixels.addFocusListener(this);
		
		textmm = new TextField(Double.toString(0.00529));
		panelDPI.add(textmm);		
		panelDPI.add(new Label("mm"));
		textmm.addTextListener(this);
		textmm.addFocusListener(this);
		
		Panel panelDPI1 = new Panel();
		panelDPI1.setLayout(new BoxLayout(panelDPI1, BoxLayout.Y_AXIS));
		panelDPI1.add(panelDPI);
		panelDPI1.add(new Label("")); // Placeholder
		
		panelDPIContainer.add(txtDescriptionDPI);	
		panelDPIContainer.add(panelDPI1);
		
		add(panelDPIContainer);
		add(new Label(""));	
		
		CalculateMMperPixel();
		
		
		// -------------------SECTION GRID OF SQUARES -----------------
		// Panels Structure:
		// panelGridContainer (GridLayout 1x2)
		//		-> txtDescriptionGrid (text)
		//		-> panelGrid1 (BoxLayout in Y axis)
		//			-> Label("Coordinates of Square-Roi")
		//			-> panelSqrLine1 (GridLayout 1x4)
		//				-> textSqrX  | textSqrWidthPix
		//			-> panelSqrLine2 (GridLayout 1x4)
		//				-> textSqrY  | textSqrWidthMM
		//			-> panelButtonGrid (GridLayout 1x2)
		//				-> buttonUseCurrentRoi | buttonCreateSquareRoi
		
		panelGridContainer = new Panel();
		panelGridContainer.setLayout(new GridLayout(1, 2, 2, 2));
		
		JTextArea txtDescriptionGrid = new JTextArea("" +
				"STEP 2. CREATE GRID OF SQUARES: Drag a Roi (Rectangle, Oval, Freeline) " +
				"or define the coordinates of a Square-Roi. " +
				"A grid of equal square-ROIs can be generated, " +
				"to which the image will be split and 'mapped'" +
				"as to the above basic bone-microstructure parameters.");
	    txtDescriptionGrid.setWrapStyleWord(true);
	    txtDescriptionGrid.setLineWrap(true);
	    txtDescriptionGrid.setBackground(getBackground());
	    txtDescriptionGrid.setEditable(false);
		
		
		
		Panel panelSqrLine1 = new Panel();
		panelSqrLine1.setLayout(new GridLayout(1,4,2,2));	
		panelSqrLine1.add(new Label("X (pix):", Label.RIGHT));
		sqrX = 0; //initial value
		textSqrX = new TextField(Integer.toString(sqrX));
		panelSqrLine1.add(textSqrX);
		textSqrX.addTextListener(this);
		textSqrX.addFocusListener(this);
		panelSqrLine1.add(new Label("Width(pix):", Label.RIGHT));
		// Define the maximum-width square within the image
		if (iImageWidth < iImageHeight)
		{
			sqrWidth = iImageWidth;
		} else {
			sqrWidth = iImageHeight;
		}
		textSqrWidthPix = new TextField(Integer.toString(sqrWidth));
		panelSqrLine1.add(textSqrWidthPix);
		textSqrWidthPix.addTextListener(this);	
		textSqrWidthPix.addFocusListener(this);
		

		// This plug-in was written on a greek-PC, so I decided to force Double output
		// in US-format #.## and not the traditional greek format #,## (;-)
		// df.format(some_double_value) produces a US-compatible output 
		// on all international machines (even mine)
	    Locale loc = Locale.US;  
		NumberFormat nf = NumberFormat.getNumberInstance(loc);		
	    df = (DecimalFormat)nf;
	    df.applyPattern("#.##"); 			
		
		Panel panelSqrLine2 = new Panel();	
		panelSqrLine2.setLayout(new GridLayout(1,4,2,2));
		panelSqrLine2.add(new Label("Y (pix) :", Label.RIGHT));
		sqrY = 0; //initial value
		textSqrY = new TextField(Integer.toString(sqrY));
		panelSqrLine2.add(textSqrY);
		textSqrY.addTextListener(this);	
		textSqrY.addFocusListener(this);
		panelSqrLine2.add(new Label("Width(mm):", Label.RIGHT));			
		textSqrWidthMM = new TextField(df.format(sqrWidth * mmperpixel));
		panelSqrLine2.add(textSqrWidthMM);
		textSqrWidthMM.addTextListener(this);
		textSqrWidthMM.addFocusListener(this);
	
		
		Panel panelButtonGrid = new Panel();
		panelButtonGrid.setLayout(new GridLayout(1,2,2,2));
   		buttonUseCurrentRoi = new Button("Use Current Roi");
		buttonUseCurrentRoi.addActionListener(this);
		buttonCreateSquareRoi = new Button("Create New Square Roi");
		buttonCreateSquareRoi.addActionListener(this);
		panelButtonGrid.add(buttonUseCurrentRoi);	
		panelButtonGrid.add(buttonCreateSquareRoi);
	
		Panel panelGrid1 = new Panel();
		panelGrid1.setLayout(new BoxLayout(panelGrid1, BoxLayout.Y_AXIS));
		panelGrid1.add(new Label("Coordinates of Square-Roi:"));
		panelGrid1.add(panelSqrLine1);
		panelGrid1.add(panelSqrLine2);
		panelGrid1.add(panelButtonGrid);
		
		panelGridContainer.add(txtDescriptionGrid);	
		panelGridContainer.add(panelGrid1);
			
		add (panelGridContainer);
		add (new Label(""));
		
		
		// ------------------ SECTION MAPPING ------------------------
		// Panels Structure:
		// panelMapContainer (GridLayout 1x2)
		//		-> txtDescriptionMap (text)
		//		-> panelMap1 (BoxLayout Y axis)
		//			-> panelMapButtons1 (GridLayout 1x2)
		//				-> panelMapButtons (GridLayout 3x1)  | panelProgress (GridLayout 3x2)
		//					-> buttonMapAllSquares					-> labelProgress
		//					-> buttonMapCurrentRoi					-> (placeholder)
		//					-> buttonMapEntireImage					-> (placeholder)
		//			-> panelCheckLine1
		//					-> panelCheckSmooth (FlowLayout) | panelCheckAutoThreshold (FlowLayout)
		//						-> chkboxSmooth						-> chkboxAutoThreshold
		//			-> panelCheckDespeckle (FlowLayout)
		//						-> chkboxDespeckle	|  textSpeckleSize
		
		panelMapContainer = new Panel();
		panelMapContainer.setLayout(new GridLayout(1, 2, 2, 2));
		
		JTextArea txtDescriptionMap = new JTextArea("" +
				"STEP 4. CALCULATE the following bone-microstructure values: " +
				"BA/TA (bone area fraction, %), " +
				"TbTh (mean trabecular thickness, �m), " +
				"TbSp (mean trabecular separation, �m), " +
				"TbN (mean trabecular number per length unit, 1/mm). " + "\n" +
				"A results-table is created: each row represents " +
				"a processed ROI. ");		
		
	    txtDescriptionMap.setWrapStyleWord(true);
	    txtDescriptionMap.setLineWrap(true);
	    txtDescriptionMap.setBackground(getBackground());
	    txtDescriptionMap.setEditable(false);
	    
		Panel panelMap1 = new Panel();
		panelMap1.setLayout(new BoxLayout(panelMap1, BoxLayout.Y_AXIS));
	    
	    Panel panelMapButtons1 = new Panel();
	    panelMapButtons1.setLayout(new GridLayout(1,2,2,2));
	    
	    Panel panelMapButtons = new Panel();
	    panelMapButtons.setLayout(new GridLayout(3,1,2,2));	    
		buttonMapAllSquares = new Button("Map All Squares");
		buttonMapAllSquares.addActionListener(this);
		panelMapButtons.add(buttonMapAllSquares);
		buttonMapCurrentRoi = new Button ("Map Current Roi");
		buttonMapCurrentRoi.addActionListener(this);
		panelMapButtons.add(buttonMapCurrentRoi);
		buttonMapEntireImage = new Button ("Map Entire Image");
		buttonMapEntireImage.addActionListener(this);
		panelMapButtons.add(buttonMapEntireImage);
		
		Panel panelProgress = new Panel();
		panelProgress.setLayout(new GridLayout(3, 2, 2, 2));
		panelProgress.add(new Label("Progress:", Label.RIGHT));		
		labelProgress = new Label("0/0");
		panelProgress.add(labelProgress);		
		panelProgress.add(new Label("")); panelProgress.add(new Label("")); // Placeholders
		panelProgress.add(new Label("")); panelProgress.add(new Label("")); 	
		
		panelMapButtons1.add(panelMapButtons);
		panelMapButtons1.add(panelProgress);
		
		Panel panelCheckLine1 = new Panel();
		panelCheckLine1.setLayout(new GridLayout(1,2,1,1));
		
		Panel panelCheckSmooth = new Panel();
		panelCheckSmooth.setLayout(new FlowLayout(FlowLayout.LEADING));
		chkboxSmooth = new Checkbox("Smooth Image", true);
		panelCheckSmooth.add(chkboxSmooth);	
		
		Panel panelCheckAutoThreshold = new Panel();
		panelCheckAutoThreshold.setLayout(new FlowLayout (FlowLayout.LEADING));
		chkboxAutoThreshold = new Checkbox("Autothreshold", true);
		panelCheckAutoThreshold.add(chkboxAutoThreshold);
		
		panelCheckLine1.add(panelCheckSmooth);
		panelCheckLine1.add(panelCheckAutoThreshold);

		Panel panelCheckDespeckle = new Panel();
		panelCheckDespeckle.setLayout(new FlowLayout(FlowLayout.LEADING));
		chkboxDespeckle = new Checkbox("Despeckle Particles with Radius <", true);
		panelCheckDespeckle.add(chkboxDespeckle);
		textSpeckleSize = new TextField("10");
		panelCheckDespeckle.add(textSpeckleSize);
		panelCheckDespeckle.add(new Label("pix"));
		
		panelMap1.add(panelMapButtons1);
		panelMap1.add(panelCheckLine1);
		panelMap1.add(panelCheckDespeckle);
		
	    panelMapContainer.add(txtDescriptionMap);
		panelMapContainer.add(panelMap1);
		
		add(panelMapContainer);
		add(new Label(""));

		// ------------------SECTION GRAYSCALE MASK -------------------
		// Panels Structure: 
		// panelMaskContainer (GridLayout 1x2)
		//		-> txtDescriptionMask (text)
		//		-> panelMask1 (BoxLayout in Y axis)
		//			-> buttonGrayscale (action button)
		//			-> grayscaleMapping (list of parameters)
		//			-> User defined GrayscaleMin and GrayscaleMax values
		//			-> Label("") as place holder
		
		panelMaskContainer = new Panel();
		panelMaskContainer.setLayout(new GridLayout(1, 2, 2, 2));
		
		JTextArea txtDescriptionMask = new JTextArea("" +
				"STEP 4. CREATE A GRAYSCALED DISTRIBUTION of each parameter " +
				"on a thresholded mask of the bone-trabeculae. " +
				"There are predefined values for minimum- (white) and maximum-shading (black).");		
		
	    txtDescriptionMask.setWrapStyleWord(true);
	    txtDescriptionMask.setLineWrap(true);
	    txtDescriptionMask.setBackground(getBackground());
	    txtDescriptionMask.setEditable(false);
	    
	    panelMaskContainer.add(txtDescriptionMask);
		
		Panel panelMask1 = new Panel();
		panelMask1.setLayout(new GridLayout(4, 2, 2, 2));
		buttonGrayscale = new Button("Grayscaled Distribution of ...");
		buttonGrayscale.addActionListener(this);
		panelMask1.add(buttonGrayscale);

		grayscaleMapping = new Choice();
		grayscaleMapping.add("BA/TA (%)");
		grayscaleMapping.add("Tb.Th (�m)");
		grayscaleMapping.add("Tb.Sp (�m)");
		grayscaleMapping.add("Tb.N (1/mm)");
		panelMask1.add(grayscaleMapping);
		grayscaleMapping.addItemListener(this);
		
	
		panelMask1.add(new Label("White Shade for :", Label.RIGHT));
		textGrayscaleMin = new TextField("0");
		panelMask1.add(textGrayscaleMin);	

		panelMask1.add(new Label("Black Shade for :", Label.RIGHT));
		textGrayscaleMax = new TextField("100");
		panelMask1.add(textGrayscaleMax);	
		
		panelMask1.add(new Label(""));
		panelMask1.add(new Label(""));
		
		panelMaskContainer.add(panelMask1);
		
		add(panelMaskContainer);
		add(new Label(""));
		// -----------------END PANEL---------------------
		
		// Initialize the user-depended global values 
		// forcing a dummy TextEvent
		textValueChanged(null);
		
		pack();
		show();
		
	}

/***************************************************************************\
|		
|  The following 3 functions: 
|  textValueChanged(TextEvent), itemStateChanged(ItemEvent), actionPerformed(ActionEvent)
|  are system call-backs invoked whenever a change takes place in text-fields, 
|  choice-fields, or a button is clicked respectively.
|  The call-back function focusGained(FocusEvent) is called whenever the user
|  clicks on one of the trapped-fields.
|
\***************************************************************************/	
	
	public void focusGained(FocusEvent event){
		if (event != null) {
		// User wants to type-in something. Stop the annoying
		// Roi-overriding, or else the Square-Roi fields are 
		// blocked for input by the constant refreshing!
				flagOverrideRoiDraw = false;
		}
	}
	
	public void textValueChanged(TextEvent event)
	
	// Caution: A TextEvent is automatically generated even when a text-field is 
	// modified within the subroutine. This causes an endless loop. To differentiate
	// when textValueChanged is invoked though user-input or internally, we 
	// use the flag textValueChangeInvokedInternally to avoid endless loops
	{
	if (flagTextValueChangeInvokedInternally == true ) {
		//This one no, the next one yes
		flagTextValueChangeInvokedInternally = false;
		return;
	}
	
	// If Roi-trapping is activated, don't bother update any values (endless loop...)	
	if (flagOverrideRoiDraw == true){
		return;
	}

////////////////////////////////////////////////////////////
// Checks entries in Calibration, in pixels and mm
	
	int pixels = Integer.parseInt(textPixels.getText());    	
	double mm = Double.parseDouble(textmm.getText());
	if (mm/pixels  == 0.00529) 
	{
		DPI.select("4800DPI");
	} else if (mm/pixels == 0.010583) 
	{
		DPI.select("2400DPI");
	} else if (mm/pixels == 0.002646)
	{
		DPI.select("9600DPI");
	} else 
	{
		DPI.select("Custom");
	} 
	
	// Refresh the global variables mmperpixel and umperpixel
	if (CalculateMMperPixel() == true) 
	{ // mmperpixel was modified. 
		// Refresh textSqrWidthMM
		flagTextValueChangeInvokedInternally = true;
		textSqrWidthMM.setText(df.format((sqrWidth * mmperpixel)));			
	}
	
    
///////////////////////////////////////////////////////////
// Checks entries in square-Roi Coordinates
    	
	sqrX = Integer.parseInt(textSqrX.getText());
	sqrY = Integer.parseInt(textSqrY.getText());  

	
    // If user typed the width of the square-ROI in mm, then convert to pixels 
	// If user typed the width of the square-Roi in pixels, then convert to mm
	if (event != null) {
		if (event.getSource() == textSqrWidthMM) {
			double iMMRoiWidth = Double.parseDouble(textSqrWidthMM.getText());
			sqrWidth = (int)(iMMRoiWidth/mmperpixel);	
			flagTextValueChangeInvokedInternally = true;
			textSqrWidthPix.setText(Integer.toString(sqrWidth));
		}
		else if (event.getSource() == textSqrWidthPix) {
			sqrWidth = Integer.parseInt(textSqrWidthPix.getText());
			flagTextValueChangeInvokedInternally = true;
			textSqrWidthMM.setText(df.format((sqrWidth * mmperpixel)));				
		}
	} 
	
  	
}

    public void itemStateChanged(ItemEvent e)
    {
//////////////////////////////////////////////////////////////////////
// Checks entries in DPI Choice-field
    	String selection = DPI.getSelectedItem();
        if(e.getSource()== DPI)
        {
	    	if (selection.equals("4800DPI"))
	    	{
	    		textPixels.setText(Integer.toString(1));
	    		textmm.setText(Double.toString(0.00529));
	    	} else if (selection.equals("2400DPI"))
			{
	    		textPixels.setText(Integer.toString(1));
	    		textmm.setText(Double.toString(0.010583));			
			} else if (selection.equals("9600DPI"))
			{
	    		textPixels.setText(Integer.toString(1));
	    		textmm.setText(Double.toString(0.002646));			
			}	
	    	
	    	// Refresh the global variables mmperpixel and umperpixel
	    	if (CalculateMMperPixel() == true) 
	    	{ // mmperpixel was modified. 
	    		// Refresh textSqrWidthMM
	    		flagTextValueChangeInvokedInternally = true;
	    		textSqrWidthMM.setText(df.format((sqrWidth * mmperpixel)));			
	    	}	    	
        } 

////////////////////////////////////////////////////////////////////////////        
// Traps entries in grayscaleMapping
    	mappingValue = grayscaleMapping.getSelectedItem();
        if(e.getSource()== grayscaleMapping)
        {
        	// Predefined grayscaleMin, grayscaleMax for every value selected.
        	if (mappingValue.equals("BA/TA (%)")) 
        	{
        		textGrayscaleMin.setText("0");
        		textGrayscaleMax.setText("100");
        	} else if (mappingValue.equals("Tb.Th (�m)"))
        	{
        		textGrayscaleMin.setText("0");
        		textGrayscaleMax.setText("300");
        	} else if (mappingValue.equals("Tb.Sp (�m)"))
        	{
        		textGrayscaleMin.setText("0");
        		textGrayscaleMax.setText("1000");        		
        	} else if (mappingValue.equals("Tb.N (1/mm)"))
        	{
        		textGrayscaleMin.setText("0");
        		textGrayscaleMax.setText("5");        		
        	}
        	
        	// Refresh screen
    		Graphics panelg = panelMaskContainer.getGraphics();
    		panelMaskContainer.update(panelg);	        	
        }         
        
    }	
	
	public void actionPerformed(ActionEvent e) {
		
////////////////////////////////////////////////////////////		
// Traps the "Use Current Roi" button
		if (e.getSource() == buttonUseCurrentRoi)
		{
			TrapUserDefinedRoi();
			imp.draw();
		}

////////////////////////////////////////////////////////////
// Traps the "Create Square Roi" button
		if (e.getSource() == buttonCreateSquareRoi)
		{
			// Create a square-Roi according to user-coordinates
			// and then call TrapUserDefinedRoi()
			imp.setRoi(sqrX, sqrY, sqrWidth, sqrWidth);
			TrapUserDefinedRoi();
			imp.draw();
		}

/////////////////////////////////////////////////////////////		
// Traps the "MapXXXX" buttons
		if (e.getSource() == buttonMapAllSquares)
		{					
			CommandSplitAndAnalyzeSquareRois();
		} 

		if (e.getSource() == buttonMapCurrentRoi)
		{					
			userRoi = imp.getRoi();
			if (userRoi == null) {
				// No user-defined Roi. Assume entire image
				imp.setActivated();
				IJ.makeRectangle(0, 0, iImageWidth, iImageHeight);
				userRoi = imp.getRoi();
			}			
			CommandAnalyzeSingleRoi(userRoi);
		} 
		
		if (e.getSource() == buttonMapEntireImage)
		{		
			imp.setActivated();
			IJ.makeRectangle(0, 0, iImageWidth, iImageHeight);
			userRoi = imp.getRoi();			
			CommandAnalyzeSingleRoi(userRoi);
		} 
		
//////////////////////////////////////////////////////////////////		
// Traps the "Create a Grayscale Mask" button		
		if (e.getSource() == buttonGrayscale)
		{
		// If there is no maskIP and no entry in ResultsTable, cannot go on
			int noROIs = rt.getCounter();
			if ((maskIP == null) || (noROIs < 1))
			{
				return;
			}
			
			// Read user input: which value is to be mapped?
	    	mappingValue = grayscaleMapping.getSelectedItem();   	
	    	
			// Read user input: grayscaleMin and grayscaleMax 
    		double min = Double.parseDouble(textGrayscaleMin.getText());
    		double max = Double.parseDouble(textGrayscaleMax.getText());    		    	
		
	    	CreateGrayscaleMask(mappingValue, min, max);
		}
	}

/***************************************************************************\
|		
|  Calculates the calibration mm-per-pixel and um-per-pixel according
|  to user input, and defines the global variables mmperpixel and umperpixel
|  Returns true if the variables were modified, otherwise returns false
|
\***************************************************************************/	
	boolean CalculateMMperPixel(){
		// Calculate micrometers (symbol: �m or um) per pixel
    	int pixels = Integer.parseInt(textPixels.getText());    	
    	double mm = Double.parseDouble(textmm.getText());
    	
    	//Correct false entries by user
    	if (pixels == 0)
    	{
    		pixels = 1;
    		textPixels.setText(Integer.toString(1));
    	}
    	
    	if (mm <= 0)
    	{
    		mm = 0.00529;
    		textmm.setText(Double.toString(0.00529));
    	}
    	double new_mmperpixel = mm / pixels;
		
		if (new_mmperpixel == mmperpixel) 
		{ // Nothing changed
			return false;
		} else {
		 // Update the global values
			mmperpixel = new_mmperpixel;
			umperpixel = mmperpixel * 1000;
			return true;
		}
	}
	

/***************************************************************************\
|		
|  Creates a new image with the mask of thresholded bone-trabeculae, 
|  background-shaded to the user-selected parameter
|
\***************************************************************************/	
	void CreateGrayscaleMask(String mappingValue, double min, double max) {
	
		int noROIs = rt.getCounter();
		
		// grayscaleIP is the image where the grayscaled mapping will be displayed
		ImageProcessor grayscaleIP = new ByteProcessor(iImageWidth, iImageHeight);
		grayscaleIP.setColor(Color.white);
		grayscaleIP.fill();	
		
		// Print the grayscaled background		
		for (int x = 0; (x < noROIs); x++)
		{
			//Roi coordinates 
			int X1 = (int)rt.getValue("X1", x);
			int Y1 = (int)rt.getValue("Y1", x);
			int X2 = (int)rt.getValue("X2", x);
			int Y2 = (int)rt.getValue("Y2", x);
			Roi grayRoi = new Roi(X1,Y1,(X2 - X1), (Y2 - Y1));				
			
			//Value to be mapped
			double value = rt.getValue(mappingValue, x);
			
			// Convert value to grayscale 0-254, according to min, max
			int gray = (int)( 254 * (max - value)/(max) );
			
			// Paint grayscale value in grayscaleIP	
			grayscaleIP.setColor(gray);
			grayscaleIP.setRoi(grayRoi);		
			grayscaleIP.fill(grayRoi.getMask());					
		}
	
		
		// Print the trabeculae of maskIP in grayscaleIP
		grayscaleIP.resetRoi();
		grayscaleIP.setColor(Color.black);	
		new ImagePlus(imageTitle + "_" + mappingValue , grayscaleIP).show();		
		ImageProcessor mask = maskIP.duplicate();
		mask.setMask(maskIP);		
		mask.invert();	
		grayscaleIP.fill(mask);
	
		// Print the values on top
		for (int x = 0; (x < noROIs); x++)
		{
			//Roi coordinates 
			double X1 = (double)rt.getValue("X1", x);
			double Y1 = (double)rt.getValue("Y1", x);
			double X2 = (double)rt.getValue("X2", x);
			double Y2 = (double)rt.getValue("Y2", x);
			double WIDTH = X2-X1;
			double HEIGHT = Y2-Y1;
			//Value to be mapped
			double value = rt.getValue(mappingValue, x);
			
			// Paint value as string in grayscaleIP	
			grayscaleIP.setColor(Color.WHITE);
			Font f = new Font("serif", Font.PLAIN, (int)(WIDTH * 0.2)); 
			grayscaleIP.setFont(f);
			double X = X1 + ((WIDTH) * 0.2);
			double Y = Y1 + ((HEIGHT) * 0.66);
			grayscaleIP.drawString(df.format(value), (int)X , (int)Y);
		}		
		
	}
	

/***************************************************************************\
|		
|	Set of custom Roi-classes. 
|	They override the draw(Graphics g) function of the native Roi-types, 
|	in order to draw in real-time the boundings of the square-Rois 
|	to be analyzed
|
\***************************************************************************/		

	public class MyPolyRoi extends PolygonRoi {

		public MyPolyRoi(int[] xPoints, int[] yPoints, int nPoints, int type) 	{ 
				super(xPoints, yPoints, nPoints, type); 
				this.type = MYROI;
		 	}
		
		public boolean isArea(){return true;}
		
		public void draw(Graphics g){	
			super.draw(g);
			DrawBoundingSquares(g, this);
		 }
	}	
	
	public class MyOvalRoi extends OvalRoi {
		public MyOvalRoi(int x, int y, int width, int height)  	{ 
				super(x, y, width, height);
				this.type = MYROI;
		 	}
		public boolean isArea(){return true;}
		
		public void draw(Graphics g){		
			super.draw(g);
			DrawBoundingSquares(g, this);
		 }	
	}
	
	public class MyRoi extends Roi {
		public MyRoi(int x, int y, int width, int height) 	{ 
				super(x, y, width, height); 
				this.type = MYROI;
			}
		
		public boolean isArea(){return true;}
		
		public void draw(Graphics g){
			super.draw(g);
			if (flagOverrideRoiDraw == true) {
				DrawBoundingSquares(g, this);
				imp.draw();
			}
		 }
	}	
	

	/***************************************************************************\
	|		
	|	Draws the boundings of the to-be-mapped squares on the image, 
	|	and refreshes the global variables sqrWidth, sqrX, sqrY, countSqrRois 
	|
	\***************************************************************************/	
	public void DrawBoundingSquares(Graphics g, Roi r)
	{
		// Define global variables sqrX, sqrY, sqrWidth
		Rectangle rect = r.getBounds();
		if (rect.width > rect.height)
		{
			sqrWidth = rect.width;
		} else {
			sqrWidth = rect.height;
		}
		sqrX = rect.x;
		sqrY = rect.y;			
		
		// Split image to square Rois of width sqrWidth.
		// sqrXstart and sqrYstart point to the beginning where the splitting procedure should start.
		sqrXstart = sqrX - (((int)(sqrX / sqrWidth)) * sqrWidth); 
		sqrYstart = sqrY - (((int)(sqrY / sqrWidth)) * sqrWidth);
		
		// Global variables iRoisX and iRoisY are the No of Rois in the X and Y axis respectively
		iRoisX = (int) ((iImageWidth - sqrXstart) / sqrWidth);	
		iRoisY = (int) ((iImageHeight - sqrYstart) / sqrWidth);
		countSqrRois = iRoisX * iRoisY;
		
		// Update the appropriate text fields on the panel
		textSqrX.setText(Integer.toString(sqrX));
		textSqrY.setText(Integer.toString(sqrY));
		textSqrWidthPix.setText(Integer.toString(sqrWidth));
		textSqrWidthMM.setText(df.format((sqrWidth * mmperpixel)));
		labelProgress.setText(Integer.toString(0) + 
						"/" + Integer.toString(countSqrRois) + " Sqrs" );	
	

		g.setColor(Color.BLACK);		
		int x; int y; // ROI Coordinates as number		
		int iX;	int iY; // ROI Coordinates as pixels
		for ( x = 1; x <= iRoisX; x++) {
			for ( y = 1; y <= iRoisY; y++) {
				 iX = sqrXstart + (x-1)*sqrWidth;
				 iY = sqrYstart + (y-1)*sqrWidth;				 
				 
				 // Draw a rectangle around the square-Roi of this loop
				 // Note: Graphics g requires screen and not Ip coordinates
				 g.drawRect(ic.screenX(iX), ic.screenY(iY), ic.screenX(sqrWidth), ic.screenY(sqrWidth));				
			}
		}	
		 
	}

/***************************************************************************\
|		
|  Checks the type of the user-defined Roi, and creates a MyXXXRoi equivalent
|  in order to override the draw(Graphics g) function of ImageJ's native Rois.
|  By internally calling draw(...) -> DrawBoundingSquares(...), this
|  procedure automatically sets the global variables: sqrX, sqrY, countSqrRois
|
\***************************************************************************/	
		
	void TrapUserDefinedRoi(){
		userRoi = imp.getRoi();
		if (userRoi == null) {
			// No user-defined Roi. Assume entire image
			imp.setActivated();
			IJ.makeRectangle(0, 0, iImageWidth, iImageHeight);
			userRoi = imp.getRoi();
		}
		Rectangle saveRoiRect = userRoi.getBounds();
		
		// Turn on the overriding of native Roi's draw(...) call-back function
		flagOverrideRoiDraw = true;
		
		switch (userRoi.getType()) {
			case MYROI:
				// Do nothing, it is already 'trapped'
				break;
 			case Roi.FREELINE:
			case Roi.FREEROI:
			case Roi.POLYGON:
			case Roi.POLYLINE:
				PolygonRoi polyRoi = (PolygonRoi)userRoi;
				int n = polyRoi.getNCoordinates();
				int[] nx = polyRoi.getXCoordinates();
				int[] ny = polyRoi.getYCoordinates();
				int[] sx = new int[n];
				int[] sy = new int[n];
				System.arraycopy(nx, 0, sx, 0, n);
				System.arraycopy(ny, 0, sy, 0, n);
				MyPolyRoi mypolyRoi = new MyPolyRoi(sx, sy, n, userRoi.getType());
				mypolyRoi.setLocation(saveRoiRect.x, saveRoiRect.y);
				imp.setRoi(mypolyRoi);
				break;
				
			case Roi.OVAL:
				MyOvalRoi myovalRoi = new MyOvalRoi(saveRoiRect.x, saveRoiRect.y, saveRoiRect.width, saveRoiRect.height);
				myovalRoi.setLocation(saveRoiRect.x, saveRoiRect.y);
				imp.setRoi(myovalRoi);
				break;
	
			case Roi.POINT:
				saveRoiRect = ip.getRoi();
			case Roi.LINE:			
			case Roi.RECTANGLE:
			default:
				MyRoi myRoi = new MyRoi(saveRoiRect.x, saveRoiRect.y, saveRoiRect.width, saveRoiRect.height);
				myRoi.setLocation(saveRoiRect.x, saveRoiRect.y);
				imp.setRoi(myRoi);
				break;
				
		}
	}
	

/***************************************************************************\
|		
|  Splits image to a grid of square Rois of width sqrWidth
|  and performs microstructure analysis in each one of them
|
\***************************************************************************/	
	void CommandSplitAndAnalyzeSquareRois(){					
// Read Modifiers
		blnSmooth = chkboxSmooth.getState();
		blnDespeckle = chkboxDespeckle.getState();
		blnAutoThreshold = chkboxAutoThreshold.getState();
		if (blnDespeckle == true) {
			speckleSize = Double.parseDouble(textSpeckleSize.getText());
		} else {
			// Accept all particles
			speckleSize = 0;
		}
		
// maskIP contains the mask of thresholded particles		
		maskIP = new ByteProcessor(iImageWidth, iImageHeight);
		maskIP.setColor(Color.white); // White background
		maskIP.fill();
		maskIP.setColor(Color.black); // Black particles, to be added ...
		
// Initiate Results Table
		rt = Analyzer.getResultsTable();
		rt.reset();
		Analyzer.resetCounter();
		analyzer = new Analyzer(imp);	
		
// Get user-defined	Roi from ImagePlus, and "trap" it (if not already done).
// DrawBoundingSquares(), invoked indirectly by TrapUserDefinedRoi(), 
// is supposed to refresh the global variables 
// sqrWidth, sqrX, sqrY, sqrXstart, sqrYstart, iRoisX, iRoisY
//		Roi impRoi = imp.getRoi();
//		Rectangle impRoiRect = impRoi.getBounds();
		TrapUserDefinedRoi();
		
// Split image to square Rois of width sqrWidth.
// sqrXstart and sqrYstart point to the beginning where the splitting procedure should start.
// iRoisX and iRoisY is the No of Rois in the X and Y axis respectively
		int countRoi = 1;
		int x; int y; // ROI Coordinates as numbers
		int iX;	int iY;  // ROI Coordinates	as pixels	
		
		for ( x = 1; x <= iRoisX; x++) {
			for ( y = 1; y <= iRoisY; y++) {
				 iX = sqrXstart + (x-1)*sqrWidth;
				 iY = sqrYstart + (y-1)*sqrWidth;
				 
		 
			    // Update ShowProgress 
				Graphics panelg = panelMapContainer.getGraphics();
				labelProgress.setText(Integer.toString(countRoi) + 
								"/" + Integer.toString(countSqrRois) + " Sqrs" );
				panelMapContainer.update(panelg);
				countRoi++ ;				
				
				// ip is the real image.
				// ip2 is a copy of the currentRoi as a separate Imageprocessor.
				// The actual processing will take place in ip2.					
				ipRectangle = new Rectangle(iX, iY, sqrWidth, sqrWidth);
				Roi ipRoi = new Roi(ipRectangle);
				ip.setRoi(ipRoi);
				ip2 = ip.crop();
				ip2.resetRoi();			 
				
				// Call the scanning-engine
				AnalyzeIp2();
				// and publish the results for this currentRoi
				FillResultsTable();				
			}
		}	
		
	}

	
/***************************************************************************\
|
|	Analyzes the user-defined Roi on the current image.
|	It can be any type of Roi, even a freehand one.
|
\***************************************************************************/	

	void CommandAnalyzeSingleRoi(Roi roi){	
// Read Modifiers
		blnSmooth = chkboxSmooth.getState();
		blnDespeckle = chkboxDespeckle.getState();
		blnAutoThreshold = chkboxAutoThreshold.getState();
		if (blnDespeckle == true) {
			speckleSize = Double.parseDouble(textSpeckleSize.getText());
		} else {
			// Accept all particles
			speckleSize = 0;
		}
		
// maskIP contains the mask of thresholded particles		
		maskIP = new ByteProcessor(iImageWidth, iImageHeight);
		maskIP.setColor(Color.white); // White background
		maskIP.fill();
		maskIP.setColor(Color.black); // Black particles
		
// Initiate Results Table
		rt = Analyzer.getResultsTable();
		rt.reset();
		Analyzer.resetCounter();
		analyzer = new Analyzer(imp);		


// Copy the user-selection to internal clipboard, and paste it to ip2
		imp.copy(false);
		ipRectangle = roi.getBounds();	
		
		ImagePlus impClip = ImagePlus.getClipboard();
		ip2 = impClip.getProcessor().duplicate();
		WindowManager.checkForDuplicateName = true; 
		ImagePlus impIp2 = new ImagePlus("ip2", ip2);
		Roi ipRoi = impClip.getRoi();
		impIp2.killRoi();
		if (ipRoi!=null && ipRoi.isArea() && ipRoi.getType()!=Roi.RECTANGLE) {
			ipRoi = (Roi)ipRoi.clone();
			ipRoi.setLocation(0, 0);
			impIp2.setRoi(ipRoi);
			IJ.run(impIp2, "Clear Outside", null);
			impIp2.killRoi();
		}	
		impIp2.changes = false;
		impIp2.close();
		ImageProcessor ip2Mask = roi.getMask();
		ip2.setMask(ip2Mask);
			
		// Call the scanning-engine ...
		AnalyzeIp2();
		// ... and publish the results
		FillResultsTable();				
	}

/***************************************************************************\
|
| 	Scans pixel by pixel current-Roi (passed as ip2).
|	If ip2 is irregular, it must be passed with the appropriate mask 
|	If a valid pixel is encountered, analyzeParticle() is called 
|	in order to wand the particle. 
|	Modifies the following static variables for further evaluation:
|		currentRoiTotalArea (= area occupied by current-Roi)
|		currentRoiParticlesArea (= area occupied by bone)
|		currentRoiParticlesTotalPerimeter (= total perimeter of bone-particles in currentRoi)
| 
|
\***************************************************************************/	

	void AnalyzeIp2() {		
// Smooth image if wished by user
		if (blnSmooth == true) {
			ip2.smooth();
		}	

// Either accept the user-defined threshold levels of parent-ip 
// or Autothreshold
		if (blnAutoThreshold == true) {
			level1 = 0;
			level2 = ip2.getAutoThreshold();	
			ip2.setThreshold(level1, level2, ImageProcessor.RED_LUT);				
		} else {
			level1 = ip.getMinThreshold();
			level2 = ip.getMaxThreshold();
			ip2.setThreshold(level1, level2, ImageProcessor.RED_LUT);				
		}
		
// Calculate total currentRoi Area
		int[] histogram = ip2.getHistogram();		
		currentRoiTotalArea = 0;		
		for (int i = 0; i <= 255; i++) {
			currentRoiTotalArea += histogram[i];
		}				
 
// PolygonFiller pf and FloodFiller ff are needed
// in order to accurately fill the interior of found particles
// (see analyzeParticles() for further details
		pf = new PolygonFiller();
		ImageProcessor ipf = ip2.duplicate();
		ipf.setColor(Color.black);
		ff = new FloodFiller(ip2);	
		ip2Wand = new Wand(ip2);
		
// Initiate particle measurements
		particlesCount = 0;		
		particlesTotalArea = 0;		
		particlesTotalPerimeter = 0;
		
// THIS IS THE MAIN SCANNING ENGINE
// Iterate through pixels, stop and analyze at every valid point
		int offset = 0;
		double value;
		byte[] pixels  = (byte[]) ip2.getPixels();	
		
		int curRoiWidth = ip2.getWidth();
		int curRoiHeight = ip2.getHeight();

		for (int y = 0; y < curRoiHeight; y++) {
			offset = y * curRoiWidth;			
			for (int x = 0; x < curRoiWidth; x++) {
				
				value = pixels[offset + x] & 255;
				if (value >= level1 && value <= level2){
					
				// Hooray! We encountered the beginning of a particle
				// Restore currentRoi to the entire ip2 
					ip2.resetRoi();
					AnalyzeParticle(x, y,  ip2);			
				}
			}	
		}
		
		currentRoiParticlesArea = particlesTotalArea;
		currentRoiParticlesTotalPerimeter = particlesTotalPerimeter;
	}


/***************************************************************************\
|
| 	Once the Scanning Engine hits a positive pixel, the Wand tool is called
|   to extract the particle. Then follows the calculation of the outer perimeter,
|   as well of as the inner perimeter (if the particle contains holes).
|   Finally the particle is erased, so that the Scanning Engine can hit 
|   the next particle
|
\***************************************************************************/

	void AnalyzeParticle(int x, int y, ImageProcessor ip2) 
	{		
		ip2Wand.autoOutline(x, y, level1, level2);
		
		// Ignore null results, or single pixels 
		if (ip2Wand.npoints <= 1) {
			//IJ.log("wand error: " + x + " " + y);
			return;
		}
		PolygonRoi tracedPolygonRoi = new PolygonRoi(ip2Wand.xpoints, ip2Wand.ypoints, ip2Wand.npoints, Roi.TRACED_ROI);
		Rectangle tracedRect = tracedPolygonRoi.getBounds();
		
		// Now we want to "erase" our particle painting it white.
		// The "straightforward" approach ip2.setRoi(tracedPolygonRoi); ip2.fill() 
		// leads to erroneous filling of the particle without taking into consideration 
		// any internal holes.
		// A more accurate filling of the interior of the particle can be achieved
		// with the use of PolygonFiller.getMask() and FloodFiller.particlesAnalyzerFill()
		//
		// NOTE: Masks are a messy thing. Each mask for an ImageProcessor refers 
		// to a specific Roi. Don't mix different Rois with different masks!
		// Convention: 
		// maskIp2_XXXX: mask refers to the entire ip2, 
		// maskTracedRect_XXXX: mask refers to the Roi containing the traced particle

		ip2.setRoi(tracedRect);	
		PolygonRoi proi = (PolygonRoi) tracedPolygonRoi;
		pf.setPolygon(proi.getXCoordinates(), proi.getYCoordinates(), proi.getNCoordinates());
		ip2.setMask(pf.getMask(tracedRect.width, tracedRect.height));		
		ip2.setColor(Color.white);
		ff.particleAnalyzerFill(x, y, level1, level2, ip2.getMask(), tracedRect);

// This piece of code is for debugging purposes. It means: for the first e.g. 3 particles
// display ip2.getMask() or whetever you like, with a useful comment.
// You can put as many "process checks" as you wish within this procedure
//	
/*		
		if (particlesCount <= 3)
		{
			ImageProcessor ip4;
			ip4 = ip2.getMask().duplicate();
			String txt = "ip3.getMask, Particle" + particlesCount;
			new ImagePlus(txt , ip4).show();
			IJ.error(txt + "after ff.particleAnalyzerFill");
		} 		
*/		
		
		ImageProcessor maskTracedRect_WithHoles = ip2.getMask().duplicate();			

	// Now we must check our tracedPolygonRoi for internal holes. 
	// This is important for calculating the internal perimeter. 		
			
	// First save current state to restore it later (there will be a mess with ip2)
		ip2.snapshot();		

		
	// We want to fill our particle as well as everything outside it
	// black, so that if there are holes, they will appear 
	// as the only white pixels within a black tracedRect
	
	// Construct a maskIp2Background with everything surrounding the traced particle 
		ImageProcessor maskIp2_WithoutHoles = 
			ExpandMaskToParentIp(proi.getMask(), new Roi(tracedRect), ip2);
		ImageProcessor maskIp2_Background = maskIp2_WithoutHoles.duplicate();
		maskIp2_Background.invert();
		
		ImageProcessor maskTracedRect_WithoutHoles = proi.getMask();
		ImageProcessor maskTracedRect_Background = maskTracedRect_WithoutHoles.duplicate();
		maskTracedRect_Background.invert();

	// Now paint black the two masks: maskIp2WithHoles + maskIp2Background
	// This should leave only white holes (if any) in a fully black ip2
		ip2.setRoi(tracedRect);
		ip2.setColor(Color.white);
		ip2.fill();
		ip2.setColor(Color.black);
		ip2.fill(maskTracedRect_WithHoles);
		ip2.fill(maskTracedRect_Background);

				
	// Now Iterate through pixels within tracedRect, 
	// stop if a white pixel is encountered
		double internalHolesPerimeter = 0;

		int offset = 0;
		int value;
		byte[] pixels  = (byte[]) ip2.getPixels();
		int width = ip2.getWidth();
		
		// yParticle, xParticles are local variables used for scanning within tracedRect
		for (int yParticle = tracedRect.y; yParticle < (tracedRect.y + tracedRect.height); yParticle++) {
			offset = yParticle * width;
			for (int xParticle = tracedRect.x; xParticle < (tracedRect.x + tracedRect.width); xParticle++) {
				value = (int)(pixels[offset + xParticle] & 255);
				if (value == 255){ // = white
					
				// Hooray! We encountered a bit of white-hole within our particle
				// Invoke the Wand tool, get the outline, and calculate the internal perimeter
					ip2Wand.autoOutline(xParticle, yParticle, 255, 255);
					PolygonRoi internalHolePolygonRoi = 
						new PolygonRoi(ip2Wand.xpoints, ip2Wand.ypoints,ip2Wand.npoints, Roi.TRACED_ROI);
					internalHolesPerimeter += internalHolePolygonRoi.getLength();
					// Now erase this internal hole ("paint it black")
					// and proceed for the next one (if exists)
					ip2.setRoi(internalHolePolygonRoi);
					ip2.setColor(Color.black);
					ip2.fill(ip2.getMask());
				}
			}
		}
			
		// Now, all internal holes must have been deleted, 
		// which means tracedRect is all black!. Now, restore the mess 		
		ip2.reset();		

				
		// Measurements for the tracedPolygonRoi
		double tracedRoiArea = 0;
		double tracedRoiPerimeter =0;
		
		int[] histogram = maskTracedRect_WithHoles.getHistogram();
		for (int i = 0; i <= 255; i++) {
			long count;
			count = histogram[i];
			tracedRoiArea += count;
		}
		tracedRoiArea = histogram[255];
		
		// Continue with calculations only if tracedRoiArea is above
		// the user-defined radius of SpeckleSize
		if (tracedRoiArea > (Math.PI * Math.pow(speckleSize,2)))
		{	
			tracedRoiPerimeter = tracedPolygonRoi.getLength();
	
			// Add the perimeter of internal holes.
			// NOTE: in bone histomorphometry, "perimeter" refers to the length
			// bone-marrow interface, even if when the interface
			// is a "hole" of marrow within a bone structure.
			tracedRoiPerimeter += internalHolesPerimeter;
	
			particlesTotalArea += tracedRoiArea;
			particlesTotalPerimeter += tracedRoiPerimeter;
		
			// Draw tracedPolygonRoi as mask in maskIP
			// Remember: the whole ip2 is a copy of ipRoi 
			// and is contained within ipRectangle
			if (maskIP != null)
			{
				tracedPolygonRoi.setLocation(ipRectangle.x + tracedRect.x, ipRectangle.y + tracedRect.y);
				maskIP.setRoi(tracedPolygonRoi);
				maskIP.setMask(maskTracedRect_WithHoles);
				maskIP.fill(ip2.getMask());	
				
				particlesCount++;				
				
			}
		}		
	}
	
/***************************************************************************\
|
| 	Takes the global variables: 
|		currentRoiTotalArea (= area occupied by currentRoi)
|		currentRoiParticlesArea (= area occupied by bone)
|		currentRoiParticlesTotalPerimeter (= total perimeter of bone-particles in currentRoi)
|	and calculates the bone-microstructure parameters according to Parfitt.
|	The results are added to the results-table rt
|
\***************************************************************************/
	void FillResultsTable() {
		// Calculate values	according to Parfitt
		double BAr; //Bone Area
		double TAr; // Tissue Area
		double BPm; // Bone Perimeter
		double BATA; // Bone Area fraction
//		double BSTV; // Bone Surface
		double TbTh; // Trabecular Thickness
		double TbSp; // Trabecular Separation
		double TbN; // Trabecular Number
		
		TAr = currentRoiTotalArea;
		BAr = currentRoiParticlesArea;
		BPm = currentRoiParticlesTotalPerimeter;
		
		BATA = BAr/TAr;
//		BSTV = (BPm/TAr);
		TbTh = (2)* (BAr/BPm);
		TbSp = (2) * (TAr - BAr) / BPm;
		TbN = (0.5)* (BPm/TAr);

// Fill Results table		
		rt.setPrecision(3);
		
		// Increment count and upper-left coordinates of every square-ROI examined
		rt.incrementCounter();
		rt.addValue("X1", ipRectangle.getBounds().getX());
		rt.addValue("Y1", ipRectangle.getBounds().getY());
		rt.addValue("X2", ipRectangle.getBounds().getX() + ipRectangle.width - 1);
		rt.addValue("Y2", ipRectangle.getBounds().getY() + ipRectangle.height - 1);
		
		// Bone Parameters
		rt.addValue("BA/TA (%)", BATA*100);		
		rt.addValue("Tb.Th (�m)", TbTh * umperpixel);
		rt.addValue("Tb.Sp (�m)", TbSp * umperpixel);		
		rt.setPrecision(5);
		rt.addValue("Tb.N (1/mm)", TbN / (mmperpixel));		
		rt.show("Results");
	}

	
/***************************************************************************\
|
| 	If a mask refers to a roiMask within ipParent, this function returns
|	a mask refering to ipParent (expands the mask)
|
\***************************************************************************/	
	ImageProcessor ExpandMaskToParentIp(ImageProcessor mask, Roi roiOfMask, ImageProcessor ipParent)
	{		
		ImageProcessor ipCanvas = ipParent.duplicate(); 
		
		// White canvas
		ipCanvas.resetRoi();
		ipCanvas.setColor(Color.black);
		ipCanvas.fill();
		
		// Paint black everything but the pixels-of-interest
		ipCanvas.setRoi(roiOfMask);
		ipCanvas.setColor(Color.white);
		ipCanvas.fill(mask);
		
		return ipCanvas;
	}
					
}
