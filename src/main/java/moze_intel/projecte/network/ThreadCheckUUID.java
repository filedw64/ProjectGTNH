package moze_intel.projecte.network;

public class ThreadCheckUUID extends Thread
{
	private static boolean hasRunServer = false;
    private boolean isServerSide;

	public ThreadCheckUUID(boolean isServer)
	{
		this.isServerSide = isServer;
		this.setName("ProjectE UUID Checker " + (isServer ? "Server" : "Client"));
	}

	@Override
	public void run()
	{
        if (isServerSide)
            hasRunServer = true;
	}

	public static boolean hasRunServer()
	{
		return hasRunServer;
	}
}
