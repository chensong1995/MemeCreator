package encoder;


import org.opencv.core.Mat;

public class ColorTable {

	final int colorTableSize = 256;
	private ColorTableEntry[] table = new ColorTableEntry[colorTableSize];
	private int[] medians;
	private int[] colorIndices;
	private int[][] imageData;
	
	final int blueIndex = 0;
	final int greenIndex = 1;
	final int redIndex = 2;
	
	public ColorTable(Mat image) {
		imageData = new int[image.rows()*image.cols()][3];
		int index = 0;
		for (int i = 0; i < image.rows(); i++) {
			for (int j = 0; j < image.cols(); j++) {
				imageData[index][0] = (int) image.get(i, j)[blueIndex];
				imageData[index][1] = (int) image.get(i, j)[greenIndex];
				imageData[index][2] = (int) image.get(i, j)[redIndex];
				index++;
			}
		}
		medians = new int[1 << 9];
		colorIndices = new int [1 << 9];
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
	
	public int getColorIndex(double[] color) {
		if (color.length != 3) { 
			return 0; // something is wrong
		}
		
		
		// find the color with the smallest distance
		int min = 255*255 + 255*255 + 255*255;
		int minIndex = 0;
		int index = 0;
		do {
			int dist = ((int)table[index].red - (int)color[2])*((int)table[index].red - (int)color[2]) + ((int)table[index].green - (int)color[1])*((int)table[index].green - (int)color[1]) + ((int)table[index].blue - (int)color[0])*((int)table[index].blue - (int)color[0]);
			if (dist < min) {
				minIndex = index;
				min = dist;
			}
		} while (++index < colorTableSize);
		return minIndex;
		
		/*
		int index = 1;
		int level = 0;
		while (level < 8) {
			int median = medians[index];
			int colorIndex = colorIndices[index];
			int colorToBeTested = (int) color[colorIndex];
			index *= 2;
			if (colorToBeTested > median) {
				index++;
			}
			level++;
		}
		return index&0xFF;*/
	}
	
	private void constructColorTable() {
		computeMedians(0, 1, 0, imageData.length-1);
	}
	
	private void computeMedians(int numberOfAssignedBits, int medianNodeIndex, int dataStart, int dataEnd) {
		// construct the color table using the median cut algorithm
		
		// sort
		int colorIndex = findTheMostScatteredColor(dataStart, dataEnd);
		colorIndices[medianNodeIndex] = colorIndex;
		quicksort(dataStart, dataEnd, colorIndex);
		
		// assign new median
		int medianIndex = (dataStart+dataEnd)/2;
		while (++medianIndex <= dataEnd && imageData[medianIndex][colorIndex] == imageData[(dataStart+dataEnd)/2][colorIndex]);
		medianIndex = medianIndex - 1;
		medians[medianNodeIndex] = imageData[medianIndex][colorIndex];
		
		// compute the next bit
		if (numberOfAssignedBits < 7) {
			computeMedians(numberOfAssignedBits+1, medianNodeIndex*2, dataStart, medianIndex);
			computeMedians(numberOfAssignedBits+1, medianNodeIndex*2+1, medianIndex+1, dataEnd);
		} else {
			// fill the table
			
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
			
			
			int path = medianNodeIndex & 0x7F;
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
