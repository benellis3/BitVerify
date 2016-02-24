package bitverify.gui;

import bitverify.LogEvent;
import bitverify.LogEventSource;
import bitverify.Node;
import bitverify.crypto.Hash;
import bitverify.crypto.KeyDecodingException;
import bitverify.entries.Entry;
import bitverify.persistence.DatabaseIterator;

import java.awt.GridLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.bouncycastle.util.encoders.Base64;

import com.squareup.otto.Bus;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
import javafx.stage.WindowEvent;
import javafx.util.Duration;

public class GUI extends Application {
	Node mNode;
	private Text setUpText;
	private Stage primaryStage;
	private Text numPeersText;
	private Text numEntryText;
	private Text numBlocksText;
	private ObservableList<String> minerLog;
	private ObservableList<String> networkLog;
	private DatabaseIterator<Entry> mIterator;
	private Bus mEventBus;
	
	long UPDATE_TIME = 5_000;
	
	int MAX_ENTRIES_AT_ONCE = 100;
	
	public static void StartGUI() {
		launch();
	}
	
	@Override
	public void start(Stage stage) throws Exception {
		
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
	        	// Let the gui listen to log events on the bus
	        	mEventBus = mNode.getEventBus();
	        	mEventBus.register(this);
	        	
	        	// Set up main tab;
	        	HBox hbox = new HBox();
	        	hbox.setPadding(new Insets(15));
	        	
	        	TabPane tabs = new TabPane();
	        	tabs.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
	        	tabs.setSide(Side.TOP);
	        	tabs.setPadding(new Insets(15));

	        	Tab minerTab = getMinerTab();
	            Image miningImage = new Image("/mining_icon.png");
	            ImageView miningImgView = new ImageView(miningImage);
	            miningImgView.setFitHeight(20);
	            miningImgView.setFitWidth(20);
	        	minerTab.setGraphic(miningImgView);
	        	
	        	Tab addEntryTab = getAddEntryTab();
	        	Image entryImage = new Image("/entry_icon.png");
	        	ImageView entryImgView = new ImageView(entryImage);
	        	entryImgView.setFitHeight(20);
	        	entryImgView.setFitWidth(20);
	        	addEntryTab.setGraphic(entryImgView);
	        	
	        	
	        	Tab searchTab = getSearchTab();
	        	Image searchImage = new Image("/search_icon.png");
	        	ImageView searchImgView = new ImageView(searchImage);
	        	searchImgView.setFitHeight(20);
	        	searchImgView.setFitWidth(20);
	        	searchTab.setGraphic(searchImgView);
	        	
	        	Tab networkTab = getNetworkTab();
	        	Image networkImage = new Image("/network_icon.png");
	        	ImageView networkImgView = new ImageView(networkImage);
	        	networkImgView.setFitHeight(20);
	        	networkImgView.setFitWidth(20);
	        	networkTab.setGraphic(networkImgView);
	        	
	        	Tab hashTab = getDocumentCheckTab();
	        	Image hashImage = new Image("/hash_icon.png");
	        	ImageView hashImgView = new ImageView(hashImage);
	        	hashImgView.setFitHeight(20);
	        	hashImgView.setFitWidth(20);
	        	hashTab.setGraphic(hashImgView);
	        	
	        	
	        	tabs.getTabs().addAll(minerTab, addEntryTab, searchTab, hashTab, networkTab);
	       
	        	hbox.getChildren().addAll(tabs);
	        	hbox.setAlignment(Pos.CENTER);
	        	
	        	HBox.setHgrow(tabs, Priority.ALWAYS);
	        	Scene scene = new Scene(hbox, 800, 600);
	        	Timer timer = new Timer();
	        	timer.schedule(new TimerTask() {

	                @Override
	                public void run() {
	                    updateNetworkStats();
	                }
	            }, 0, UPDATE_TIME);
	        	
	    		primaryStage.setScene(scene);
	    		
	    		// Close the node when we close the program
	    		primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
	    		    @Override
	    		    public void handle(WindowEvent event) {
	    		        if (mNode != null) {
	    		        	mNode.exitProgram();
	    		        	Platform.exit();
	    		        }
	    		    }
	    		});
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
	        
	        VBox networkBox = new VBox();
	        networkBox.setPadding(new Insets(15));
	        networkBox.setSpacing(10);
	        networkBox.setAlignment(Pos.CENTER);
	        
	        numPeersText = new Text("Number of Peers: " + mNode.getNumPeers());
	        numEntryText = new Text("Number of Entries: " + mNode.getEntryCount());
	        numBlocksText = new Text("Number of Blocks: " + mNode.getBlockCount());
	        
	        networkBox.getChildren().addAll(numBlocksText,numPeersText,numEntryText);
	        
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
		int VSpacing = 10;
		
		GridPane grid = new GridPane();
		grid.setHgap(5);
		grid.setVgap(VSpacing);
		
		// VLay will hold our form
		VBox vLay = new VBox();
    	vLay.setPadding(new Insets(15));
    	vLay.setSpacing(10);
		
    	// Title shown at the top of the form
		HBox hTitle = new HBox();
		Text titleText = new Text("Choose fields for document:");
		hTitle.getChildren().add(titleText);
		
		// Get the location of the document
		//HBox docHBox = new HBox();
		//docHBox.setSpacing(HSpacing);
		//docHBox.setAlignment(Pos.TOP_LEFT);
		
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
				if (selectedFile != null) {
					docText.setText(selectedFile.getAbsolutePath());
				}
		    }
		});
		
		grid.add(docLabel, 0, 0);
		grid.add(docText, 1, 0);
		grid.add(chooseFileBtn, 2, 0);
		
		//docHBox.getChildren().addAll(docLabel, docText, chooseFileBtn);
		
		List<TextField> fields = new LinkedList<TextField>();
		
		// Get the name of the document
		TextField nameText = addFieldToGrid(grid, 1, "File Name:", TextFieldWidth);
	
		// Get the download link of the document
		TextField downloadText = addFieldToGrid(grid, 2, "Download URL:", TextFieldWidth);

		// Get the description of the document
		TextField descriptionText = addFieldToGrid(grid, 3, "Description:", TextFieldWidth);
		
		// Get the description of the document
		TextField receiverText = addFieldToGrid(grid, 4, "Receiver ID:", TextFieldWidth);
		
		// Get the description of the document
		TextField geoText = addFieldToGrid(grid, 5, "Geolocation:", TextFieldWidth);
		
		// Get the description of the document
		TextField tagsText = addFieldToGrid(grid, 6, "Tags:", TextFieldWidth);
		
		Collections.addAll(fields, docText, nameText, downloadText, descriptionText, receiverText, geoText, tagsText);
		
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
					
					FadeTransition ft = new FadeTransition(Duration.millis(3000), errorText);
					ft.setFromValue(1.0);
				    ft.setToValue(0.0);
				    ft.play();
					
					return;
				}
				
				// Get the form fields
				String name = nameText.getText();
				String download = downloadText.getText();
				String description = descriptionText.getText();
		    	String recieverID = receiverText.getText();
		    	String geoLoc = geoText.getText();
		    	String tags = tagsText.getText();
		    	
		    	try {
					mNode.addEntry(hash, download, name, recieverID, description, geoLoc, tags);
					errorText.setFill(Color.GREEN);
					errorText.setText("Added file succesfully.");
					
					FadeTransition ft = new FadeTransition(Duration.millis(3000), errorText);
					ft.setFromValue(1.0);
				    ft.setToValue(0.0);
				    ft.play();
					
				} catch (KeyDecodingException | IOException | SQLException ex) {
					errorText.setFill(Color.RED);
					errorText.setText("Error constructing entry. Check fields and try again.");
					
					FadeTransition ft = new FadeTransition(Duration.millis(3000), errorText);
					ft.setFromValue(1.0);
				    ft.setToValue(0.0);
				    ft.play();
					
					ex.printStackTrace();
				}
		    	
		    }
		});
		
		clearBtn.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
		    	// Just go through the fields and set the text in the box to ""
		    	for (TextField field : fields) {
		    		field.setText("");
		    	}
		    }
		});
		
		submitHBox.getChildren().addAll(submitBtn, clearBtn);
		
		// Add all the fields in the form to the vertical layout
		vLay.getChildren().addAll(hTitle, grid);
		vLay.getChildren().addAll(submitHBox, errorText);
		
		// Make sure to add the vertical layout to the tab before returning
		entryTab.setContent(vLay);
		return entryTab;
	}
	
	private TextField addFieldToGrid(GridPane grid, int row, String textLabel, int textFieldWidth) {
		Label label = new Label(textLabel);
		TextField text = new TextField();
		text.setPrefWidth(textFieldWidth);
		grid.add(label, 0, row);
		grid.add(text, 1, row);
		return text;
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
    	TableColumn<Entry, String> timeStampColumn = getTableColumn("Time", "entryTimeStamp");
    	TableColumn<Entry, String> nameColumn = getTableColumn("Name", "docName");
    	TableColumn<Entry, String> descriptionColumn = getTableColumn("Description", "docDescription");
    	TableColumn<Entry, String> downloadColumn = getTableColumn("Link", "docLink");
    	
    	// These columns are byte arrays, so we have to do them a little differently
    	TableColumn<Entry, String> receiverColumn = new TableColumn<Entry, String>();
    	receiverColumn.setText("Receiver");
    	receiverColumn.setPrefWidth(75);
    	receiverColumn.setCellValueFactory(
    		      cellData -> new ReadOnlyStringWrapper(Base64.toBase64String((cellData.getValue().getReceiverID()))));
    	
    	TableColumn<Entry, String> uploaderColumn = new TableColumn<Entry, String>();
    	uploaderColumn.setText("Uploader");
    	uploaderColumn.setPrefWidth(75);
    	uploaderColumn.setCellValueFactory(
    		      cellData -> new ReadOnlyStringWrapper(Base64.toBase64String((cellData.getValue().getUploaderID()))));
    	
    	TableColumn<Entry, String> hashColumn = new TableColumn<Entry, String>();
    	hashColumn.setText("Hash");
    	hashColumn.setPrefWidth(75);
    	hashColumn.setCellValueFactory(
    		      cellData -> new ReadOnlyStringWrapper(Base64.toBase64String((cellData.getValue().getDocHash()))));
   
    	// Back to normal columns now
    	TableColumn<Entry, String> geoColumn = getTableColumn("Location", "docGeoLocation");
    	TableColumn<Entry, String> tagsColumn = getTableColumn("Tags", "docTags");
    	
    	// Order of columns
    	tableView.getColumns().setAll(timeStampColumn, nameColumn, descriptionColumn, 
    			downloadColumn, receiverColumn, uploaderColumn, geoColumn, hashColumn, tagsColumn);
    	
    	//tableView.getColumns().setAll(nameColumn);
    	
    	tableView.setItems(data);
    	
    	HBox hLay = new HBox();
    	TextField searchField = new TextField();
    	searchField.setPrefWidth(600);
    	searchField.setPromptText("Search");
    	
    	Button searchButton = new Button("Search");
    	searchButton.setPrefHeight(searchButton.getHeight());
    	
    	searchButton.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
		    	data.clear();
		    	try {
		    		// If we have a previous iterator, we must close it first
		    		if (mIterator != null) {
		    			mIterator.close();
		    		}
		    		
		    		// Get the iterator for the query
					mIterator = mNode.searchEntries(searchField.getText());
				
					for (int i = 0; i < MAX_ENTRIES_AT_ONCE; i++) {
						if (mIterator.moveNext()) {
							Entry entry = mIterator.current();
							data.add(entry);
						} else {
							mIterator.close();
							break;
						}
					}
				} catch (SQLException e1) {
					// TODO handle this by showing error message
					e1.printStackTrace();
				}
		    }
		});
    	
    	searchButton.fire();
    	
    	searchField.setOnKeyPressed(new EventHandler<KeyEvent>()
        {
            @Override
            public void handle(KeyEvent ke)
            {
                if (ke.getCode().equals(KeyCode.ENTER))
                {
                    searchButton.fire();
                }
            }
        });
    	
    	HBox.setHgrow(hLay, Priority.ALWAYS);
    	hLay.getChildren().addAll(searchField, searchButton);
    	hLay.setAlignment(Pos.TOP_CENTER);
    	
    	HBox bottomH = new HBox();
    	Button loadButton = new Button("Load more entries");
    	loadButton.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
		    	if (mIterator != null) {
		    		for (int i = 0; i < MAX_ENTRIES_AT_ONCE; i++) {
		    			try {
							if (mIterator.moveNext()) {
								Entry entry = mIterator.current();
								data.add(entry);
							} else {
								mIterator.close();
								break;
							}
						} catch (SQLException e1) {
							// Hopefully don't care
							
						}
		    		}
		    	}
		    }
    	});
    	
    	bottomH.getChildren().add(loadButton);
    	bottomH.setAlignment(Pos.CENTER);
    	
    	vLay.getChildren().addAll(hLay, tableView, bottomH);
    	searchTab.setContent(vLay);
    	return searchTab;
	}
	
	private Tab getDocumentCheckTab() {
		Tab documentTab = new Tab();
		documentTab.setText("Check Hash");
		
		VBox vLay = new VBox();
		vLay.setSpacing(25);
		vLay.setPadding(new Insets(15));
		
		HBox hashHBox = new HBox();
		hashHBox.setAlignment(Pos.CENTER_LEFT);
		Text hashText = new Text("Hash: ");
		hashHBox.getChildren().add(hashText);
		
		// Get the location of the document
		HBox docHBox = new HBox();
		docHBox.setSpacing(10);
		docHBox.setAlignment(Pos.TOP_LEFT);
		
		Label docLabel = new Label("File Path:");
		TextField docText = new TextField();
		docText.setPrefWidth(400);
		
		Button chooseFileBtn = new Button("Choose File");
		chooseFileBtn.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
		    	FileChooser chooser = new FileChooser();
				chooser.setTitle("Select File");
				File selectedFile = chooser.showOpenDialog(primaryStage);
				// Update the docText field with the desired file
				if (selectedFile != null) {
					docText.setText(selectedFile.getAbsolutePath());
					updateHashText(selectedFile, hashText);
				}
				
		    }
		});
		
    	docText.setOnKeyPressed(new EventHandler<KeyEvent>()
        {
            @Override
            public void handle(KeyEvent ke)
            {
                if (ke.getCode().equals(KeyCode.ENTER))
                {
                    File file = new File(docText.getText());
                    updateHashText(file, hashText);
                }
            }
        });
		
		docHBox.getChildren().addAll(docLabel, docText, chooseFileBtn);
		vLay.getChildren().addAll(docHBox, hashHBox);
		documentTab.setContent(vLay);
		return documentTab;
	}
	
	private void updateHashText(File selectedFile, Text hashText) {
		try {
			FileInputStream inputStream = new FileInputStream(selectedFile);
			byte [] hash = Hash.hashStream(inputStream);
			hashText.setText("Hash: " + Base64.toBase64String(hash));
			inputStream.close();
		} catch (IOException e1) {
			hashText.setText(String.format("File '%s' does not exist", selectedFile.getAbsolutePath()));
		}
	}
	
	private TableColumn<Entry, String> getTableColumn(String columnName, String entryName) {
		// Construct a table column
		TableColumn<Entry, String> column = new TableColumn<Entry, String>(columnName);
		column.setCellValueFactory(new PropertyValueFactory<Entry, String>(entryName));
		return column;
	}
	
	private void updateNetworkStats(){
	    String strNumPeers = "Number of Peers: " + mNode.getNumPeers();
        String strNumEntry = "Number of Entries: " + mNode.getEntryCount();
        String strNumBlock = "Number of Blocks: " + mNode.getBlockCount();
	    Platform.runLater(new Runnable() {
            @Override
            public void run() {
                numPeersText.setText(strNumPeers);
                numEntryText.setText(strNumEntry);
                numBlocksText.setText(strNumBlock);
            }
         });
	    
	}
	
	public void addLogEvent(LogEvent o) {
	    Platform.runLater(new Runnable() {
            @Override
            public void run() {
        	    LogEventSource source = o.getSource();
        	    switch(source){
        	        case MINING:
        	            minerLog.add(constructLogMessage(o.getMessage()));
        	            break;
        	        case NETWORK:
        	            networkLog.add(constructLogMessage(o.getMessage()));
        	            break;
        	    }
            }
         });
	}

	public String constructLogMessage(String message) {
		DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		Date date = new Date();
		return String.format("[%s] %s", dateFormat.format(date), message);
	}
	
}
