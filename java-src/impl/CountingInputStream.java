package impl;
import java.io.IOException;
import java.io.InputStream;

/**
 * 
 * @author sdienst
 *
 */
public class CountingInputStream extends InputStream {
	private long count;
	private final InputStream delegate;
	
	public CountingInputStream(InputStream in) {
		this.delegate = in;
	}
	/**
	 * Drop a number of bytes without returning anything.
	 * @param length
	 * @return
	 * @throws IOException
	 */
	public final long skip(long length) throws IOException {
		return this.count += delegate.skip(length);
	}
	/**
	 * @return number of bytes already read from the delegated inputstream
	 */
	public final long size() {
		return this.count;
	}

	@Override
	public final String toString() {
		return delegate.toString()+", "+count+" bytes read";
	}
	@Override
	public int read() throws IOException {
		int v = delegate.read();
		if(v>-1) count++;
		return v;
	}
//	public long getOffset() throws IOException {
//		return delegate.getOffset();
//	}
//	public void seek(long loc) throws IOException {
//		delegate.seek(loc);
//	}
//	public void mark(int readlimit) {
//		delegate.mark(readlimit);
//	}
//	public void reset() throws IOException {
//		delegate.reset();
//	}
}
