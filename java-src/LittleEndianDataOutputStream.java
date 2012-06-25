import java.io.*;

public class LittleEndianDataOutputStream extends FilterOutputStream implements DataOutput{

	public LittleEndianDataOutputStream(OutputStream out) {
		super(out);
	}

	public void writeBoolean(boolean b) throws IOException {
		if (b)
			this.write(1);
		else
			this.write(0);
	}

	public void writeByte(int b) throws IOException {
		out.write(b);
	}

	public void writeShort(int s) throws IOException {
		out.write(s & 0xFF);
		out.write((s >>> 8) & 0xFF);
	}

	public void writeChar(int c) throws IOException {
		out.write(c & 0xFF);
		out.write((c >>> 8) & 0xFF);
	}

	public void writeInt(int i) throws IOException {
		out.write(i & 0xFF);
		out.write((i >>> 8) & 0xFF);
		out.write((i >>> 16) & 0xFF);
		out.write((i >>> 24) & 0xFF);

	}

	public void writeLong(long l) throws IOException {
		out.write((int) l & 0xFF);
		out.write((int) (l >>> 8) & 0xFF);
		out.write((int) (l >>> 16) & 0xFF);
		out.write((int) (l >>> 24) & 0xFF);
		out.write((int) (l >>> 32) & 0xFF);
		out.write((int) (l >>> 40) & 0xFF);
		out.write((int) (l >>> 48) & 0xFF);
		out.write((int) (l >>> 56) & 0xFF);
	}

	public final void writeFloat(float f) throws IOException {
		this.writeInt(Float.floatToIntBits(f));
	}

	public final void writeDouble(double d) throws IOException {
		this.writeLong(Double.doubleToLongBits(d));
	}

	public void writeBytes(String s) throws IOException {
		int length = s.length();
		for (int i = 0; i < length; i++) {
			out.write((byte) s.charAt(i));
		}
	}

	public void writeChars(String s) throws IOException {

		int length = s.length();
		for (int i = 0; i < length; i++) {
			int c = s.charAt(i);
			out.write(c & 0xFF);
			out.write((c >>> 8) & 0xFF);
		}

	}

	public void writeUTF(String s) throws IOException {
		int numchars = s.length();
		int numbytes = 0;

		for (int i = 0; i < numchars; i++) {
			int c = s.charAt(i);
			if ((c >= 0x0001) && (c <= 0x007F))
				numbytes++;
			else if (c > 0x07FF)
				numbytes += 3;
			else
				numbytes += 2;
		}
		if (numbytes > 65535)
			throw new UTFDataFormatException();

		out.write((numbytes >>> 8) & 0xFF);
		out.write(numbytes & 0xFF);
		for (int i = 0; i < numchars; i++) {
			int c = s.charAt(i);
			if ((c >= 0x0001) && (c <= 0x007F)) {
				out.write(c);
			} else if (c > 0x07FF) {
				out.write(0xE0 | ((c >> 12) & 0x0F));
				out.write(0x80 | ((c >> 6) & 0x3F));
				out.write(0x80 | (c & 0x3F));

			} else {
				out.write(0xC0 | ((c >> 6) & 0x1F));
				out.write(0x80 | (c & 0x3F));
			}
		}

	}

}