package projectgtnh.api;

import static org.junit.Assert.*;

import org.junit.Test;

public class ProjectGTNHAPITest
{

	@Test
	public void testGetEMCProxy() throws Exception
	{
		assertNotNull(ProjectGTNHAPI.getEMCProxy());
	}

	@Test
	public void testGetConversionProxy() throws Exception
	{
		assertNotNull(ProjectGTNHAPI.getConversionProxy());
	}

	@Test
	public void testGetTransmutationProxy() throws Exception
	{
		assertNotNull(ProjectGTNHAPI.getTransmutationProxy());
	}

	@Test
	public void testGetBlacklistProxy() throws Exception
	{
		assertNotNull(ProjectGTNHAPI.getBlacklistProxy());
	}
}