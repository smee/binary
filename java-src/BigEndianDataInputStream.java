import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;


public class BigEndianDataInputStream extends DataInputStream implements UnsignedDataInput {

	public BigEndianDataInputStream(InputStream in) {
		super(in);
	}
	
	/* (non-Javadoc)
	 * @see UnsignedDataInput#readUnsignedInt()
	 */
	@Override
	public final long readUnsignedInt() throws IOException
	{
        long ch1 = in.read();
        long ch2 = in.read();
        long ch3 = in.read();
        long ch4 = in.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }
	

}
