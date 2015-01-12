package de.codesourcery.luaparser;

public class ItemAndAmount
{
	public final Item item;
	public final double amount;
	public ItemAndAmount(Item item,double amount) {
		this.item = item;
		this.amount = amount;
	}

	@Override
	public String toString() {
		return item.name+" x "+amount;
	}
}