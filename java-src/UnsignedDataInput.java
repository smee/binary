import java.io.DataInput;
import java.io.IOException;

import clojure.lang.BigInt;

public interface UnsignedDataInput extends DataInput {

	long readUnsignedInt() throws IOException;
	int readUnsignedShort() throws IOException;
	BigInt readUnsignedLong() throws IOException;

}