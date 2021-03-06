package conversions.creators;

import java.awt.Dimension

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.openedit.Data
import org.openedit.entermedia.Asset
import org.openedit.entermedia.MediaArchive
import org.openedit.entermedia.creator.BaseImageCreator
import org.openedit.entermedia.creator.ConvertInstructions
import org.openedit.entermedia.creator.ConvertResult
import org.openedit.entermedia.creator.MediaCreator

import com.openedit.OpenEditException
import com.openedit.page.Page
import com.openedit.util.ExecResult
import com.openedit.util.PathUtilities

public class imagemagickCreatorFaster extends BaseImageCreator {
	private static final Log log = LogFactory.getLog(imagemagickCreatorFaster.class);
	protected String fieldPathToProfile;

	public String getPathtoProfile(){
		if(fieldPathToProfile == null){
			Page profile = getPageManager().getPage("/system/components/conversions/tinysRGB.icc");
			fieldPathToProfile = profile.getContentItem().getAbsolutePath();
		}
		return fieldPathToProfile;
	}

	public boolean canReadIn(MediaArchive inArchive, String inInput) {
		return !inInput.equals("mp3") || canPreProcess(inArchive, inInput); //Can read in most any input except mp3. Maybe in the future it will look up the album cover
	}

	public ConvertResult convert(MediaArchive inArchive, Asset inAsset, Page inOutFile, ConvertInstructions inStructions) throws OpenEditException {
		if(!inStructions.isForce() && inOutFile.length() > 1 ) {
			ConvertResult result = new ConvertResult();
			result.setOk(true);
			result.setComplete(true);
			return result;
		}

		if(inStructions.isWatermark()) {
			inStructions.setWatermark(false);
			String outputPath = populateOutputPath(inArchive, inStructions); //now does not contain wm
			Page outputPage = getPageManager().getPage(outputPath);
			if(!outputPage.exists())
			{
				createOutput(inArchive, inAsset, outputPage, inStructions); //this will create smaller version
			}
			inStructions.setWatermark(true);
			String finaloutputPath = populateOutputPath(inArchive, inStructions);
			//inStructions.setAssetSourcePath(outputPath);
			Page finaloutputPage = getPageManager().getPage(finaloutputPath);
			
			return applyWaterMark(inArchive, outputPage.getContentItem().getAbsolutePath(), finaloutputPage.getContentItem().getAbsolutePath(), inStructions);
			
		}
		return createOutput(inArchive, inAsset, inOutFile, inStructions);
	}

