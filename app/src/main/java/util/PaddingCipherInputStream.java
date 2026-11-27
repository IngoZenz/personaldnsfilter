package util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

public class PaddingCipherInputStream extends InputStream {

	private static final int MAX_FRAME_SIZE = 32 * 1024 * 1024; // 32 MiB defensive cap

	private DataInputStream lowerIn;
	private ByteArrayInputStream byteBuf;
	private boolean eof = false;
	private boolean init = false;

	public PaddingCipherInputStream(InputStream underlying)
			throws IOException {
		lowerIn = new DataInputStream(underlying);
	}

	private void initRead() throws IOException {
		if (init) return;

		int size = -1;
		init = true;

		//check init bytes
		try {
			size = lowerIn.readInt();
		} catch (EOFException eofEx) {
			eof = true;
		}
		if (!eof) {
			if (size <= 0 || size > MAX_FRAME_SIZE)
				throw new IOException("Wrong keyphrase!");

			byte[] next = new byte[size];
			lowerIn.readFully(next);

			try {
				byte[] decrypted = Encryption.decrypt(next);
				if (!MessageDigest.isEqual(decrypted, Encryption.INIT_BYTES))
					throw new IOException("Wrong keyphrase!");
			} catch (IOException e) {
				throw new IOException("Wrong keyphrase!");
			}
		}
	}

	@Override
	public int read() throws IOException {
		if (!init)
			initRead();
		if (eof)
			return -1;
		if (byteBuf == null || byteBuf.available() == 0) {
			byteBuf = getNewBytes();
			if (byteBuf == null)
				return -1;
		}
		return byteBuf.read();
	}

	@Override
	public int read(byte b[], int off, int len) throws IOException {
		if (!init)
			initRead();
		if (available() != 0)
			return byteBuf.read(b, off, len);
		if (eof)
			return -1;

		byteBuf = getNewBytes();

		if (eof) return -1;

		return byteBuf.read(b, off, len);
	}

	@Override
	public int available() throws IOException {
		if (byteBuf != null)
			return byteBuf.available();
		return 0;
	}

	private ByteArrayInputStream getNewBytes() throws IOException {
		byte[] next = null;
		try {
			int size = lowerIn.readInt();
			if (size == 0) {
				byteBuf = null;
				eof = true;
				return null; // EOF
			}
			if (size < 0 || size > MAX_FRAME_SIZE)
				throw new IOException("Invalid encrypted frame size: " + size);
			next = new byte[size];
			lowerIn.readFully(next);
		} catch (EOFException e) {
			eof = true;
			byteBuf = null;
			return null;
		}

		try {
			byte[] decrypted = Encryption.decrypt(next);
			byteBuf = new ByteArrayInputStream(decrypted);
			return byteBuf;
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}
	}

	@Override
	public void close() throws IOException {
		lowerIn.close();
	}
}
