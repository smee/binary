import java.io.DataInput;
import java.io.IOException;

public interface UnsignedDataInput extends DataInput {

	public abstract long readUnsignedInt() throws IOException;

}