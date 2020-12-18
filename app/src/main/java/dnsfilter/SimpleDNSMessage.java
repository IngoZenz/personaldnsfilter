package dnsfilter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.StringTokenizer;


public class SimpleDNSMessage {
	
	byte[] data;
	int offs;
	int length;
	int rqFlgs;
	int resFlgs;
	String qHost;
	Short qType;
	Short qClass;	

	
	public SimpleDNSMessage(byte[] data, int offs, int length) throws IOException {
	    this.data = data;
		this.offs = offs;
		this.length = length;
		rqFlgs = data[offs+2]&0xFF;
		resFlgs = data[offs+3]&0xFF;		
		
		if (isStandardQuery()) {
			ByteBuffer buf = ByteBuffer.wrap(data, offs, length);
			buf.position(offs+12);
	        qHost =  readDomainName(buf, buf.arrayOffset());
	        qType = buf.getShort();
	        qClass = buf.getShort();
		}		
	}

    public boolean isStandardQuery() {
        return ( (rqFlgs >> 6) == 0);
    }
    
    public short getResponseFlag(){
    	return (short) (rqFlgs>>7);
    }
    
    public Object[] getQueryData() {
    	return new Object[] {qHost, qType, qClass};    	
    }
    
    public int produceResponse(byte[] response, int offset,  byte[] ip) {
    	
        System.arraycopy(data, offs, response, offset, length);
    	response[offset+2] = (byte) (((1<<7) + (response[offset+2] & 0b01111111)) | 0b00000100); // response flag and Authoritive answer
        response[offset+3] = (byte) (1<<7); //recursion available

    	ByteBuffer buf = ByteBuffer.wrap(response, offset, response.length-offset);
    	buf.position(offset+4);
    	buf.putShort((short)1); //Q-count
    	buf.putShort((short)1); // A-count
    	buf.putShort((short)0); // Auth-count
    	buf.putShort((short)0); //Add-count   	
    	
    	
    	StringTokenizer chainElements = new StringTokenizer(qHost,".");
    	
    	//QUESTION
    	
    	//set request host
        for (int i = 0; i < chainElements.countTokens(); i++) {
        	String element = chainElements.nextToken();
            buf.put((byte)(element.length() & 0xFF));
            buf.put(element.getBytes());
        }
        buf.put((byte)0);
        
        //Query type and class
        buf.putShort(qType); // Q-Type:1
        buf.putShort(qClass); // Q-Class:1
        
        
        //ANSWER
        
        short ptr = (short)((short)1 << 15) +12;
        buf.putShort(ptr);  //pointer to req host
        buf.putShort(qType); // Q-Type:1
        buf.putShort(qClass); // Q-Class:1
        buf.putInt(0); //TTL:0
        buf.putShort((short)ip.length); // IP Len
        buf.put(ip);
        
        return buf.position()- offset;
    	
    }

    
    private static String readDomainName(ByteBuffer buf, int offs) throws IOException {

        byte[] substr = new byte[64];

        int count = -1;
        String dot = "";
        String result = "";
        int ptrJumpPos = -1;

        while (count != 0) {
            count = buf.get();
            if (count != 0) {
                if ((count & 0xc0) == 0) {
                    buf.get(substr, 0, count);
                    result = result + dot + new String(substr, 0, count);
                    dot = ".";
                } else {// pointer
                    buf.position(buf.position() - 1);
                    int pointer = offs + (buf.getShort() & 0x3fff);
                    if (ptrJumpPos == -1)
                        ptrJumpPos = buf.position();
                    buf.position(pointer);
                }
            } else {
                if (count == 0 && ptrJumpPos != -1)
                    buf.position(ptrJumpPos);
            }
        }
        return result;
    }

	   

}
