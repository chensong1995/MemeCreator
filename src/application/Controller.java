package application;

import java.net.MalformedURLException;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class Controller {
	@FXML
	private ImageView imageView;
	
	@FXML
	protected void openVideo(ActionEvent event) throws MalformedURLException {
		Image im = new Image("file:G:\\Documents\\Simon Fraser University\\3rd Year\\CMPT 365\\Term Project\\resources\\default.png");
		imageView.setImage(im);
	}
}
