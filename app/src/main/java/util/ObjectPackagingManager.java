package util;

import java.io.IOException;

public interface ObjectPackagingManager {
	
	public int objectSize();
	
	public  Object bytesToObject(byte[] data, int offs);
	
	public void objectToBytes (Object object, byte[] data, int offs);	
	
}
