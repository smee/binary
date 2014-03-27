import java.io.DataOutput;
import java.io.IOException;

import clojure.lang.BigInt;


public interface UnsignedDataOutput extends DataOutput{
	void writeUnsignedShort(int i) throws IOException;
	void writeUnsignedInt(long i) throws IOException;
	void writeUnsignedLong(BigInt i) throws IOException;
}
