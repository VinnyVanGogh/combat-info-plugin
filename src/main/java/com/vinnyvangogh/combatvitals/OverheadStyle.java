package com.vinnyvangogh.combatvitals;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * How the overhead readout relates to the game's own health bar.
 *
 * The game's bar is a few pixels wide and the readout is always wider, so
 * drawing text straight onto it overlaps badly. These are the two ways out:
 * cover it with a bar of our own, or move clear of it.
 */
@AllArgsConstructor
public enum OverheadStyle
{
	BAR("Bar behind text"),
	ABOVE_BAR("Text above health bar"),
	TEXT("Text only");

	@Getter
	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
