package util.http;


import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import util.Utils;

public class HttpChunkedInputStream extends InputStream {

	private DataInputStream underlying = null;
	private ByteArrayInputStream bytesBuf = null;
	private boolean eof = false;

	public HttpChunkedInputStream(InputStream lowerIn) {
		underlying = new DataInputStream(lowerIn);
	}

	@Override
	public int read() throws IOException {
		if (available() != 0)
			return bytesBuf.read();
		if (eof)
			return -1;

		readNextChunk();

		if (eof)
			return -1;

		return bytesBuf.read();

	}

	@Override
	public int read(byte b[], int off, int len) throws IOException {
		if (available() != 0)
			return bytesBuf.read(b, off, len);
		if (eof)
			return -1;

		readNextChunk();

		if (eof)
			return -1;

		return bytesBuf.read(b, off, len);
	}

	@Override
	public int read(byte b[]) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int available() throws IOException {
		if (bytesBuf != null)
			return bytesBuf.available();

		return 0;
	}

	@Override
	public void close() throws IOException {
		// Underlying Must not be closed!!!
		// Otherwise connection can't be reused!
	}

	private void readNextChunk() throws IOException {

		String line = Utils.readLineFromStreamRN(underlying);

		if (line == null)
			throw new EOFException("Invalid end of ChunkedInputStream!");

		int extIdx = line.indexOf(';');
		String hexStr = (extIdx != -1 ? line.substring(0, extIdx) : line).trim();

		if (hexStr.equals("")) {
			eof = true;
			return;
		}
		int length = -1;
		try {
			length = Integer.parseInt(hexStr, 16);
		} catch (Exception e) {
			throw new IOException(e.toString());
		}
		if (length < 0)
			throw new IOException("Invalid negative chunk length: " + length);

		if (length != 0) {
			byte[] buf = new byte[length];
			underlying.readFully(buf);
			bytesBuf = new ByteArrayInputStream(buf);
			Utils.readLineFromStreamRN(underlying);
		} else {
			eof = true;
			// Consume optional trailer fields up to empty line
			String trailer;
			do {
				trailer = Utils.readLineFromStreamRN(underlying);
			} while (!trailer.equals(""));
		}

	}
}
