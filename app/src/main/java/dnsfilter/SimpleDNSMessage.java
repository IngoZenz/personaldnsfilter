package dnsfilter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.StringTokenizer;


public class SimpleDNSMessage {
	
	byte[] data;
	int offs;
	int length;
	int rqFlgs;
	int resFlgs;
	String qHost;
	short qType;
	short qClass;

	
	public SimpleDNSMessage(byte[] data, int offs, int length) throws IOException {
	    this.data = data;
		this.offs = offs;
		this.length = length;
		rqFlgs = data[offs+2]&0xFF;
		resFlgs = data[offs+3]&0xFF;		
		
		if (isStandardQuery()) {
			ByteBuffer buf = ByteBuffer.wrap(data, offs, length);
			buf.position(offs+12);
	        qHost =  DNSResponsePatcher.readDomainName(buf, buf.arrayOffset());
	        qType = buf.getShort();
	        qClass = buf.getShort();
		}		
	}

    public boolean isStandardQuery() {
        return ( (rqFlgs >> 3) == 0);
    }
    
    public Object[] getQueryData() {
    	return new Object[] {qHost, qType, qClass};    	
    }
    
    public int produceResponse(byte[] response, int offset,  byte[] ip) {
    	
        System.arraycopy(data, offs, response, offset, length);
    	//response[offset+2] = (byte) (((1<<7) + (response[offset+2] & 0b01111111)) | 0b00000100); // response flag and Authoritive answer
        response[offset+2] = (byte) ((1<<7) + (response[offset+2] & 0b01111111));
        response[offset+3] = (byte) (1<<7); //recursion available

    	ByteBuffer buf = ByteBuffer.wrap(response, offset, response.length-offset);
    	buf.position(offset+4);
    	buf.putShort((short)1); //Q-count
    	buf.putShort((short)1); // A-count
    	buf.putShort((short)0); // Auth-count
    	buf.putShort((short)0); //Add-count   	
    	
    	
    	StringTokenizer chainElements = new StringTokenizer(qHost,".");
    	int count = chainElements.countTokens();
    	
    	//QUESTION
    	
    	//set request host
        for (int i = 0; i < count; i++) {        	
        	String element = chainElements.nextToken();
            buf.put((byte)(element.length() & 0xFF));
            buf.put(element.getBytes());
        }
        buf.put((byte)0);
        
        //Query type and class
        buf.putShort(qType); // Q-Type:1
        buf.putShort(qClass); // Q-Class:1
        
        
        //ANSWER
        
        short ptr = (short)((short)0xC0 << 8) +12;
        buf.putShort(ptr);  //pointer to req host
        buf.putShort(qType); // Q-Type:1
        buf.putShort(qClass); // Q-Class:1
        buf.putInt(0); //TTL:0
        buf.putShort((short)ip.length); // IP Len
        buf.put(ip);
        
        return buf.position()- offset;    	
    }

}
