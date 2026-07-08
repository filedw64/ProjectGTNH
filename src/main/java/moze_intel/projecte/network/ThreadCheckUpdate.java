package moze_intel.projecte.network;

public class ThreadCheckUpdate extends Thread
{
	private static boolean hasRunServer = false;
	private static boolean hasRunClient = false;
	private boolean isServerSide;

	public ThreadCheckUpdate(boolean isServer)
	{
		this.isServerSide = isServer;
		this.setName("ProjectE Update Checker " + (isServer ? "Server" : "Client"));
	}

	@Override
	public void run()
	{
        if (isServerSide)
        {
            hasRunServer = true;
        }
        else
        {
            hasRunClient = true;
        }
	}

	public static boolean hasRunServer()
	{
		return hasRunServer;
	}

	public static boolean hasRunClient()
	{
		return hasRunClient;
	}
}
