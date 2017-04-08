package encoder;


import org.opencv.core.Mat;

public class ColorTable {

	final int colorTableSize = 256;
	private ColorTableEntry[] table = new ColorTableEntry[colorTableSize];
	private int[][] imageData;
	private int[][] imageIndices;
	private int imageRowCount;
	private int imageColCount;
	
	
	final int blueIndex = 0;
	final int greenIndex = 1;
	final int redIndex = 2;
	
	public ColorTable(Mat image) {
		imageData = new int[image.rows()*image.cols()][5];
		imageIndices = new int[image.rows()][image.cols()];
		imageRowCount = image.rows();
		imageColCount = image.cols();
		int index = 0;
		for (int i = 0; i < image.rows(); i++) {
			for (int j = 0; j < image.cols(); j++) {
				imageData[index][0] = (int) image.get(i, j)[blueIndex];
				imageData[index][1] = (int) image.get(i, j)[greenIndex];
				imageData[index][2] = (int) image.get(i, j)[redIndex];
				imageData[index][3] = i;
				imageData[index][4] = j;
				index++;
			}
		}
		constructColorTable();
	}
	
	public byte[] getRawData() {
		byte[] rawData = new byte[colorTableSize*3];
		for (int i = 0; i < colorTableSize; i++) {
			rawData[i*3] = table[i].red;
			rawData[i*3+1] = table[i].green;
			rawData[i*3+2] = table[i].blue;
		}
		return rawData;
	}
	
	public int[] getImageIndices() {
		int[] data = new int[imageRowCount*imageColCount];
		int index = 0;
		for (int i = 0; i < imageRowCount; i++) {
			for (int j = 0; j < imageColCount; j++) {
				data[index++] = imageIndices[i][j];
			}
		}
		return data;
	}
	
	
	private void constructColorTable() {
		computeMedians(0, 1, 0, imageData.length-1);
	}
	
	private void computeMedians(int numberOfAssignedBits, int medianNodeIndex, int dataStart, int dataEnd) {
		// construct the color table using the median cut algorithm
		
		// sort
		int colorIndex = findTheMostScatteredColor(dataStart, dataEnd);
		quicksort(dataStart, dataEnd, colorIndex);
		
		// assign new median
		int medianIndex = (dataStart+dataEnd)/2;
		while (++medianIndex <= dataEnd && imageData[medianIndex][colorIndex] == imageData[(dataStart+dataEnd)/2][colorIndex]);
		medianIndex = medianIndex - 1;
		
		// compute the next bit
		if (numberOfAssignedBits < 7) {
			computeMedians(numberOfAssignedBits+1, medianNodeIndex*2, dataStart, medianIndex);
			computeMedians(numberOfAssignedBits+1, medianNodeIndex*2+1, medianIndex+1, dataEnd);
		} else {
			// fill the table
			
			int path = medianNodeIndex & 0x7F;
			
			for (int i = dataStart; i <= medianIndex; i++) {
				imageIndices[imageData[i][3]][imageData[i][4]] = path*2;
			}
			for (int i = medianIndex+1; i <= dataEnd; i++) {
				imageIndices[imageData[i][3]][imageData[i][4]] = path*2+1;
			}
			
			
			int greenSmall, greenLarge, redSmall, redLarge, blueSmall, blueLarge;
			
			if (colorIndex == greenIndex) {
				greenSmall = imageData[(dataStart+medianIndex)/2][greenIndex];
				greenLarge = imageData[(medianIndex+1+dataEnd)/2][greenIndex];
				
				// compute red
				quicksort(dataStart, medianIndex, redIndex);
				quicksort(medianIndex+1, dataEnd, redIndex);
				redSmall = imageData[(dataStart+medianIndex)/2][redIndex];
				redLarge = imageData[(medianIndex+1+dataEnd)/2][redIndex];
				
				// compute blue
				quicksort(dataStart, medianIndex, blueIndex);
				quicksort(medianIndex+1, dataEnd, blueIndex);
				blueSmall = imageData[(dataStart+medianIndex)/2][blueIndex];
				blueLarge = imageData[(medianIndex+1+dataEnd)/2][blueIndex];
			} else if (colorIndex == blueIndex) {
				blueSmall = imageData[(dataStart+medianIndex)/2][blueIndex];
				blueLarge = imageData[(medianIndex+1+dataEnd)/2][blueIndex];
				
				// compute green
				quicksort(dataStart, medianIndex, greenIndex);
				quicksort(medianIndex+1, dataEnd, greenIndex);
				greenSmall = imageData[(dataStart+medianIndex)/2][greenIndex];
				greenLarge = imageData[(medianIndex+1+dataEnd)/2][greenIndex];
				
				// compute red
				quicksort(dataStart, medianIndex, redIndex);
				quicksort(medianIndex+1, dataEnd, redIndex);
				redSmall = imageData[(dataStart+medianIndex)/2][redIndex];
				redLarge = imageData[(medianIndex+1+dataEnd)/2][redIndex];
				
			} else {
				redSmall = imageData[(dataStart+medianIndex)/2][redIndex];
				redLarge = imageData[(medianIndex+1+dataEnd)/2][redIndex];
				
				// compute green
				quicksort(dataStart, medianIndex, greenIndex);
				quicksort(medianIndex+1, dataEnd, greenIndex);
				greenSmall = imageData[(dataStart+medianIndex)/2][greenIndex];
				greenLarge = imageData[(medianIndex+1+dataEnd)/2][greenIndex];
				
				// compute blue
				quicksort(dataStart, medianIndex, blueIndex);
				quicksort(medianIndex+1, dataEnd, blueIndex);
				blueSmall = imageData[(dataStart+medianIndex)/2][blueIndex];
				blueLarge = imageData[(medianIndex+1+dataEnd)/2][blueIndex];
			}
			
			
			table[path*2] = new ColorTableEntry((byte)redSmall, (byte)greenSmall, (byte)blueSmall);
			table[path*2+1] = new ColorTableEntry((byte)redLarge, (byte)greenLarge, (byte)blueLarge);
				
		}
	}
	
