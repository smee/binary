import java.io.DataOutput;
import java.io.IOException;


public interface UnsignedDataOutput extends DataOutput{
	void writeUnsignedInt(long i) throws IOException;
}