	protected ConvertResult createOutput(MediaArchive inArchive, Asset inAsset, Page inOutFile, ConvertInstructions inStructions) {
		ConvertResult result = new ConvertResult();
		String outputpath = inOutFile.getContentItem().getAbsolutePath();
		Page input = null;
		boolean useoriginal = Boolean.parseBoolean(inStructions.get("useoriginalasinput"));
		//if watermarking is set
		if(inStructions.isWatermark())
		{
			Page inputPage = inArchive.getOriginalDocument(inAsset);
			//Page inputPage = getPageManager().getPage(inStructions.getAssetSourcePath());
			if(inputPage == null || !inputPage.exists())
			{
				result.setOk(false);
				return result;
			}
			String fullInputPath = inputPage.getContentItem().getAbsolutePath();
			String tmpoutputpath = PathUtilities.extractPagePath(outputpath) + ".wm.jpg";
			applyWaterMark(inArchive, fullInputPath, tmpoutputpath, inStructions);
			input = getPageManager().getPage(tmpoutputpath);  
		}

		boolean autocreated = false; //If we already have a smaller version we just need to make a copy of that
		String offset = inStructions.getProperty("timeoffset");

		String tmpinput = PathUtilities.extractPageType( inOutFile.getPath() );
		boolean transparent = inStructions.isTransparencyMaintained(tmpinput);
		String ext = inStructions.getInputExtension();
		if( ext == null && input != null)
		{
			ext = PathUtilities.extractPageType( input.getPath() );
		}

		if( ext == null)
		{
			ext = inAsset.getFileFormat();
		}
		
		//TODO: Make custom thumb inputs, document.pdf and jpg input pre-processors instead of hard coded items
		if( inStructions.getMaxScaledSize() != null && offset == null ) //page numbers are 1 based
		{
			String page = null;
			if( inStructions.getPageNumber() > 1 )
			{
				page = "page" + inStructions.getPageNumber();
			}
			else
			{
				page = "";
			}

			Dimension box = inStructions.getMaxScaledSize();
			if( input == null && box.getWidth() < 1025 )
			{
				if (transparent) 
				{
					input = getPageManager().getPage("/WEB-INF/data" + inArchive.getCatalogHome() + "/generated/" + inAsset.getSourcePath() + "/image1024x768" + page + ".png");
				} else {
					
					input = getPageManager().getPage("/WEB-INF/data" + inArchive.getCatalogHome() + "/generated/" + inAsset.getSourcePath() + "/image1024x768" + page + ".jpg");
				}
				if( input.length() < 2 )
				{
					input = null;
				}
				else
				{
					autocreated = true;
				}
			}
		}

		boolean hascustomthumb = false;
		Page customthumb = null;

		if("png".equals(ext)){
			customthumb = getPageManager().getPage("/WEB-INF/data/" + inArchive.getCatalogId() + "/generated/" + inAsset.getSourcePath() + "/customthumb.png");

		}	else
		{
			customthumb = getPageManager().getPage("/WEB-INF/data" + inArchive.getCatalogHome() + "/generated/" + inAsset.getSourcePath() + "/customthumb.jpg");
		}
		String filetype = inArchive.getMediaRenderType(inAsset.getFileFormat());
		if(customthumb.exists()){
			hascustomthumb = true;
			if(input == null && !"document".equals(filetype)){
				input = customthumb;
				log.info("Length was ${input.length()}");
				if( input.length() < 2 )
				{
					input = null;
				}
				else
				{
					autocreated = true;
				}
			}
		}
		if(input == null)
		{
			if(inStructions.getInputPath() != null)
			{
				input = getPageManager().getPage(inStructions.getInputPath());
			}
		}
		//Look over to see if there is a creator that can do a better job of reading in this type
		MediaCreator preprocessor = getPreProcessor(inArchive, ext);
		Page original = inArchive.getOriginalDocument(inAsset);
		
		if(original.exists() &&  preprocessor != null && !hascustomthumb)
		{
			//This will output a native format. First one wins. it is not a loop.
			String tmppath = preprocessor.populateOutputPath(inArchive, inStructions);
			Page tmpout = getPageManager().getPage(tmppath);
			if( !tmpout.exists() || tmpout.getContentItem().getLength()==0)
			{
				//Create
				ConvertResult tmpresult = preprocessor.convert(inArchive, inAsset, tmpout, inStructions);
				if( !tmpresult.isOk() )
				{
					return tmpresult;
				}
				//					if( !inStructions.isWatermark() && out.getPath().equals(inOutFile.getPath())) //this is the same file we originally wanted
				//					{
				//						//return tmpresult;
				//					}
				if( tmpout.getContentItem().getLength() > 0)
				{
					//cmykpreprocessor returns an xconf
					//so this should be safe since there are probably
					//no other preprocessors that return xconf files
					if ("xconf" != tmpout.getPageType()){
						input = tmpout;
					}
					//					This is only useful for INDD at 1024. to complex to try and optimize
					//					if( input.getPath().equals(inOutFile.getPath()))
					//					{
					//preprosessor took care of the entire file. such as exiftol
					//						result.setOk(true);
					//						return result;
					//					}
				}
				else
				{
					//exifthumbtool probably did not work due to permissions
					//Highlights doesn't have the original - maybe we can revert back to an input if the original doesn't exist?  Or just
					String page = null;
					if( inStructions.getPageNumber() > 1 )
					{
						page = "page" + inStructions.getPageNumber();
					}
					else
					{
						page = "";
					}
					result.setError("Prepropessor could not create tmp file");
					result.setOk(false);
					return result;
				}
			}
			else if( input == null)
			{
				//we are looking for a working format to use as input
				
				//cmykpreprocessor returns an xconf
				//so this should be safe since there are probably
				//no other preprocessors that return xconf files
				if ("xconf" != tmpout.getPageType()){
					input = tmpout;
				}
			}
		}
//		if( input == null)
//		{
//			//last chance
//			input = inArchive.findOriginalMediaByType("document",inAsset);
//		}

		if( input == null || !input.exists())
		{
			//no such original
			result.setOk(false);
			//This sucks,if the orignal is not available or we are waiting for a proxy
			//The fix is to do conversions by the asset like we do for push
			log.debug("input not yet available " + inAsset.getSourcePath() );
			return result;
		}


		File inputFile = new File(input.getContentItem().getAbsolutePath());
		String newext = PathUtilities.extractPageType( input.getPath() );
		if( newext != null && newext.length()> 1)
		{
			ext = newext.toLowerCase();
		}
		List<String> com = createCommand(inputFile, inStructions);
		com.add("-limit");
		com.add("thread");
		com.add("1");

		if (inStructions.getMaxScaledSize() != null)
		{
			//be aware ImageMagick writes to a tmp file with a larger version of the file before it is finished
			if( "eps".equalsIgnoreCase( ext) || "pdf".equalsIgnoreCase( ext) || "ai".equalsIgnoreCase( ext))
			{
				//check input width
				int width = inAsset.getInt("width");
				if( width > 0 )
				{
					// calculate output width
					int height = inAsset.getInt("height");
					double ratio = height/width;

					int prefw = inStructions.getMaxScaledSize().getWidth();
					int prefh = inStructions.getMaxScaledSize().getHeight();

					int distw = Math.abs(prefw - width);
					int disth = Math.abs(prefh - height);

					int outputw;
					if(disth < distw)
					{
						outputw = width*(prefh/height);
					}
					else
					{
						outputw = prefw;
					}

					if( width < outputw)
					{
						//for small input files we want to scale up the density
						float density = ((float)outputw / (float)width) * 300f;
						density = Math.max(density,300);
						density = Math.min(density,900);						
						String val = String.valueOf( Math.round(density) );
						com.add(0,val);
						com.add(0,"-density");
					}
					else
					{
						com.add(0,"300");
						com.add(0,"-density");
					}
				}
			}
			if (!inStructions.isCrop())
			{
				//end of probably wrong section
				com.add("-resize");

				String prefix = null;
				String postfix = null;

				prefix =  String.valueOf( inStructions.getMaxScaledSize().width );
				postfix =  String.valueOf( inStructions.getMaxScaledSize().height );
				if (isOnWindows())
				{
					com.add("\"" + prefix + "x" + postfix + "\"");
				}
				else
				{
					com.add(prefix + "x" + postfix);
				}
			}

		}

		//faster to do it after sizing
		if(inStructions.isCrop())
		{
			boolean croplast = Boolean.parseBoolean(inStructions.get("croplast"));
			//resize then cut off edges so end up with a square image
			if(!croplast)
			{
				com.add("-resize");
				StringBuffer resizestring = new StringBuffer();
				resizestring.append(inStructions.getMaxScaledSize().width);
				resizestring.append("x");
				resizestring.append(inStructions.getMaxScaledSize().height);
				resizestring.append("^");
				com.add(resizestring.toString());
			}

			//This gravity is the relative point of the crop marks
			setValue("gravity", "NorthWest", inStructions, com);

			if( !transparent && ("eps".equals(ext) || "pdf".equals(ext) || "png".equals(ext) ||  "gif".equals(ext)) )
			{
				com.add("-background");
				com.add("white");
				com.add("-flatten");
			}
			else if ("svg".equals(ext))//add svg support; include transparency
			{
				com.add("-background");
				com.add("transparent");
				com.add("-flatten");
			} else {
				setValue("background", null, inStructions, com);
				setValue("layers", null, inStructions, com);
			}

			com.add("-crop");
			StringBuffer cropString = new StringBuffer();
			String cropwidth = inStructions.get("cropwidth");
			if(!cropwidth){
				cropwidth = inStructions.getMaxScaledSize().width;
			}
			cropString.append(cropwidth);
			cropString.append("x");
			String cropheight = inStructions.get("cropheight");

			if(!cropheight){
				cropheight = inStructions.getMaxScaledSize().height;
			}
			cropString.append(cropheight);

			String x1 = inStructions.get("x1");
			String y1 = inStructions.get("y1");

			cropString.append("+");
			if(x1 == null)
			{
				cropString.append("0");
			}
			else
			{
				cropString.append(x1);
			}
			cropString.append("+");
			if(y1 == null)
			{
				cropString.append("0");
			}
			else
			{
				cropString.append(y1);
			}
			com.add(cropString.toString());
			com.add("+repage");
			if(croplast)
			{
				com.add("-resize");
				StringBuffer resizestring = new StringBuffer();
				resizestring.append(inStructions.getMaxScaledSize().width);
				resizestring.append("x");
				resizestring.append(inStructions.getMaxScaledSize().height);
				resizestring.append("^");
				com.add(resizestring.toString());
			}
		}
		else if( !transparent && ("eps".equals(ext) || "pdf".equals(ext) || "png".equals(ext) || "gif".equals(ext) ) )
		{
			com.add("-background");
			com.add("white");
			com.add("-flatten");
		}
		else if ("svg".equals(ext))//add svg support; include transparency
		{
			com.add("-background");
			com.add("transparent");
			com.add("-flatten");
		} else {
			
			setValue("background", null, inStructions, com);
			setValue("layers", null, inStructions, com);
		}
		setValue("quality", "89", inStructions, com);
		//add sampling-factor if specified
		if (inStructions.get("sampling-factor")!=null)
		{
			com.add("-sampling-factor");
			com.add(inStructions.get("sampling-factor"));
		}
		if( autocreated )  //we are using a color corrected input
		{
			com.add("-strip"); //This does not seem to do much
		}
		else
		{
			String _colorspace = inAsset.get("colorspace");
			log.info("Colorspace: " + _colorspace)
			
			Data colorspacedata  = _colorspace!=null ? inArchive.getData("colorspace",_colorspace) : null;
			if (colorspacedata!=null && colorspacedata.getName().equalsIgnoreCase("cmyk")) //Edge case where someone has the wrong colorspace set in the file 
			{
				com.add("-auto-orient"); //Needed for rotate tool
				com.add("-strip"); //This does not seem to do much
				setValue("profile", getPathtoProfile(), inStructions, com);
			}
			else
			{
				setValue("colorspace", "sRGB", inStructions, com);
			}
			

			//Some old images have a conflict between a Color Mode of CMYK but they have an RGB Profile embeded. Make sure we check for this case
		}
		if (isOnWindows() )
		{
			// windows needs quotes if paths have a space
			com.add("\"" + outputpath + "\"");
		}
		else
		{
			com.add(outputpath);
		}

		long start = System.currentTimeMillis();
		new File(outputpath).getParentFile().mkdirs();
		
		long timeout = getConversionTimeout(inArchive, inAsset);
		ExecResult execresult = getExec().runExec("convert", com, true, timeout);

		boolean ok = execresult.isRunOk();
		result.setOk(ok);

		if (ok)
		{
			result.setComplete(true);

			log.info("Convert complete in:" + (System.currentTimeMillis() - start) + " " + inOutFile.getName());

			return result;
		}
		//problems
		log.info("Could not exec: " + execresult.getStandardOut() );
		if( execresult.getReturnValue() == 124)
		{
			result.setError("Exec timed out after " + timeout);
		}
		else
		{
			result.setError(execresult.getStandardOut());
		}
		return result;
	}


