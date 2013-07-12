import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;


public class BigEndianDataOutputStream extends DataOutputStream implements UnsignedDataOutput {

	public BigEndianDataOutputStream(OutputStream out) {
		super(out);
	}

	@Override
	public void writeUnsignedInt(long i) throws IOException {
		out.write((int) ((i >>> 24) & 0xFF));
        out.write((int) ((i >>> 16) & 0xFF));
        out.write((int) ((i >>>  8) & 0xFF));
        out.write((int) ((i >>>  0) & 0xFF));
	}

}
