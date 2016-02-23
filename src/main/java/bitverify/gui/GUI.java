package bitverify.gui;

import bitverify.Node;
import bitverify.crypto.Hash;
import bitverify.crypto.KeyDecodingException;
import bitverify.entries.Entry;
import bitverify.persistence.DatabaseIterator;

import java.awt.GridLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class GUI extends Application {
	Node mNode;
	private Text setUpText;
	private Stage primaryStage;
	private Text numPeersText;
	private ObservableList<String> minerLog;
	private ObservableList<String> networkLog;
	DatabaseIterator<Entry> mIterator;
	long UPDATE_TIME = 10_000;
	int MAX_ENTRIES_AT_ONCE = 100;
	
	public static void StartGUI() {
		launch();
	}
	
	@Override
	public void start(Stage stage) throws Exception {
		AquaFx.style();
		
		// Set up loading screen
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
        
        //set up gui node to run in background
        (new Thread() {
        	public void run() {
        		mNode = new Node(GUI.this);
        	}
        }).start();
        //onNodeSetupComplete();
	}
	
	public void changeLoadingText(String newText) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				if (setUpText != null) {
					setUpText.setText(newText);
				}
			}
		});
	}
	
	public void onNodeSetupComplete() {
		// Can't run UI updates on a different thread
	    Platform.runLater(new Runnable() {
	        @Override
	        public void run() {
	        	// Set up main tab;
	        	HBox hbox = new HBox();
	        	hbox.setPadding(new Insets(15));
	        	
	        	TabPane tabs = new TabPane();
	        	tabs.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
	        	tabs.setSide(Side.TOP);
	        	tabs.setPadding(new Insets(15));
	        	
	        	Tab minerTab = getMinerTab();
	        	
	        	Tab addEntryTab = getAddEntryTab();
	        	
	        	Tab searchTab = getSearchTab();
	        	
	        	Tab networkTab = getNetworkTab();
	        	
	        	Tab settingsTab = new Tab();
	        	settingsTab.setText("Settings");
	        	
	        	tabs.getTabs().addAll(minerTab, addEntryTab, searchTab, networkTab, settingsTab);
	       
	        	hbox.getChildren().addAll(tabs);
	        	hbox.setAlignment(Pos.CENTER);
	        	
	        	HBox.setHgrow(tabs, Priority.ALWAYS);
	        	Scene scene = new Scene(hbox, 800, 600);
	        	Timer timer = new Timer();
	        	timer.schedule(new TimerTask() {

	                @Override
	                public void run() {
	                    updateNumPeers();
	                }
	            }, 0, UPDATE_TIME);
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
		
		HBox lowerBtnBox = new HBox();
		lowerBtnBox.setPadding(new Insets(15));
		lowerBtnBox.setSpacing(10);
		lowerBtnBox.setAlignment(Pos.BOTTOM_RIGHT);
		
		
		Button minerBtn = new Button("Start Miner");
		Button resetBtn = new Button("Reset Miner");
		Button clearBtn = new Button("Clear Log");        //testing out the log screen 
		
		// Start or stop the miner depending on the action
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
		
		clearBtn.setOnAction(new EventHandler<ActionEvent>(){
		    @Override public void handle(ActionEvent e) {
		        minerLog.clear();
		        
		    }
		});
		
		btnBox.getChildren().addAll(minerBtn, resetBtn);
		lowerBtnBox.getChildren().add(clearBtn);
		
		ListView<String> messageList = new ListView<String>();
		messageList.setPrefHeight(400);
		messageList.setMouseTransparent( false );
		messageList.setFocusTraversable( false );
		
		minerLog = FXCollections.observableArrayList(constructLogMessage("Miner ready."));
		messageList.setItems(minerLog);
		
		VBox vLay = new VBox();
		vLay.setPadding(new Insets(15));
		vLay.setSpacing(25);
		
		vLay.getChildren().addAll(btnBox, messageList,lowerBtnBox);
		
		minerTab.setContent(vLay);
		
		return minerTab;
	}
	
	private Tab getNetworkTab(){
	    
	       Tab networkTab = new Tab();
	       networkTab.setText("Network");
	        
	        HBox networkBox = new HBox();
	        networkBox.setPadding(new Insets(15));
	        networkBox.setSpacing(10);
	        networkBox.setAlignment(Pos.CENTER);
	        
	        numPeersText = new Text("Number of Peers: " + mNode.getNumPeers());
	        
	        networkBox.getChildren().add(numPeersText);
	        
	        ListView<String> networkView = new ListView<String>();
	        networkView.setPrefHeight(400);
	        networkView.setMouseTransparent( false );
	        networkView.setFocusTraversable( false );
	        
	        networkLog = FXCollections.observableArrayList(constructLogMessage("Network Running."));
	        networkView.setItems(networkLog);
	        
	        VBox vLay = new VBox();
	        vLay.setPadding(new Insets(15));
	        vLay.setSpacing(25);
	        
	        vLay.getChildren().addAll(networkBox,networkView);
	        
	        networkTab.setContent(vLay);
	        
	        return networkTab;
	    
	}
	
	private Tab getAddEntryTab() {
		Tab entryTab = new Tab();
		entryTab.setText("Add Entry");
		
		// Set some constants
		int TextFieldWidth = 200;
		int HSpacing = 5;
		
		// VLay will hold our form
		VBox vLay = new VBox();
    	vLay.setPadding(new Insets(15));
    	vLay.setSpacing(10);
		
    	// Title shown at the top of the form
		HBox hTitle = new HBox();
		Text titleText = new Text("Choose fields for document:");
		hTitle.getChildren().add(titleText);
		
		// Get the location of the document
		HBox docHBox = new HBox();
		docHBox.setSpacing(HSpacing);
		docHBox.setAlignment(Pos.TOP_LEFT);
		
		Label docLabel = new Label("File Path:");
		TextField docText = new TextField();
		docText.setPrefWidth(TextFieldWidth);
		
		Button chooseFileBtn = new Button("Choose File");
		chooseFileBtn.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
		    	FileChooser chooser = new FileChooser();
				chooser.setTitle("Select File");
				File selectedFile = chooser.showOpenDialog(primaryStage);
				// Update the docText field with the desired file
				docText.setText(selectedFile.getAbsolutePath());
		    }
		});
		
		docHBox.getChildren().addAll(docLabel, docText, chooseFileBtn);
		
		List<HBox> fields = new LinkedList<HBox>();
		
		// Get the name of the document
		HBox nameHBox = getFieldHBox("File Name:", HSpacing, TextFieldWidth);
		fields.add(nameHBox);
		
		// Get the download link of the document
		HBox downloadHBox = getFieldHBox("Download URL:", HSpacing, TextFieldWidth);
		fields.add(downloadHBox);

		// Get the description of the document
		HBox descriptionHBox = getFieldHBox("Description:", HSpacing, TextFieldWidth);
		fields.add(descriptionHBox);
		
		// Get the description of the document
		HBox recieverHBox = getFieldHBox("Reciever ID:", HSpacing, TextFieldWidth);
		fields.add(recieverHBox);
		
		// Get the description of the document
		HBox geoHBox = getFieldHBox("Geolocation:", HSpacing, TextFieldWidth);
		fields.add(geoHBox);
		
		// Get the description of the document
		HBox tagsHBox = getFieldHBox("Tags:", HSpacing, TextFieldWidth);
		fields.add(tagsHBox);
		
		// This will hold any errors in input
		Text errorText = new Text("");
		errorText.setFill(Color.RED);
		
		// Add buttons to submit the entry and clear the fields
		HBox submitHBox = new HBox();
		submitHBox.setSpacing(25);
		
		Button submitBtn = new Button("Submit Entry");
		Button clearBtn = new Button("Clear all fields");
		
		submitBtn.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
		    	File sourceFile = new File(docText.getText());
		    	
		    	// Try to hash the source file. Display message if failed
		    	byte [] hash = null;
				try {
					FileInputStream inputStream = new FileInputStream(sourceFile);
					hash = Hash.hashStream(inputStream);
				} catch (IOException e1) {
					errorText.setFill(Color.RED);
					errorText.setText(String.format("File '%s' does not exist", docText.getText()));
					return;
				}
				
				// Get the form fields
				String name = extractTextFromHBoxField(nameHBox);
				String download = extractTextFromHBoxField(downloadHBox);
				String description = extractTextFromHBoxField(descriptionHBox);
		    	String recieverID = extractTextFromHBoxField(recieverHBox);
		    	String geoLoc = extractTextFromHBoxField(geoHBox);
		    	String tags = extractTextFromHBoxField(tagsHBox);
		    	
		    	try {
					mNode.addEntry(hash, download, name, recieverID, description, geoLoc, tags);
					errorText.setFill(Color.GREEN);
					errorText.setText("Added file succesfully.");
				} catch (KeyDecodingException | IOException | SQLException ex) {
					errorText.setFill(Color.RED);
					errorText.setText("Error constructing entry. Check fields and try again.");
					ex.printStackTrace();
				}
		    	
		    }
		});
		
		clearBtn.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
		    	docText.setText("");
		    	// Just go through the fields and set the text in the box to ""
		    	for (HBox hbox : fields) {
		    		TextField field = (TextField) hbox.getChildren().get(1);
		    		field.setText("");
		    	}
		    }
		});
		
		submitHBox.getChildren().addAll(submitBtn, clearBtn);
		
				
		// Add all the fields in the form to the vertical layout
		vLay.getChildren().addAll(hTitle, docHBox);
		vLay.getChildren().addAll(fields);
		vLay.getChildren().addAll(submitHBox, errorText);
		
		// Make sure to add the vertical layout to the tab before returning
		entryTab.setContent(vLay);
		return entryTab;
	}
	
	private String extractTextFromHBoxField(HBox hbox) {
		TextField field = (TextField) hbox.getChildren().get(1);
		return field.getText();
	}
	
	private HBox getFieldHBox(String textLabel, int spacing, int textFieldWidth) {
		// Get the description of the document
		HBox hBox = new HBox();
		hBox.setSpacing(spacing);
		hBox.setAlignment(Pos.TOP_LEFT);
		
		Label label = new Label(textLabel);
		TextField text = new TextField();
		text.setPrefWidth(textFieldWidth);
		
		hBox.getChildren().addAll(label, text);
		HBox.setHgrow(hBox, Priority.ALWAYS);
		return hBox;
	}
	
	private Tab getSearchTab() {
    	Tab searchTab = new Tab();
    	searchTab.setText("Search Entries");
    	
    	VBox vLay = new VBox();
    	vLay.setPadding(new Insets(15));
    	vLay.setSpacing(25);
    	
    	// Create a table view to display the data
    	ObservableList<Entry> data = FXCollections.observableArrayList();
    	TableView<Entry> tableView = new TableView<Entry>();
    	
    	// Create all our columns
    	//TableColumn<Entry, String> timeStampColumn = getTableColumn("Time", "entryTimeStamp");
    	TableColumn<Entry, String> nameColumn = getTableColumn("Name", "docName");
    	//TableColumn<Entry, String> descriptionColumn = getTableColumn("Description", "docDescription");
    	//TableColumn<Entry, String> downloadColumn = getTableColumn("Link", "docLink");
    	//TableColumn<Entry, String> recieverColumn = getTableColumn("Receiver", "recieverID");
    	//TableColumn<Entry, String> uploaderColumn = getTableColumn("Uploader", "uploaderID");
    	//TableColumn<Entry, String> geoColumn = getTableColumn("Location", "docGeoLocation");
    	//TableColumn<Entry, String> hashColumn = getTableColumn("Hash", "docHash");
    	//TableColumn<Entry, String> tagsColumn = getTableColumn("Tags", "docTags");
    	
//    	tableView.getColumns().setAll(timeStampColumn, nameColumn, descriptionColumn, 
//    			downloadColumn, recieverColumn, uploaderColumn, geoColumn, tagsColumn);
    	
    	tableView.getColumns().setAll(nameColumn);
    	
    	tableView.setItems(data);
    	
    	HBox hLay = new HBox();
    	TextField searchField = new TextField();
    	searchField.setPrefWidth(600);
    	searchField.setPromptText("Search");
    	
    	Button searchButton = new Button("Search");
    	searchButton.setPrefHeight(searchButton.getHeight());
    	
    	searchButton.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
		    	try {
		    		// If we have a previous iterator, we must close it first
		    		if (mIterator != null) {
		    			mIterator.close();
		    		}
		    		
		    		// Get the iterator for the query
					mIterator = mNode.searchEntries(searchField.getText());
				
					for (int i = 0; i < MAX_ENTRIES_AT_ONCE; i++) {
						if (mIterator.moveNext()) {
							System.out.println(mIterator.current());
							//data.add(mIterator.current());
						} else {
							break;
						}
					}
				} catch (SQLException e1) {
					// TODO handle this by showing error message
					e1.printStackTrace();
				}
		    }
		});
    	
    	HBox.setHgrow(hLay, Priority.ALWAYS);
    	hLay.getChildren().addAll(searchField, searchButton);
    	hLay.setAlignment(Pos.TOP_CENTER);
    	
    	// Perhaps if we want to split this into pages
    	// Pagination pager = new Pagination();
    	
    	vLay.getChildren().addAll(hLay, tableView);
    	searchTab.setContent(vLay);
    	return searchTab;
	}
	
	private TableColumn<Entry, String> getTableColumn(String columnName, String entryName) {
		// Construct a table column
		TableColumn<Entry, String> column = new TableColumn<Entry, String>(columnName);
		column.setCellFactory(new PropertyValueFactory(entryName));
		return column;
	}
	
	private void updateNumPeers(){
	    String strNumPeers = "Number of Peers: " + mNode.getNumPeers();
	    Platform.runLater(new Runnable() {
            @Override
            public void run() {
                numPeersText.setText(strNumPeers);
            }
         });
	    
	}

	public String constructLogMessage(String message) {
		DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		Date date = new Date();
		return String.format("[%s] %s", dateFormat.format(date), message);
	}
	
}