	private int findTheMostScatteredColor(int dataStart, int dataEnd) {
		if (dataStart <= dataEnd) {
			int[] blueBins = new int[256];
			int[] greenBins = new int[256];
			int[] redBins = new int[256];
			for (int i = dataStart; i <= dataEnd; i++) {
				blueBins[imageData[i][blueIndex]]++;
				greenBins[imageData[i][greenIndex]]++;
				redBins[imageData[i][redIndex]]++;
			}
			
			// compute the averages
			long blueSum = 0;
			long greenSum = 0;
			long redSum = 0;
			for (int i = 1; i < 256; i++) { // ignore i=0, no contribution to the sum
				blueSum += blueBins[i] * i;
				greenSum += greenBins[i] * i;
				redSum += redBins[i] * i;
			}
			int blueAverage = (int) (blueSum / (dataEnd-dataStart+1));
			int greenAverage = (int) (greenSum / (dataEnd-dataStart+1));
			int redAverage = (int) (redSum / (dataEnd-dataStart+1));
			
			// compute the variance
			long blueVariance = 0;
			long greenVariance = 0;
			long redVariance = 0;
			for (int i = 0; i < 256; i++) {
				blueVariance += blueBins[i] * Math.abs(i - blueAverage);
				greenVariance += greenBins[i] * Math.abs(i - greenAverage);
				redVariance += redBins[i] * Math.abs(i - redAverage);
			}
			
			if (blueVariance >= greenVariance && blueVariance >= redVariance) {
				return blueIndex;
			} else if (greenVariance >= blueVariance && greenVariance >= redVariance) {
				return greenIndex;
			} else {
				return redIndex;
			}
		} 
		
		return blueIndex;
		
		
	}
	
	private void quicksort(int low, int high, int colorIndex) {
		if (low < high) {
	        int i = low, j = high;
	        int pivot = imageData[low + (high-low)/2][colorIndex];

	        while (i <= j) {
	            while (imageData[i][colorIndex] < pivot) {
	                i++;
	            }
	            while (imageData[j][colorIndex] > pivot) {
	                j--;
	            }
	            if (i <= j) {
	                int[] temp = imageData[i];
	                imageData[i] = imageData[j];
	                imageData[j] = temp;
	                i++;
	                j--;
	            }
	        }
	        if (low < j) {
	            quicksort(low, j, colorIndex);
	        }
	        if (i < high) {
	            quicksort(i, high, colorIndex);
	        }
		}
	}
	
}
