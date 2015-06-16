package impl;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;


/** This class uses a memory cache to allow seeking within
    an InputStream. Based on the JAI MemoryCacheSeekableStream class.
    Can also be constructed from a RandomAccessFile, which uses less
    memory since the memory cache is not required.
    
    Copied and adapted from http://imagej.nih.gov/ij/developer/source/ij/io/RandomAccessStream.java.html (public domain)
*/ 
public final class RandomAccessInputStream extends InputStream {

    private static final int BLOCK_SIZE = 1024;
    private static final int BLOCK_MASK = 1023;
    private static final int BLOCK_SHIFT = 10;

    private InputStream src;
    private RandomAccessFile ras;
    private long pointer;
    private List<byte[]> data;
    private long length;
    private boolean foundEOS;
    
    /** Constructs a RandomAccessStream from an InputStream. Seeking
        backwards is supported using a memory cache. */
    public RandomAccessInputStream(InputStream inputstream) {
        pointer = 0L;
        data = new ArrayList<byte[]>();
        length = 0L;
        foundEOS = false;
        src = inputstream;
    }

    /** Constructs a RandomAccessStream from an RandomAccessFile. */
    public RandomAccessInputStream(RandomAccessFile ras) {
        this.ras = ras;
    }


    public long getOffset() throws IOException {
        if (ras!=null)
            return ras.getFilePointer();
        else
            return pointer;
    }

    public int read() throws IOException {
        if (ras!=null)
            return ras.read();
        long l = pointer + 1L;
        long l1 = readUntil(l);
        if (l1>=l) {
            byte abyte0[] = data.get((int)(pointer>>BLOCK_SHIFT));
            return abyte0[(int)(pointer++ & BLOCK_MASK)] & 0xff;
        } else
            return -1;
    }

    public int read(byte[] bytes, int off, int len) throws IOException {
        if(bytes == null)
            throw new NullPointerException();
        if (ras!=null)
            return ras.read(bytes, off, len);
        if (off<0 || len<0 || off+len>bytes.length)
            throw new IndexOutOfBoundsException();
        if (len == 0)
            return 0;
        long l = readUntil(pointer+len);
        if (l<=pointer)
            return -1;
        else {
            byte abyte1[] = data.get((int)(pointer >> BLOCK_SHIFT));
            int k = Math.min(len, BLOCK_SIZE - (int)(pointer & BLOCK_MASK));
            System.arraycopy(abyte1, (int)(pointer & BLOCK_MASK), bytes, off, k);
            pointer += k;
            return k;
        }
    }

    private long readUntil(long l) throws IOException {
        if (l<length)
            return l;
        if (foundEOS)
            return length;
        int i = (int)(l>>BLOCK_SHIFT);
        int j = (int)(length>>BLOCK_SHIFT);
        for (int k=j; k<=i; k++) {
            byte abyte0[] = new byte[BLOCK_SIZE];
            data.add(abyte0);
            int i1 = BLOCK_SIZE;
            int j1 = 0;
            while (i1>0) {
                int k1 = src.read(abyte0, j1, i1);
                if (k1==-1) {
                    foundEOS = true;
                    return length;
                }
                j1 += k1;
                i1 -= k1;
                length += k1;
            }
        }
        return length;
    }

    public void seek(long loc) throws IOException {
        if (ras!=null)
            {ras.seek(loc); return;}
        if (loc<0L)
            pointer = 0L;
        else
            pointer = loc;
    }

    public void close() throws IOException {
        if (ras!=null)
            ras.close();
        else {
            data.clear();
            src.close();
        }
    }
    
 
}