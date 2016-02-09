package bitverify.entries;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
	
	public static Metadata deserialize(InputStream in) throws IOException {
		// DataInputStream allows us to read in primitives in binary form.
		try (DataInputStream d = new DataInputStream(in)) {
			String docHash = d.readUTF();
			String linkToDownloadFile = d.readUTF();
			String docName = d.readUTF();
			String docDescription = d.readUTF();
			String docGeoLocation = d.readUTF();
			String docTimeStamp = d.readUTF();
			
			int numTags = d.readInt();
			String[] tags = new String[numTags];
			for (int i=0; i<numTags; i++){
				tags[i] = d.readUTF();
			}

			return new Metadata(docHash, linkToDownloadFile, docName, docDescription,
					docGeoLocation, docTimeStamp, tags);
		}
	}
	
	public static Metadata deserialize(byte[] data) throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		return Metadata.deserialize(in);
	}
	
	public void serialize(OutputStream out) throws IOException {
		// DataOutputStream allows us to write primitives in binary form.
		try (DataOutputStream d = new DataOutputStream(out)) {
			// write out each field in binary form, in declaration order.
			d.writeUTF(docHash);
			d.writeUTF(linkToDownloadFile);
			d.writeUTF(docName);
			d.writeUTF(docDescription);
			d.writeUTF(docGeoLocation);
			d.writeUTF(docTimeStamp);
			
			d.writeInt(tags.length);
			for (String tag : tags) {
				d.writeUTF(tag);
			}

			d.flush();
		}
	}
	
	public byte[] serialize() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		serialize(out);
		return out.toByteArray();
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
