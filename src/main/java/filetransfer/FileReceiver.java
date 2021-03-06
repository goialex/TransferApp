package filetransfer;

import org.jetbrains.annotations.NotNull;
import filesistem.FileException;
import filetransfer.api.*;
import network.ConnectionException;
import window.AppLogger;

import java.util.logging.Level;
import java.util.logging.Logger;

public class FileReceiver
{
	private static final Logger LOGGER = AppLogger.getInstance();
	private static final int CONNECTION_TIMEOUT_MILLIS = 10_000;
	public static final int ERROR_CODE = -1;
	private static final int START_CODE = 1;
	private static final int CHUNK_SIZE = 8192;

	private TransferFileOutput fileOutput;
	private TransferInput transferInput;
	private TransferOutput transferOutput;

	private long fileSizeBytes;


	public FileReceiver(@NotNull TransferInput transferInput, @NotNull TransferOutput transferOutput,
						@NotNull TransferFileOutput fileOutput, long fileSizeBytes)
	{
		assert null != transferInput : "Invalid socket receiver";
		assert null != transferOutput : "Invalid socket transmitter";
		assert null != fileOutput : "Invalid fileOutput";
		assert fileSizeBytes > 0 : "Invalid file size";

		this.fileOutput = fileOutput;
		this.transferInput = transferInput;
		this.transferOutput = transferOutput;

		this.fileSizeBytes = fileSizeBytes;
	}

	public void transfer() throws ConnectionException, FileException, InvalidFilePath
	{
		LOGGER.log(Level.INFO, "File receiving starting...");
		try
		{
			transferInput.skip(transferInput.available());
			basicFileCheck(fileOutput, fileSizeBytes);
			fileOutput.open();
			transferOutput.transmitByte(START_CODE);
			receiveBytesAndWriteToFile();
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		} catch (FileException | InvalidFilePath e)
		{
			transferOutput.transmitByte(ERROR_CODE);
			transferInput.skip(transferInput.available());
			throw e;
		}finally
		{
			LOGGER.log(Level.INFO, "File receiving done.");
		}
	}

	private void receiveBytesAndWriteToFile() throws ConnectionException, FileException, InterruptedException
	{
		byte[] buffer = new byte[CHUNK_SIZE];
		long bytesLeftToReceive = fileSizeBytes;
		int available;
		DeltaTime dt = new DeltaTime();
		while (bytesLeftToReceive > 0 && hasTime(dt))
		{
			if ((available = transferInput.available()) > 0)
			{
				bytesLeftToReceive -= transferChunk(buffer, Math.min(available, CHUNK_SIZE));
				dt.reset();
			} else
				Thread.sleep(100);
		}
		if (bytesLeftToReceive != 0)
			throw new ConnectionException("Did not receive full file, bytes missing: " + bytesLeftToReceive);
	}

	protected int transferChunk(byte[] buffer, int maxAmountToRead) throws ConnectionException, FileException
	{
		int amountRead = transferInput.read(buffer, maxAmountToRead);
		fileOutput.writeToFile(buffer, amountRead);

		return amountRead;
	}

	private boolean hasTime(DeltaTime dt)
	{
		return dt.getElapsedTimeMillis() <= CONNECTION_TIMEOUT_MILLIS;
	}

	private void basicFileCheck(TransferFileOutput fileOutput, long fileSizeBytes) throws InvalidFilePath
	{
		if (fileOutput.exists())
			throw new InvalidFilePath("File already exists");

		if (fileOutput.diskSpaceAtLocation() < fileSizeBytes)
			throw new InvalidFilePath("Not enough space on device");
	}
}