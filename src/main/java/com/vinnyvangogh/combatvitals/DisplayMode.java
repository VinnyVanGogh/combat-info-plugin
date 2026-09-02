package com.vinnyvangogh.combatvitals;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum DisplayMode
{
	HITPOINTS("Hitpoints"),
	PERCENTAGE("Percentage"),
	BOTH("Both");

	@Getter
	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
