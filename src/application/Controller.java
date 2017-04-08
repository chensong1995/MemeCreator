package application;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.controlsfx.control.RangeSlider;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import encoder.GIFEncoder;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import utilities.Utilities;

public class Controller {
	@FXML
	private ImageView imageView; // the image display window in the GUI
	@FXML
	private RangeSlider rangeSlider; // the range slider in the GUI
	@FXML
	private TextField textField; // the text field in the GUI
	@FXML
	private RadioButton quarterRadioButton; // the quarter resolution radio button in the GUI
	@FXML
	private RadioButton halfRadioButton; // the half resolution radio button in the GUI
	@FXML
	private RadioButton fullRadioButton; // the full resolution radio button in the GUI
	@FXML
	private ToggleGroup radioButtonGroup; // the group of 3 radio buttons
	
	private VideoCapture capture; // the video file
	
	private ScheduledExecutorService timer; // a timer for acquiring the video stream
	
	private String text; // the text to be put on the output GIF
	
	private String defaultDirectory = ""; // default directory for the file chooser
	
	
	@FXML
	protected void openVideo(ActionEvent event) {
		// select a file
		File recordsDir = new File(defaultDirectory);
	    FileChooser chooser = new FileChooser();
	    chooser.setTitle("Open Video File");
	    if (recordsDir != null && recordsDir.isDirectory()) {
		    chooser.setInitialDirectory(recordsDir);
	    }
	    File file = chooser.showOpenDialog(imageView.getScene().getWindow());
	    if (file != null && file.isFile()) {
	    	defaultDirectory = file.getParent();
			capture = new VideoCapture(file.getAbsolutePath()); // open video file
			if (capture.isOpened()) { // open successfully
				createFrameGrabber();
				
				ChangeListener<Number> changeListener = new ChangeListener<Number>() {
				      @Override 
				      public void changed(ObservableValue<? extends Number> observableValue, Number oldValue, Number newValue) {
				    	  try {
						  		if (capture.isOpened()) { // the video capture must be open
						  			
									double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
									double percentageOfFramesToBeSkipped = rangeSlider.getLowValue() / rangeSlider.getMax();
									double numberOfFramesToBeSkipped = totalFrameCount * percentageOfFramesToBeSkipped;
									double framePerSecond = capture.get(Videoio.CAP_PROP_FPS);

									// terminate the timer if it is running 
									if (timer != null && !timer.isShutdown()) {
										timer.shutdown();
										timer.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
									}
						  			
									// skip all frames before the starting point
									capture.set(Videoio.CAP_PROP_POS_FRAMES, numberOfFramesToBeSkipped);
									
									// decode that frame
									Mat frame = new Mat();
									if (capture.read(frame)) { // decode successfully
										
										// create a copy to that frame
										Mat frameCopy = new Mat();
										frame.copyTo(frameCopy);
										
										// add text
										addTextToImage(frameCopy, new Point((double)frameCopy.cols()*3/4, (double)frameCopy.rows()*3/4), new Scalar(255, 255, 255), 3);
										
										Image im = Utilities.mat2Image(frameCopy);
										imageView.setImage(im);
									}
									
									createFrameGrabber();
								}
				    	  } catch (InterruptedException e) {
				  			System.err.println("Something is wrong: " + e);;
				    	  }		
				      }
				 };
				rangeSlider.lowValueProperty().addListener(changeListener);
				rangeSlider.highValueProperty().addListener(changeListener);
			}
	    }
	}
	
	@FXML
	protected void createFrameGrabber(MouseEvent mouseEvent) {
		createFrameGrabber();	
	}
	
