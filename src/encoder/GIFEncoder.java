package encoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opencv.core.Mat;

public class GIFEncoder {
	
	private final int targetBitsPerPixel = 8;
	
	private Mat[] originalImages;
	private ByteBuffer buffer;
	
	public GIFEncoder(Mat[] images) {
		originalImages = images;
		buffer = ByteBuffer.allocate(computeMaximumBufferSize());
		buffer.order(ByteOrder.LITTLE_ENDIAN);
	}
	
	public byte[] encode(short delayInMilliSeconds) {
		writeHeader();
		writeScreenDescriptor();
		for (Mat image : originalImages) {
			writeGraphicsControlExtension(delayInMilliSeconds); // 89a
			writeImageDescriptor(image);
			ColorTable colorTable = new ColorTable(image);
			writeColorTable(colorTable);
			writeImageData(image, colorTable);
		}
		writeTrailer();
		byte[] result = new byte[buffer.position()];
		buffer.rewind();
		buffer.get(result);
		return result;
	}
	
	private int computeMaximumBufferSize() {
		final int headerSize = 6;
		final int screenDescriptorSize = 7;
		final int colorTableSize = (1 << targetBitsPerPixel) * 3; // only use local color tables
		final int imageDescriptorSize = 10;
		final int imageSize = originalImages[0].cols() * originalImages[0].rows() * targetBitsPerPixel; // size before LZW compression
		final int totalSize = headerSize + screenDescriptorSize + (imageDescriptorSize + colorTableSize + imageSize) * originalImages.length;
		
		return totalSize;
	}
	
	private void writeHeader() {
		final byte[] signature = {(byte)'G', (byte)'I', (byte)'F'};
		buffer.put(signature);
		final byte[] version = {(byte)'8', (byte)'9', (byte)'a'};
		buffer.put(version);
	}
	
	private void writeScreenDescriptor() {
		final short screenWidth = (short) originalImages[0].cols();
		buffer.putShort(screenWidth);
		final short screenHeight = (short) originalImages[0].rows();
		buffer.putShort(screenHeight);
		final byte packed = (byte)0x70; // global color table disabled, 8bit resolution
		buffer.put(packed);
		final byte backgroundColor = (byte)0; // we are not going to use it
		buffer.put(backgroundColor);
		final byte pixelAspectRatio = (byte)0; // we are not going to specify it
		buffer.put(pixelAspectRatio);
	}
	
	private void writeImageDescriptor(Mat image) {
		byte separator = (byte) 0x2C;
		buffer.put(separator);
		short left = 0; // display the image at x=0
		buffer.putShort(left);
		short top = 0; // display the image at y=0
		buffer.putShort(top);
		short width = (short) image.cols();
		buffer.putShort(width);
		short height = (short) image.rows();
		buffer.putShort(height);
		byte packed = (byte) 0x87; // 1 0 0 00 111, local color table enabled, interlace scan disabled, sort disabled, 256 entries in the table
		buffer.put(packed);
	}
	
	private void writeColorTable(ColorTable colorTable) {
		buffer.put(colorTable.getRawData());
	}
	
	private void writeGraphicsControlExtension(short delayInMilliSeconds) {
		final byte introducer = (byte) 0x21;
		buffer.put(introducer);
		final byte label = (byte) 0xf9;
		buffer.put(label);
		final byte blocksize = (byte) 0x04;
		buffer.put(blocksize);
		final byte packed = (byte) 0x00; // 000 00 0 0
		buffer.put(packed);
		final short delayTime = (short) (delayInMilliSeconds / 10);
		buffer.putShort(delayTime);
		final byte colorIndex = (byte) 0x00;
		buffer.put(colorIndex);
		final byte terminator = (byte) 0x00;
		buffer.put(terminator);
	}
	
	private void writeImageData(Mat image, ColorTable colorTable) {
		// first generate the uncompressed image data
		int[] uncompressedData = colorTable.getImageIndices();
		
		// next we do the LZW compression
		byte[] compressedData = compressImageData(uncompressedData);
		
		buffer.put(compressedData);
	}
	
	private byte[] compressImageData(int[] uncompressedData) {
		Map<List<Integer>, Integer> dictionary = new HashMap<List<Integer>, Integer>();
		int code = 0;
		
		// add initial records to the dictionary
		while (code < 256) {
			dictionary.put(new ArrayList<Integer>(Arrays.asList(code)), code);
			code++;
		}
		int clearCode = code++;
		int endOfInformationCode = code++;
		
		// store the initial dictionary
		final int initialCode = code;
		Map<List<Integer>, Integer> initialDictionary = new HashMap<List<Integer>, Integer>(dictionary);
		
		// LZW algorithm begins
		LZWImageData packedCodes = new LZWImageData(uncompressedData.length);
		int currentCodeLength = 9;
		int currentCodeLimit = (1 << currentCodeLength) - 1;
		final int maxCodeLength = 12; // the specification says, "up to 12 bits per code"
		packedCodes.write(clearCode, currentCodeLength); // send out the first clear code
		
		List<Integer> savedInput = new ArrayList<Integer>(Arrays.asList(uncompressedData[0]));
		for (int i = 1; i < uncompressedData.length; i++) {
			int currentInput = uncompressedData[i];
			savedInput.add(currentInput);
			if (!dictionary.containsKey(savedInput)) {
				dictionary.put(new ArrayList<Integer>(savedInput), code); // add the new string to the dictionary with a new code
				savedInput.remove(savedInput.size()-1); // remove the current input
				packedCodes.write(dictionary.get(savedInput), currentCodeLength);
				savedInput = new ArrayList<Integer>(Arrays.asList(currentInput));
				if (code++ > currentCodeLimit) {
					currentCodeLength++;
					if (currentCodeLength > maxCodeLength) { // code is too long
						// send out a clear code
						packedCodes.write(clearCode, currentCodeLength - 1); // send out the first clear code
						// reset the dictionary
						dictionary = new HashMap<List<Integer>, Integer>(initialDictionary);
						code = initialCode;
						currentCodeLength = 9;
					}
					currentCodeLimit = (1 << currentCodeLength) - 1;
				}
			}
		}
		if (!savedInput.isEmpty()) {
			packedCodes.write(dictionary.get(savedInput), currentCodeLength);
		}
		packedCodes.write(endOfInformationCode, currentCodeLength);
		
		return packedCodes.getData();
	}
	
	private void writeTrailer() {
		buffer.put((byte) 0x3B);
	}
	
}
