package org.entermediadb.asset.creators.preprocess;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.openedit.Data;
import org.openedit.entermedia.Asset;
import org.openedit.entermedia.MediaArchive;
import org.openedit.entermedia.creator.
import org.openedit.util.ExecResult;

import com.openedit.page.Page;
import com.openedit.page.PageProperty;

public class exiftoolthumbCreator extends BaseImageCreator
{
	public boolean canReadIn(MediaArchive inArchive, String inInputType)
	{
		return inInputType != null && inInputType.endsWith("indd");
	}

	public ConvertResult convert(MediaArchive inArchive, Asset inAsset, Page inOut, ConvertInstructions inStructions)
	{
		ConvertResult result = new ConvertResult();
		result.setOk(false);
		
		Page input = inArchive.findOriginalMediaByType("image",inAsset);
		if( input != null)
		{
			new File( inOut.getContentItem().getAbsolutePath() ).getParentFile().mkdirs();
			List base = new ArrayList();
			//command.add("-b");
			//command.add("-ThumbnailImage");
			base.add(input.getContentItem().getAbsolutePath());
			//command.add("-o");
			base.add(inOut.getContentItem().getAbsolutePath());

			List command = new ArrayList(base);			
			command.add("PageImage");
			long timeout = getConversionTimeout(inArchive, inAsset);
			ExecResult done = getExec().runExec("exiftoolthumb",command,timeout);
			if(inOut.length() == 0)
			{
				command = new ArrayList(base);
				command.add("ThumbnailImage");
				done = getExec().runExec("exiftoolthumb",command,timeout);
			}	
			result.setOk(done.isRunOk());
			
		}
		if(inOut.length() == 0){
			inArchive.getPageManager().removePage(inOut);
			result.setOk(false);
			result.setError("no embeded thumbnail found in file");
		}
		return result;
	}

	
}