package org.entermediadb.asset.creator;

import org.openedit.data.BaseData;
import org.openedit.repository.ContentItem;

public class ConvertResult extends BaseData
{
	protected boolean fieldOk;
	protected boolean fieldComplete;
	protected String fieldError;
	protected ContentItem fieldOutput;
	
	public ContentItem getOutput()
	{
		return fieldOutput;
	}

	public void setOutput(ContentItem inOutput)
	{
		fieldOutput = inOutput;
	}

	public boolean isComplete() 
	{
		return fieldComplete;
	}
	
	public void setComplete(boolean inComplete) 
	{
		fieldComplete = inComplete;
	}
	
	public String getError()
	{
		return fieldError;
	}
	
	public void setError(String inError)
	{
		fieldError = inError;
	}
	public boolean isError()
	{
		return fieldError != null;
	}
	protected String fieldOutputPath;
	
	public boolean isOk()
	{
		return fieldOk;
	}
	public void setOk(boolean inOk)
	{
		fieldOk = inOk;
	}
	public String getOutputPath()
	{
		return fieldOutputPath;
	}
	public void setOutputPath(String inOutputPath)
	{
		fieldOutputPath = inOutputPath;
	}
	
}