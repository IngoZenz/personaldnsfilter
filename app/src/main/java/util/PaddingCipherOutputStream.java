package util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class PaddingCipherOutputStream extends OutputStream {
	
	private ByteArrayOutputStream bytesBuffer;
	private DataOutputStream lowerOut;
	private OutputStream underlying;
	private int bufSize;
	
	
	public PaddingCipherOutputStream (OutputStream underlying, int bufSize) throws IOException {
		this.bufSize= bufSize;
		this.underlying =underlying; 
		bytesBuffer = new ByteArrayOutputStream(1024);	//will resize if needed	
	}
	
	private void init() throws IOException {		
		if (lowerOut != null)
			return;
		
		lowerOut = new DataOutputStream(underlying);
		lowerOut.writeInt(Encryption.ENCR_INIT_BYTES.length);
		lowerOut.write(Encryption.ENCR_INIT_BYTES);
		
	}

	@Override
	public void write(int oneByte) throws IOException {
		init();	
		bytesBuffer.write(oneByte);
		bytesBuffer.flush();
		if (bytesBuffer.size() >= bufSize)
			writeNext();
	}
	
	@Override
	public void write(byte b[], int off, int len) throws IOException {
		init();
		bytesBuffer.write(b,off,len);
		bytesBuffer.flush();
		if (bytesBuffer.size()>= bufSize)
			writeNext();
	}
	
	@Override
	public void write(byte b[]) throws IOException {
		write(b,0,b.length);		
	}
	
	@Override
	public void flush() throws IOException {
		init();
		writeNext();
		lowerOut.flush();		
	}
	
	public void writeNext() throws IOException {
		bytesBuffer.flush();
		if (bytesBuffer.size() == 0)
			return;
		byte[] buf = bytesBuffer.toByteArray();		
		
		bytesBuffer.reset();
		try {
			byte[] encrypted = Encryption.encrypt(buf);
			lowerOut.writeInt(encrypted.length);
			lowerOut.write(encrypted);			
		} catch (IOException ioe) {
			throw ioe;
		} catch (Exception e) {
			throw new IOException(e.getMessage());
		}	
	}		
	
	
	public void close() throws IOException {
		init();
		writeNext();
		//lowerOut.writeInt(0);	
		lowerOut.flush();
		lowerOut.close();
	}	
	
}
