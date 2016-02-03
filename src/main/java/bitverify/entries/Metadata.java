package bitverify.entries;

public class Metadata {

	private String docHash;
	private String linkToDownloadFile = ""; //e.g. magnet link
	
	private String docName = "";
	private String docDescription = "";
	private String docGeoLocation = "";
	private String docTimeStamp = "";
	private String[] tags = new String[0];
	
	public Metadata(String docHash, String linkToDownloadFile, String docName, String docDescription,
			String docGeoLocation, String docTimeStamp, String[] tags){
		this.docHash = docHash;
		this.linkToDownloadFile = linkToDownloadFile;
		this.docName = docName;
		this.docDescription = docDescription;
		this.docGeoLocation = docGeoLocation;
		this.docTimeStamp = docTimeStamp;
		this.tags = tags;
	}
	
	public String getDocHash(){
		return docHash;
	}
	
	public String getLinkToDownloadFile(){
		return linkToDownloadFile;
	}
	
	public String getDocName(){
		return docName;
	}
	
	public String getDocDescription(){
		return docDescription;
	}
	
	public String getDocGeoLocation(){
		return docGeoLocation;
	}
	
	public String getDocTimeStamp(){
		return docTimeStamp;
	}
	
	public String[] getTags(){
		return tags;
	}
	
}
