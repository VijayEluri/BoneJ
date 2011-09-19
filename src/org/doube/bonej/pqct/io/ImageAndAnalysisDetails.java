/*
	This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

	N.B.  the above text was copied from http://www.gnu.org/licenses/gpl.html
	unmodified. I have not attached a copy of the GNU license to the source...

    Copyright (C) 2011 Timo Rantalainen
*/

package ImageReading;
public class ImageAndAnalysisDetails{
	public double scalingFactor;
	public double constant;
	public double marrowThreshold;
	public double airThreshold;
	public double fatThreshold;
	public double muscleThreshold;
	public double areaThreshold;	//For cortical AREA analyses (CoA, SSI, I) + peeling distal pixels
	public double BMDthreshold;		//For cortical BMD analyses
	public double softThreshold;	//Thresholding soft tissues + marrow from bone
	public double boneThreshold;	//Thresholding bone from the rest and cortical AREA analyses (CoA, SSI, I)
	public int filterSize;
	public int softFilterSize;
	public int sectorWidth;
	public String imageSavePath;
	public boolean mRoiDet;
	public boolean dBoneSite;
	public boolean eOn;
	public boolean mOn;
	public boolean dOn;
	public boolean stOn;
	public boolean cOn;
	public boolean ukkOn;
	public boolean dicomOn;
	public boolean imOn;
	public boolean femur;

	//ImageJ plugin constructor
	public ImageAndAnalysisDetails(double scalingFactorIn, double constantIn,double areaThresholdIn,double BMDthresholdIn){
		scalingFactor	= scalingFactorIn;
		constant 		= constantIn;
		airThreshold	= -100;
		fatThreshold 	= 40;
		muscleThreshold = 200;
		marrowThreshold = 300;
		areaThreshold 	= areaThresholdIn;	//For cortical AREA analyses (CoA, SSI, I) + peeling distal pixels
		BMDthreshold 	= BMDthresholdIn;		//For cortical BMD analyses
		softThreshold 	= 300;	//Thresholding soft tissues + marrow from bone
		boneThreshold 	= areaThresholdIn;
		filterSize		= 3;
		softFilterSize	= 7;
		sectorWidth 	= 10;
		imageSavePath 	= new String("");
		mRoiDet			= false;		//manual roi determination
		dBoneSite		= false;	//Distal bone site
		eOn				= false;	//erode distal site
		mOn				= false;	//marrow analysis
		dOn				= true;	//Distribution analysis
		stOn			= false;	//Soft tissue analysis
		cOn				= true;		//Cortical analysis
		ukkOn			= false;		//UKK special
		dicomOn			= false;	//DICOM IMAGES
		imOn			= false;		//Visual inspection images
		femur 			= false;			//For radii rotation in case of femoral mid-shaft images...
	}
	
	public ImageAndAnalysisDetails(double scalingFactorIn, double constantIn, double softThresholdIn, double boneThresholdIn,double marrowThresholdIn,double areaThresholdIn,double BMDthresholdIn,int filterSizeIn,int softFilterSizeIn, int sectorWidthIn, String imageSavePathIn
	,boolean mRoiDetIn,boolean dBoneSiteIn,boolean eOnIn,boolean mOnIn,boolean dOnIn, boolean stOnIn, boolean cOnIn,boolean ukkOnIn,boolean dicomOnIn,boolean imOnIn,boolean femurIn){
		scalingFactor	= scalingFactorIn;
		constant 		= constantIn;
		airThreshold = -100;
		fatThreshold = 40;
		muscleThreshold = 200;
		marrowThreshold = marrowThresholdIn;
		areaThreshold = areaThresholdIn;	//For cortical AREA analyses (CoA, SSI, I) + peeling distal pixels
		BMDthreshold = BMDthresholdIn;		//For cortical BMD analyses
		softThreshold = softThresholdIn;	//Thresholding soft tissues + marrow from bone
		boneThreshold = boneThresholdIn;
		filterSize		= filterSizeIn;
		softFilterSize		= softFilterSizeIn;
		sectorWidth 	= sectorWidthIn;
		imageSavePath 	= imageSavePathIn;
		mRoiDet			=mRoiDetIn;		//manual roi determination
		dBoneSite		=dBoneSiteIn;	//Distal bone site
		eOn			=eOnIn;	//erode distal site
		mOn			=mOnIn;	//marrow analysis
		dOn			=dOnIn;	//Distribution analysis
		stOn			=stOnIn;	//Soft tissue analysis
		cOn			= cOnIn;		//Cortical analysis
		ukkOn		= ukkOnIn;		//UKK special
		dicomOn		= dicomOnIn;	//DICOM IMAGES
		imOn		= imOnIn;		//Visual inspection images
		femur 		= femurIn;			//For radii rotation in case of femoral mid-shaft images...
	}
		public ImageAndAnalysisDetails(double scalingFactorIn, double constantIn, double softThresholdIn, double boneThresholdIn,double marrowThresholdIn,double areaThresholdIn,double BMDthresholdIn,int filterSizeIn,int softFilterSizeIn, int sectorWidthIn, String imageSavePathIn
	,boolean mRoiDetIn,boolean dBoneSiteIn,boolean eOnIn,boolean mOnIn,boolean dOnIn, boolean stOnIn, boolean cOnIn,boolean ukkOnIn,boolean dicomOnIn,boolean imOnIn){
		scalingFactor	= scalingFactorIn;
		constant 		= constantIn;
		airThreshold = -100;
		fatThreshold = 40;
		muscleThreshold = 200;
		marrowThreshold = marrowThresholdIn;
		areaThreshold = areaThresholdIn;	//For cortical AREA analyses (CoA, SSI, I) + peeling distal pixels
		BMDthreshold = BMDthresholdIn;		//For cortical BMD analyses
		softThreshold = softThresholdIn;	//Thresholding soft tissues + marrow from bone
		boneThreshold = boneThresholdIn;
		filterSize		= filterSizeIn;
		softFilterSize		= softFilterSizeIn;
		sectorWidth 	= sectorWidthIn;
		imageSavePath 	= imageSavePathIn;
		mRoiDet			=mRoiDetIn;		//manual roi determination
		dBoneSite		=dBoneSiteIn;	//Distal bone site
		eOn			=eOnIn;	//erode distal site
		mOn			=mOnIn;	//marrow analysis
		dOn			=dOnIn;	//Distribution analysis
		stOn			=stOnIn;	//Soft tissue analysis
		cOn			= cOnIn;		//Cortical analysis
		ukkOn		= ukkOnIn;		//UKK special
		dicomOn		= dicomOnIn;	//DICOM IMAGES
		imOn		= imOnIn;		//Visual inspection images
		femur 		= false;			//For radii rotation in case of femoral mid-shaft images...
	}
}