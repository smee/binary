import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

import clojure.lang.BigInt;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * Not threadsafe!
 * @author sdienst
 *
 */
public class LittleEndianDataInputStream extends InputStream implements UnsignedDataInput {

	private final DataInputStream d;
	private final byte w[]; // work array for buffering input
	
	public LittleEndianDataInputStream(InputStream in) {
		this.d = new DataInputStream(in);
		w = new byte[9];
	}
	@Override
	public final short readShort() throws IOException
	{
		this.readFully(w, 0, 2);
		return (short)(
				(w[1]&0xff) << 8 |
				(w[0]&0xff));
	}

	@Override
	 public final int readUnsignedShort() throws IOException
	 {
		 this.readFully(w, 0, 2);
		 return (
				 (w[1]&0xff) << 8 |
				 (w[0]&0xff));
	 }

	@Override
	 public final char readChar() throws IOException
	 {
		 this.readFully(w, 0, 2);
		 return (char) (
				 (w[1]&0xff) << 8 |
				 (w[0]&0xff));
	 }

	@Override
	 public final int readInt() throws IOException
	 {
		 this.readFully(w, 0, 4);
		 return
		 (w[3])      << 24 |
		 (w[2]&0xff) << 16 |
		 (w[1]&0xff) <<  8 |
		 (w[0]&0xff);
	 }
	@Override
	 public final long readUnsignedInt() throws IOException
	 {
		 this.readFully(w, 0, 4);
		 return
				 (w[3])      << 24 |
				 (w[2]&0xff) << 16 |
				 (w[1]&0xff) <<  8 |
				 (w[0]&0xff);
	 }
	@Override
	 public final long readLong() throws IOException
	 {
		 this.readFully(w, 0, 8);
		 return
		 (long)(w[7])      << 56 | 
		 (long)(w[6]&0xff) << 48 |
		 (long)(w[5]&0xff) << 40 |
		 (long)(w[4]&0xff) << 32 |
		 (long)(w[3]&0xff) << 24 |
		 (long)(w[2]&0xff) << 16 |
		 (long)(w[1]&0xff) <<  8 |
		 (long)(w[0]&0xff);
	 }
	@Override
	public BigInt readUnsignedLong() throws IOException {
		d.readFully(w,1,8);
		boolean isMax=false;
		// reverse byte array
		for (int i = 1; i < 5; i++) {
			byte b = w[i];
			w[i]=w[9-i];
			w[9-i] = b;
			isMax |= (b==255);
		}
		w[0]=(byte) (isMax?1:0);
		return BigInt.fromBigInteger(new BigInteger(w));
	}
	@Override
	 public final float readFloat() throws IOException {
		 return Float.intBitsToFloat(readInt());
	 }
	@Override
	 public final double readDouble() throws IOException {
		 return Double.longBitsToDouble(readLong());
	 }
	@Override
	 public final int read(byte b[], int off, int len) throws IOException {
		 return d.read(b, off, len);
	 }
	@Override
	 public final void readFully(byte b[]) throws IOException {
		 d.readFully(b, 0, b.length);
	 }
	@Override
	 public final void readFully(byte b[], int off, int len) throws IOException {
		 d.readFully(b, off, len);
	 }
	@Override
	 public final int skipBytes(int n) throws IOException {
		 return d.skipBytes(n);
	 }
	@Override
	 public final boolean readBoolean() throws IOException {
		 return d.readBoolean();
	 }
	@Override
	 public final byte readByte() throws IOException {
		 return d.readByte();
	 }
	@Override
	 public final int read() throws IOException {
		 return d.read();
	 }

	 public final int readUnsignedByte() throws IOException {
		 return d.readUnsignedByte();
	 }
	 
	 @Override
	 @Deprecated
	 public final String readLine() throws IOException {
		 throw new RuntimeException("unimplemented");
	 }
	 @Override
	 public final String readUTF() throws IOException {
		 throw new RuntimeException("unimplemented");
	 }
	 @Override
	 public final void close() throws IOException {
		 this.close();
	 }

}


