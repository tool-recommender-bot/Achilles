package parser.entity;

import fr.doan.achilles.annotations.Key;
import fr.doan.achilles.entity.type.MultiKey;

/**
 * CorrectMultiKey
 * 
 * @author DuyHai DOAN
 * 
 */
public class CorrectMultiKey implements MultiKey
{
	@Key(order = 1)
	private String name;

	@Key(order = 2)
	private int rank;

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public int getRank()
	{
		return rank;
	}

	public void setRank(int rank)
	{
		this.rank = rank;
	}
}
