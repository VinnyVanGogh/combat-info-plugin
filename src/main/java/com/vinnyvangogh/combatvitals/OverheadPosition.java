package com.vinnyvangogh.combatvitals;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Where over the actor the text sits. Large NPCs put their own health bar at
 * the top of the screen and their name plate above their head, so TOP is not
 * always the readable choice — hence the per-target override.
 */
@AllArgsConstructor
public enum OverheadPosition
{
	TOP("Top"),
	MIDDLE("Middle"),
	BOTTOM("Bottom");

	@Getter
	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
