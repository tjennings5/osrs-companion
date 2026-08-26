package com.farmrun;

public enum CompostType
{
	NONE("None"),
	SUPERCOMPOST("Supercompost"),
	ULTRACOMPOST("Ultracompost");

	private final String displayName;

	CompostType(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
