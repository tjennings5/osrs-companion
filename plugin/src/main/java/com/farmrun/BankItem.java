package com.osrsmcp.farmrun;

public class BankItem
{
	private final String name;
	private final int quantity;

	public BankItem(String name, int quantity)
	{
		this.name = name;
		this.quantity = quantity;
	}

	public String getName()
	{
		return name;
	}

	public int getQuantity()
	{
		return quantity;
	}
}
