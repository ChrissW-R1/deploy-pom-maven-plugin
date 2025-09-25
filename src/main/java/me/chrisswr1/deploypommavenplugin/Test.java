package me.chrisswr1.deploypommavenplugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Test {
	public static void main(String[] args) throws IOException {
		byte[] output = Files.readAllBytes(Path.of("pom.xml"));
		output = PomProcessor.addContent(
			output,
			"<developers>\n" +
			"\t<developer>\n" +
			"\t\t<id>ChrissW-R1</id>\n" +
			"\t\t<name>ChrissW-R1</name>\n" +
			"\t\t<email>contact@ChrissW-R1.me</email>\n" +
			"\t</developer>\n" +
			"</developers>",
			"/project",
			false
		);
		output = PomProcessor.addContent(
			output,
			"<url>https://example.com</url>\n<url2>https://example2.com</url2>",
			"/project/url",
			true
		);

		Files.write(Path.of("deploy.pom"), output);
	}
}
