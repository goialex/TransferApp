package logic.client;

import logic.*;
import network.ConnectionResolver;
import window.local.LocalController;
import window.remote.RemoteController;

import java.io.IOException;

public class ServerController extends Controller
{
	private ConnectionResolver connectionResolver;

	public ServerController(Connection initialConnection, ConnectionResolver resolver,
							BusinessEvents businessEvents, ConnectCloseEvent connectCloseEvent)
	{
		this.mainConnection = initialConnection;
		this.connectionResolver = resolver;
		this.businessEvents = businessEvents;
		this.mainConnectCloseEvent = connectCloseEvent;
	}

	@Override
	public void go()
	{
		if (resolveConnection())
		{
			transmitterController = new TransmittingController(mainConnection, transmittingConnection,
					businessEvents, mainConnectCloseEvent);
			receiverController = new ReceiverController(mainConnection, receivingConnection,
					businessEvents, mainConnectCloseEvent);
			receiverController.startListening();

			UIFileEventsHandler handler = new UIFileEventsHandler();
			LocalController.setFileEvents(handler);
			RemoteController.setFileEvents(handler);
		} else
			mainConnectCloseEvent.disconnect("Connection establish failed");
	}

	private boolean resolveConnection()
	{
		boolean successful = true;
		try
		{
			receivingConnection = connectionResolver.listenNextConnection(10_000);
			transmittingConnection = connectionResolver.listenNextConnection(10_000);

		} catch (IOException e)
		{
			successful = false;
		}

		return successful;
	}
}