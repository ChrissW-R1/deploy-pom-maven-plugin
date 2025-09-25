package me.chrisswr1.deploypommavenplugin;

import com.ximpleware.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VTDTest {
	private static final Pattern LINE_SEPARATOR_PATTERN = Pattern.compile(
		"\r?\n|\r"
	);

	public static void main(
		final String[] args
	) throws IOException {
		byte[] output = Files.readAllBytes(Path.of("pom.xml"));
		output = VTDTest.addContent(
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
		output = VTDTest.addContent(
			output,
			"<url>https://example.com</url>\n<url2>https://example2.com</url2>",
			"/project/url",
			true
		);

		Files.write(Path.of("deploy.pom"), output);
	}

	public static byte[] addContent(
		final byte[] doc,
		final String content,
		final String path,
		final boolean replace
	) throws IOException {
		try {
			VTDGen gen = new VTDGen();
			gen.setDoc(doc);
			gen.parse(true);
			VTDNav    nav       = gen.getNav();
			AutoPilot autoPilot = new AutoPilot(nav);
			autoPilot.selectXPath(path);

			XMLModifier modifier = new XMLModifier(nav);
			boolean     modified = false;
			if (autoPilot.evalXPath() != -1) {
				String indent           = VTDTest.detectIndent(doc, nav);
				String formattedContent = VTDTest.indentLines(content, indent);

				if (replace) {
					modifier.insertBeforeElement(formattedContent);
					modifier.remove();
				} else {
					modifier.insertBeforeTail(
						indent + formattedContent + "\n"
					);
				}

				modified = true;
			}

			if (!modified) {
				return doc;
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			modifier.output(baos);
			return baos.toByteArray();
		} catch (
			final ParseException |
				  XPathParseException |
				  XPathEvalException |
				  NavException |
				  ModifyException |
				  TranscodeException e
		) {
			throw new IOException("Couldn't handle XML!", e);
		}
	}

	private static String detectIndent(
		final byte[] doc,
		final VTDNav nav
	) throws NavException {
		nav.push();

		try {
			if (!(nav.toElement(VTDNav.FIRST_CHILD))) {
				return "\t";
			}

			int startTok = nav.getCurrentIndex();
			int startOff = nav.getTokenOffset(startTok);

			int i = startOff - 1;
			while (i >= 0 && doc[i] != '\n' && doc[i] != '\r') {
				i--;
			}
			int wsStart = i + 1;

			StringBuilder sb = new StringBuilder();
			for (int k = wsStart; k < startOff; k++) {
				byte b = doc[k];
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

	private static String indentLines(
		final String content,
		final String indent
	) {
		StringBuilder builder = new StringBuilder();

		Matcher matcher = VTDTest.LINE_SEPARATOR_PATTERN.matcher(content);
		int     last    = 0;
		while (matcher.find()) {
			String line      = content.substring(last, matcher.start());
			String delimiter = matcher.group();

			builder.append(line);
			builder.append(delimiter);

			if (!(line.trim().isEmpty())) {
				builder.append(indent);
			}

			last = matcher.end();
		}
		builder.append(content.substring(last));

		return builder.toString();
	}
}
