package com.farmrun;

public enum RunMode
{
	/** No run in progress. */
	IDLE,
	/** Run started; player is retrieving items from the bank. */
	BANKING,
	/** Player is actively visiting patches. */
	ACTIVE
}
