package me.chrisswr1.deploypommavenplugin;

import com.ximpleware.*;
import lombok.AllArgsConstructor;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.*;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AllArgsConstructor
public class PomProcessor {
	private static final Pattern LINE_SEPARATOR_PATTERN = Pattern.compile(
		"\r?\n|\r"
	);

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
				String indent = PomProcessor.detectIndent(doc, nav);
				String formattedContent = PomProcessor.indentLines(
					content,
					indent
				);

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

	public static @NotNull Model getModel(
		final @Nullable File file
	) throws IOException {
		if (file == null) {
			throw new IOException("File cannot be null!");
		}

		final @NotNull MavenXpp3Reader pomReader = new MavenXpp3Reader();
		final @Nullable Model          model;

		try (final @NotNull FileInputStream fis = new FileInputStream(file)) {
			model = pomReader.read(fis);
		} catch (final @NotNull XmlPullParserException e) {
			throw new IOException("Couldn't parse model from POM!", e);
		}

		if (model == null) {
			throw new IOException("Read model is null!");
		}

		return model;
	}

	public static void setModel(
		final @Nullable File file,
		final @NotNull Model model,
		final @Nullable MavenSession session,
		final @NotNull ProjectBuilder projectBuilder
	) throws IOException, ProjectBuildingException, IllegalArgumentException {
		if (file == null) {
			throw new IllegalArgumentException("File cannot be null!");
		}
		if (session == null) {
			throw new IllegalArgumentException(
				"Maven session is not available!"
			);
		}
		final @Nullable MavenProject project = session.getCurrentProject();
		if (project == null) {
			throw new IllegalArgumentException(
				"Maven project is not available!"
			);
		}

		final @NotNull File directory = file.getParentFile();
		if (directory != null && (!(directory.exists()))) {
			if (!(directory.mkdirs())) {
				throw new IOException(
					"Cannot create directory of output POM: " +
					directory.getAbsolutePath()
				);
			}
		}
		if ((!(file.exists())) && (!(file.createNewFile()))) {
			throw new IOException(
				"File of output POM could not be created: " +
				file.getAbsolutePath()
			);
		}

		final @NotNull MavenXpp3Writer pomWriter = new MavenXpp3Writer();
		try (final @NotNull FileOutputStream fos = new FileOutputStream(file)) {
			pomWriter.write(fos, model);
		}

		final @NotNull Properties projectProperties = project.getProperties();

		project.setOriginalModel(model);
		project.setPomFile(file);

		final @NotNull ProjectBuildingRequest request =
			session.getProjectBuildingRequest();
		request.setProcessPlugins(true);
		request.setResolveDependencies(true);
		request.setValidationLevel(
			ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_1
		);

		final @NotNull Properties userProperties = request.getUserProperties();
		userProperties.putAll(session.getUserProperties());
		request.setUserProperties(userProperties);
		session.getUserProperties().putAll(userProperties);

		final @NotNull ProjectBuildingResult result = projectBuilder.build(
			file,
			request
		);
		MavenProject newProject = result.getProject();

		for (String key : projectProperties.stringPropertyNames()) {
			if (!(newProject.getProperties().containsKey(key))) {
				newProject.getProperties().setProperty(
					key,
					projectProperties.getProperty(key)
				);
			}
		}

		project.setOriginalModel(newProject.getModel());
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

		Matcher matcher = PomProcessor.LINE_SEPARATOR_PATTERN.matcher(content);
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