	protected List<String> createCommand(File inFile, ConvertInstructions inStructions)
	{
		List<String> com = new ArrayList<String>();

		// New version of Image Magik are 0 based
		int page = inStructions.getPageNumber();
		page--;
		page = Math.max(0, page);

		String prefix = "";
		String extension = "";
		int dotIndex = inFile.getName().lastIndexOf('.');
		if (dotIndex > 0)
		{
			extension = inFile.getName().substring(dotIndex + 1);
		}
		if ("dng".equalsIgnoreCase(extension))
		{
			prefix = "dng:";
		}
		else if (inStructions.getInputExtension() != null)
		{
			prefix = inStructions.getInputExtension() + ":";

		}
		if (isOnWindows())
		{
			com.add("\"" + prefix + inFile.getAbsolutePath() + "[" + page + "]\"");
		}
		else
		{
			com.add(prefix + inFile.getAbsolutePath() + "[" + page + "]");
		}
		return com;
	}

	public void resizeThumbnailImage(File inFile, File inOutFile, ConvertInstructions inStructions) throws Exception
	{

		List<String> com = new ArrayList<String>();
		com.add(inFile.getAbsolutePath() + "[" + inStructions.getPageNumber() + "]");
		com.add("-thumbnail");
		com.add((int) inStructions.getMaxScaledSize().getWidth() + "x" + (int) inStructions.getMaxScaledSize().getHeight() + ">");
		com.add(inOutFile.getAbsolutePath());
		inOutFile.getParentFile().mkdirs();
		long start = System.currentTimeMillis();
		if (runExec("convert", com))
		{
			log.info("Resize complete in:" + (System.currentTimeMillis() - start) + " " + inOutFile.getName());
		}
	}

