package me.chrisswr1.deploypommavenplugin;

import com.ximpleware.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VTDTest {
	public static void main(
		final String[] args
	) throws IOException {
		byte[] output = Files.readAllBytes(Path.of("pom.xml"));
		output = VTDTest.appendContent(
			output,
			"<developers><developer><id>ChrissW-R1</id><name>ChrissW-R1</name><email>contact@ChrissW-R1.me</email></developer></developers>",
			"/project"
		);

		Files.write(Path.of("deploy.pom"), output);
	}

	public static byte[] appendContent(
		final byte[] xml,
		final String content,
		final String path
	) throws IOException {
		try {
			VTDGen gen = new VTDGen();
			gen.setDoc(xml);
			gen.parse(true);
			VTDNav    nav       = gen.getNav();
			AutoPilot autoPilot = new AutoPilot(nav);
			autoPilot.selectXPath(path);

			XMLModifier modifier = new XMLModifier(nav);
			if (autoPilot.evalXPath() != -1) {
				String childIndent = VTDTest.detectChildIndent(xml, nav);
				modifier.insertBeforeTail(childIndent + content + "\n");
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			modifier.output(baos);
			return baos.toByteArray();
		} catch (
			final ParseException |
				  XPathParseException |
				  ModifyException |
				  XPathEvalException |
				  NavException |
				  TranscodeException e
		) {
			throw new IOException("Couldn't handle XML!", e);
		}
	}

	private static String detectChildIndent(
		final byte[] xml,
		final VTDNav nav
	) throws NavException {
		nav.push();

		try {
			if (!(nav.toElement(VTDNav.FIRST_CHILD))) {
				return "\t";
			}

			int childStartTok = nav.getCurrentIndex();
			int childStartOff = nav.getTokenOffset(childStartTok);

			int i = childStartOff - 1;
			while (i >= 0 && xml[i] != '\n' && xml[i] != '\r') {
				i--;
			}
			int wsStart = ++i;

			StringBuilder sb = new StringBuilder();
			for (int k = wsStart; k < childStartOff; k++) {
				byte b = xml[k];
				if (b == ' ' || b == '\t') {
					sb.append((char) b);
				} else {
					break;
				}
			}
			String indent = sb.toString();

			return indent.isEmpty() ? "\t" : indent;
		} finally {
			nav.pop();
		}
	}
}