	protected void createFrameGrabber() {
		try {
			if (capture != null && capture.isOpened()) { // the video capture must be open

				double framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
				double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
				double LastFrameToBeDisplayed = rangeSlider.getHighValue() / rangeSlider.getMax() * totalFrameCount;
				
				// create a runnable to fetch new frames periodically
				Runnable frameGrabber = new Runnable() {
					@Override
					public void run() {
						Mat frame = new Mat();
						double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
						if (currentFrameNumber >= LastFrameToBeDisplayed) {
							double percentageOfFramesToBeSkipped = rangeSlider.getLowValue() / rangeSlider.getMax();
							double numberOfFramesToBeSkipped = totalFrameCount * percentageOfFramesToBeSkipped;
							capture.set(Videoio.CAP_PROP_POS_FRAMES, numberOfFramesToBeSkipped);
						}
						if (capture.read(frame)) { // decode successfully
							// create a copy to that frame
							Mat frameCopy = new Mat();
							frame.copyTo(frameCopy);
							
							// add text
							addTextToImage(frameCopy, new Point((double)frameCopy.cols()*3/4, (double)frameCopy.rows()*3/4), new Scalar(255, 255, 255), 3);
							
							Image im = Utilities.mat2Image(frameCopy);
							Utilities.onFXThread(imageView.imageProperty(), im);
						}
					}
				};
				
				// terminate the timer if it is running 
				if (timer != null && !timer.isShutdown()) {
					timer.shutdown();
					timer.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
				}
				
				// run the frame grabber
				timer = Executors.newSingleThreadScheduledExecutor();
				timer.scheduleAtFixedRate(frameGrabber, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
				
			}
		} catch (InterruptedException e) {
			System.err.println("Something is wrong: " + e);;
		}		
	}
	
	@FXML
	protected void saveGIF(ActionEvent event) {
		try {
			if (capture != null && capture.isOpened()) { // the video capture must be open

				// select a file
				File recordsDir = new File(defaultDirectory);
			    FileChooser chooser = new FileChooser();
			    chooser.setTitle("Select Saving Location");
			    if (recordsDir != null && recordsDir.isDirectory()) {
				    chooser.setInitialDirectory(recordsDir);
			    }
			    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("GIF Image", "*.gif"));
			    chooser.setInitialFileName("*.gif");
			    File file = chooser.showSaveDialog(imageView.getScene().getWindow());
			    
			    if (file != null) {
					// terminate the timer if it is running 
					if (timer != null && !timer.isShutdown()) {
						timer.shutdown();
						timer.awaitTermination(1000, TimeUnit.MILLISECONDS);
					}
					
					double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
					double framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
					double percentageOfFramesToBeSkipped = rangeSlider.getLowValue() / rangeSlider.getMax();
					double numberOfFramesToBeSkipped = totalFrameCount * percentageOfFramesToBeSkipped;
					final double samplePerSecond = framePerSecond/2;
					final double sampleRate = framePerSecond / samplePerSecond - 1;
					int numberOfFramesToBeSampled = (int) Math.round((rangeSlider.getHighValue() - rangeSlider.getLowValue()) / rangeSlider.getMax() * totalFrameCount * samplePerSecond / framePerSecond);
					
					
					// skip all frames before the starting point
					capture.set(Videoio.CAP_PROP_POS_FRAMES, numberOfFramesToBeSkipped);
					
					// read that frame
					Mat[] frames = new Mat[numberOfFramesToBeSampled];
					for (int i = 0; i < numberOfFramesToBeSampled; i++) {
						frames[i] = new Mat();
						for (int j = 0; j < sampleRate; j++) {
							capture.grab();
						}
						if (!capture.read(frames[i])) { 
							System.err.println("Failed to read a new frame.");;
						}
					}
					
					// create copies for the frames
					Mat[] framesCopy = new Mat[frames.length];
					for (int i = 0; i < frames.length; i++) {
						framesCopy[i] = new Mat();
						frames[i].copyTo(framesCopy[i]);
					}
					
					// add text
					addTextToImages(framesCopy, new Point((double)framesCopy[0].cols()*3/4, (double)framesCopy[0].rows()*3/4), new Scalar(255, 255, 255), 3);
					
					// reduce resolution
					reduceImageResolution(framesCopy);
					
					// encode that frame
					GIFEncoder encoder = new GIFEncoder(framesCopy);
					byte[] data = encoder.encode((short) (1000/samplePerSecond));
					
					// output to a file
					FileOutputStream outputStream = new FileOutputStream(file.getAbsolutePath());
					outputStream.write(data);
					outputStream.close();
					
					createFrameGrabber();
			    }
				
			}
		} catch (IOException e) {
			System.err.println("Something is wrong: " + e);;
		} catch (InterruptedException e) {
			System.err.println("Something is wrong: " + e);;
		}
			
	}
	
	@FXML
	protected void applyText(ActionEvent event) {
		if (!textField.getText().equals("")) {
			text = new String(textField.getText());
		}
	}

	protected void addTextToImages(Mat[] images, Point position, Scalar color, int fontSize) {
		if (text != null) {
			for (int i = 0; i < images.length; i++) {
				Imgproc.putText(images[i], text, position, Core.FONT_HERSHEY_PLAIN, fontSize, color, fontSize);
			}
		}
	}
	
	protected void addTextToImage(Mat image, Point position, Scalar color, int fontSize) {
		if (text != null) {
			Imgproc.putText(image, text, position, Core.FONT_HERSHEY_PLAIN, fontSize, color, fontSize);
		}
	}
	
	protected void reduceImageResolution(Mat[] images) {
		Size reducedSize;
		if (fullRadioButton.isSelected()) {
			return;
		} else if (quarterRadioButton.isSelected()) {
			reducedSize = new Size(images[0].cols()/4, images[0].rows()/4);
		} else {
			reducedSize = new Size(images[0].cols()/2, images[0].rows()/2);
		}
		for (int i = 0; i < images.length; i++) {
			Imgproc.resize(images[i], images[i], reducedSize);
		}
	}
	
	protected void shutdownTimer() {
		try {
			if (timer != null && !timer.isShutdown()) {
				timer.shutdown();
				timer.awaitTermination(1000, TimeUnit.MILLISECONDS);
			}
		} catch (InterruptedException e) {
			System.err.println("Something is wrong: " + e);;
		}	
	}
}
