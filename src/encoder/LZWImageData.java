package encoder;

public class LZWImageData {
	
	byte[] buffer;
	int currentByteIndex;
	int savedBlockSizeIndex;
	int currentBlockLength;
	int availableBitsInCurrentByte;
	final int minimumCodeLength = 8;
	final int maxBlockLength = 256; // according to the GIF specification
	
	public LZWImageData(int maxLength) {
		buffer = new byte[maxLength];
		currentByteIndex = 0;

		// add initial LZW code size (8)
		buffer[currentByteIndex++] = (byte)minimumCodeLength;
		
		// create the first sub-block
		savedBlockSizeIndex = currentByteIndex++; // fill the sub-block size record later when this sub-block terminates
		currentBlockLength = 1; // 1 byte for the size record
		availableBitsInCurrentByte = 8; // how many bits in packBuffer[currentByteIndex] are available
		
	}
	
	public void write(int code, int numberOfBits) {
		// construct the bit pattern of code
		int currentCodeLength = 0;
		while (currentCodeLength < numberOfBits) {
			// output bit by bit
			int theLastBit = code & 0x1; // take the last bit
			buffer[currentByteIndex] |= (theLastBit << (8 - availableBitsInCurrentByte));
			availableBitsInCurrentByte--;
			if (availableBitsInCurrentByte == 0) { // packBuffer[currentByteIndex] is exhausted
				// start a new byte
				currentByteIndex++;
				availableBitsInCurrentByte = 8;
				currentBlockLength++;
				if (currentBlockLength == maxBlockLength) { // the current sub-block is exhausted
					buffer[savedBlockSizeIndex] = (byte) (maxBlockLength - 1); // fill the sub-block size record
					// start a new block
					savedBlockSizeIndex = currentByteIndex++;
					currentBlockLength = 1;
				}
			}
			currentCodeLength++;
			code >>= 1; 
		}
	}
	
	public byte[] getData() {
		if (currentBlockLength != 1) { // clean-up work for the last block is needed
			if (availableBitsInCurrentByte != 8) { // terminate the last half-filled byte
				currentByteIndex++;
				currentBlockLength++;
			}
			buffer[savedBlockSizeIndex] = (byte) (currentBlockLength - 1); // fill the sub-block size record
		}
		
		// add block Terminator (0)
		buffer[currentByteIndex++] = (byte) 0x0;
		
		// shrink the length
		byte[] shrinkedBuffer = new byte[currentByteIndex];
		System.arraycopy(buffer, 0, shrinkedBuffer, 0, currentByteIndex);
		
		return shrinkedBuffer;
	}
}
