import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

import clojure.lang.BigInt;


public class BigEndianDataInputStream extends DataInputStream implements UnsignedDataInput {

	
	public BigEndianDataInputStream(CountingInputStream in) {
		super(in);
	}
	
	/* (non-Javadoc)
	 * @see UnsignedDataInput#readUnsignedInt()
	 */
	@Override
	public final long readUnsignedInt() throws IOException
	{
        long ch1 = this.read();
        long ch2 = this.read();
        long ch3 = this.read();
        long ch4 = this.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }
	final byte[] w = new byte[9];
	@Override
	public BigInt readUnsignedLong() throws IOException {
		this.read(w,1,8);
		boolean isMax = false;
		for (int i = 1; i < w.length; i++) {
			isMax |= (w[i]==255);
		}
		w[0]=(byte) (isMax?1:0);
		return  BigInt.fromBigInteger(new BigInteger(w));
	}

	@Override
	public long size() {
		return ((CountingInputStream)in).size();
	}
}
