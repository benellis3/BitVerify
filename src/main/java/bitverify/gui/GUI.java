package bitverify.gui;

import bitverify.Node;

import java.awt.GridLayout;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.aquafx_project.AquaFx;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

public class GUI extends Application {
	Node mNode;
	private Text setUpText;
	private Stage primaryStage;
	private ObservableList<String> minerLog;
	
	public static void StartGUI() {
		launch();
	}
	
	@Override
	public void start(Stage stage) throws Exception {
		AquaFx.style();
		
		primaryStage = stage;
        primaryStage.setTitle("BitVerify");
        
        BorderPane border = new BorderPane();

        VBox topVb = new VBox();
       
        Text scenetitle = new Text("BitVerify");
        scenetitle.setFont(Font.font("Tahoma", FontWeight.NORMAL, 80));
        scenetitle.setTextAlignment(TextAlignment.CENTER);
        
        Image image = new Image("/ic_launcher.png");
        ImageView imgView = new ImageView();
        imgView.setImage(image);
        
        topVb.getChildren().addAll(scenetitle, imgView);
        topVb.setAlignment(Pos.CENTER);
        topVb.setSpacing(35);
        topVb.setPadding(new Insets(25));
        border.setTop(topVb);
        
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));

        VBox textVb = new VBox();
        setUpText = new Text("Setting up BitVerify...");
        setUpText.setFont(Font.font("Tahoma", FontWeight.NORMAL, 15));
        setUpText.setTextAlignment(TextAlignment.CENTER);
        textVb.getChildren().add(setUpText);
        textVb.setAlignment(Pos.CENTER);
        grid.add(textVb, 0, 1);
        
        VBox progressVb = new VBox();
        ProgressBar progressBar = new ProgressBar(-1.0);
        progressVb.getChildren().add(progressBar);
        progressVb.setAlignment(Pos.CENTER);
        
        grid.add(progressVb, 0, 2);
        
        border.setCenter(grid);
        
        Scene scene = new Scene(border, 800, 600);
        primaryStage.setScene(scene);
        
        primaryStage.show();
        
        (new Thread() {
        	public void run() {
        		mNode = new Node(GUI.this);
        	}
        }).start();
        //onNodeSetupComplete();
	}
	
	public void changeLoadingText(String newText) {
		if (setUpText != null) {
			setUpText.setText(newText);
		}
	}
	
	public void onNodeSetupComplete() {
	    Platform.runLater(new Runnable() {
	        @Override
	        public void run() {
	        	HBox hbox = new HBox();
	        	hbox.setPadding(new Insets(15));
	        	
	        	TabPane tabs = new TabPane();
	        	tabs.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
	        	tabs.setSide(Side.TOP);
	        	tabs.setPadding(new Insets(15));
	        	
	        	Tab minerTab = getMinerTab();
	        	
	        	Tab addEntryTab = new Tab();
	        	addEntryTab.setText("Add Entry");
	        	
	        	Tab searchTab = new Tab();
	        	searchTab.setText("Search Entries");
	        	
	        	Tab networkTab = new Tab();
	        	networkTab.setText("Network");
	        	
	        	Tab settingsTab = new Tab();
	        	settingsTab.setText("Settings");
	        	
	        	tabs.getTabs().addAll(minerTab, addEntryTab, searchTab, networkTab, settingsTab);
	       
	        	hbox.getChildren().addAll(tabs);
	        	hbox.setAlignment(Pos.CENTER);
	        	
	        	HBox.setHgrow(tabs, Priority.ALWAYS);
	        	Scene scene = new Scene(hbox, 800, 600);
	        	
	    		primaryStage.setScene(scene);
	        }
	     });
	}
	
	private Tab getMinerTab() {
		Tab minerTab = new Tab();
		minerTab.setText("Miner");
		
		HBox btnBox = new HBox();
		btnBox.setPadding(new Insets(15));
		btnBox.setSpacing(10);
		btnBox.setAlignment(Pos.CENTER);
		
		Button minerBtn = new Button("Start Miner");
		Button resetBtn = new Button("Reset Miner");
		
		minerBtn.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
		        String text = minerBtn.getText();
		        if (text.equalsIgnoreCase("start miner")) {
		        	minerLog.add(constructLogMessage("Starting miner..."));
		        	mNode.startMiner();
		        	minerBtn.setText("Stop Miner");
		        } else if (text.equalsIgnoreCase("stop miner")) {
		        	minerLog.add(constructLogMessage("Stopping miner..."));
		        	mNode.stopMiner();
		        	minerBtn.setText("Start Miner");
		        }
		    }
		});
		
		resetBtn.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
	        	minerLog.add(constructLogMessage("Stopping miner..."));
	        	mNode.stopMiner();
		    	
	        	minerLog.add(constructLogMessage("Starting miner..."));
	        	mNode.startMiner();
	        	minerBtn.setText("Stop Miner");
		    }
		});
		
		btnBox.getChildren().addAll(minerBtn, resetBtn);
		
		ListView<String> messageList = new ListView<String>();
		messageList.setPrefHeight(400);
		messageList.setMouseTransparent( true );
		messageList.setFocusTraversable( false );
		
		minerLog =FXCollections.observableArrayList(constructLogMessage("Miner ready."));
		messageList.setItems(minerLog);
		
		VBox vLay = new VBox();
		vLay.setPadding(new Insets(15));
		vLay.setSpacing(25);
		
		vLay.getChildren().addAll(btnBox, messageList);
		
		minerTab.setContent(vLay);
		
		return minerTab;
	}

	public String constructLogMessage(String message) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		return String.format("%s - %s", dateFormat.format(date), message);
	}
	
}
