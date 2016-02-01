package bitverify.crypto;

import static org.junit.Assert.*;

import org.junit.Test;

public class HashTest {

	@Test
	public void testHashString() {
		String input[] = {
				"",
				"asd",
				"You shall not pass!",
				"that is funny. haha",
				"ok, not really",
				"Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.",
		};
		String output[] = {
				"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
				"688787d8ff144c502c7f5cffaafe2cc588d86079f9de88304c26b0cb99ce91c6",
				"deb865ddc0ba5cc7e38db5f01ea0670399931874ec47cca9310ec1bc2a50a1c6",
				"ea2c522db145aad48f21dac74dc301ba532714c87cba3d8daecab37aa97c232f",
				"a2913eda5ccdb31a683ba3df7294cca04e9a2819ad35e784ffc59c5235717bb5",
				"7321348c8894678447b54c888fdbc4e4b825bf4d1eb0cfb27874286a23ea9fd2",
		};
		for (int i=0; i<input.length; i++){
			assertEquals( output[i], Hash.hashString(input[i]) );	
		}
	}

}
