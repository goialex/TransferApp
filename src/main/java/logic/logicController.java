package logic;

import network.*;
import window.AppLogger;
import window.LocalUIEvents;
import window.connection.ConnectionPresenter;
import window.local.LocalPresenter;

import java.io.File;
import java.net.Socket;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class logicController extends Thread
{
	private static final Logger LOGGER = AppLogger.getInstance();
	private static final int PORT = 50001;

	private String downloadPath;

	private ConnectionResolver connectionResolver;

	private DeltaTime deltaT = new DeltaTime(5);

	private UIEventTransmitter UIEventTransmitter;
	private Socket mainSocket;

	private SenderController senderController;
	private ReceiverController receiverController;

	public logicController(UIEventTransmitter UIEventTransmitter)
	{
		UIEventReceiver localUIHandler = new UIEventReceiver();
		ConnectionPresenter.changeLocalEventHandler(localUIHandler);
		LocalPresenter.changeLocalEventHandler(localUIHandler);

		this.UIEventTransmitter = UIEventTransmitter;
		this.connectionResolver = new ConnectionResolver(new ConnectionListener());

		setDaemon(true);
	}

	public void run()
	{
		connectionResolver.startListening(PORT);

		while (true)
		{

			//DEBUG
//			if (null != mainSocket && mainSocket.isConnected())
//				LOGGER.log(Level.FINE, "Connected to: " + mainSocket.getInetAddress());


			deltaT.update();
			if (!deltaT.enoughTimePassed())
			{
				try
				{
					long millis = deltaT.getTimeToTick() / (long) 1e+6;
//					AppLogger.getInstance().log(Level.FINEST, String.format("sleeping for %d millis", millis));
					Thread.sleep(millis);
				} catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			deltaT.subtractOneTick();

		}
	}

	class UIEventReceiver implements LocalUIEvents
	{
		@Override
		public void updateAvailableFileList(List<File> files)
		{
			senderController.updateAvailableFileList(files);
		}

		@Override
		public boolean attemptConnectionToHost(String host, int port)
		{
			assert null == mainSocket : "Already connected to something";

			LOGGER.log(Level.FINE, "Connection request to: " + host);

			connectionResolver.attemptConnection(host, port);

			return true;
		}

		@Override
		public void setDownloadLocation(String path)
		{
			LOGGER.log(Level.FINE, "Set file download location to: " + path);

			downloadPath = path;
		}

		@Override
		public void requestFileForDownload(String fileName)
		{
			LOGGER.log(Level.FINE, "Request file" + fileName);

		}
	}

	class ConnectionListener implements ConnectionResolver.ConnectionEvent
	{
		public void connectionEstablished(Socket socket)
		{
			assert null == mainSocket || mainSocket.isClosed() : "Got connection request while connected";
			assert socket.isConnected() : "Got event with unconnected socket";

			LOGGER.log(Level.ALL, "Received Connection request");
			if (UIEventTransmitter.shouldAcceptConnectionFrom(socket.getInetAddress().toString()))
			{
				LOGGER.log(Level.ALL, "Successfully connected to socket");
				mainSocket = socket;
				receiverController = new ReceiverController(socket, UIEventTransmitter);
				senderController = new SenderController(socket);

				if (connectionResolver.isListening())
					connectionResolver.stopListening();
			} else
			{
				LOGGER.log(Level.ALL, String.format("Could not connect to: %s, already connected to: %s",
						socket.getInetAddress(), socket.getInetAddress()));

				if (!connectionResolver.isListening())
					connectionResolver.startListening(PORT);
			}
		}
	}

}
