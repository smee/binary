import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import clojure.lang.BigInt;


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

	@Override
	public void writeUnsignedShort(int v) throws IOException {
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 0) & 0xFF);
	}

	@Override
	public void writeUnsignedLong(BigInt bi) throws IOException {
		byte[] arr = bi.toBigInteger().toByteArray();
		int arrayLength = arr.length;
		boolean isLongerThanLong = arrayLength>8;
		if(isLongerThanLong && arr[0]>1)
			throw new ArithmeticException("unsigned long is too big! Would truncate on write!");
		int offset = isLongerThanLong?1:0;
		int len = isLongerThanLong?8:arrayLength;
		byte[] toWrite = new byte[8];
		System.arraycopy(arr, offset, toWrite, 8-len, len);
		out.write(toWrite,0,8);		
	}

}