	public List<String> getConvertCommand(File inIn, File inOut, int inPageNumber)
	{
		List<String> com = new ArrayList<String>();
		com.add(inIn.getAbsolutePath() + "[" + inPageNumber + "]");
		com.add(inOut.getAbsolutePath());
		return com;
	}

	public ConvertResult applyWaterMark(MediaArchive inArchive, String inInputAbsPath, String inOutputAbsPath, ConvertInstructions inStructions)
	{
		// composite -dissolve 15 -tile watermark.png src.jpg dst.jpg
		List<String> com = new ArrayList<String>();
		com.add("-dissolve");
		com.add("100");

		String placement = inStructions.getWatermarkPlacement();
		if(placement == null)
		{
			placement = "tile";//"SouthWest";
		}

		if (placement.equals("tile"))
		{
			com.add("-tile");

		}
		else
		{
			com.add("-gravity");
			com.add(placement);
		}

		com.add(getWaterMarkPath(inArchive.getThemePrefix()));
		com.add(inInputAbsPath);
		com.add(inOutputAbsPath);
		
		boolean ok =  runExec("composite", com);
		ConvertResult result = new ConvertResult();
		result.setOk(ok);
		return result;

	}

	protected void setValue(String inName, String inDefault,ConvertInstructions inStructions, List comm)
	{
		String value = inStructions.get(inName);
		if( value != null || inDefault != null)
		{
			comm.add("-" + inName );
			if( value != null)
			{
				comm.add(value);
			}
			else if( inDefault != null)
			{
				comm.add(inDefault);
			}
		}

	}
}
