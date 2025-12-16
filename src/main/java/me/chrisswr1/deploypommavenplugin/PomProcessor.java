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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AllArgsConstructor
public class PomProcessor {
	private static final @NotNull Pattern LINE_SEPARATOR_PATTERN =
		Pattern.compile(
			"\r?\n|\r"
		);

	public static byte[] addContent(
		final byte[] doc,
		final @Nullable String content,
		final @Nullable String path,
		final boolean replace
	) throws IOException {
		if (content == null || content.trim().isEmpty()) {
			return doc;
		}

		final @NotNull String normalizedPath = (path != null) ? path : "/";

		try {
			if (!replace && PomProcessor.pathExists(doc, normalizedPath)) {
				return doc;
			}
		} catch (
			final @NotNull
			ParseException |
			XPathParseException e
		) {
			throw new IOException("Couldn't check path!", e);
		}

		return PomProcessor.addContent(doc, content, normalizedPath);
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
		} catch (
			final @NotNull
			XmlPullParserException e
		) {
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
		final @Nullable MavenProject newProject = result.getProject();

		for (
			final @Nullable String key : projectProperties.stringPropertyNames()
		) {
			if (!(newProject.getProperties().containsKey(key))) {
				newProject.getProperties().setProperty(
					key,
					projectProperties.getProperty(key)
				);
			}
		}

		project.setOriginalModel(newProject.getModel());
	}

	private static @NotNull String getPathParent(
		final @NotNull String path
	) {
		final int lastSlash = path.lastIndexOf('/');
		return lastSlash > 0 ? path.substring(0, lastSlash) : path;
	}

	private static @NotNull String getLocalElement(
		final @NotNull String path
	) {
		final int lastSlash = path.lastIndexOf('/');
		return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
	}

	private static @NotNull VTDNav getNav(
		final byte[] doc
	) throws ParseException {
		final @NotNull VTDGen gen = new VTDGen();
		gen.setDoc(doc);
		gen.parse(true);
		return gen.getNav();
	}

	private static boolean pathExists(
		final byte[] doc,
		final @NotNull String path
	) throws ParseException, XPathParseException {
		final @NotNull VTDNav    nav       = PomProcessor.getNav(doc);
		final @NotNull AutoPilot autoPilot = new AutoPilot(nav);

		autoPilot.selectXPath("boolean(" + path + ")");
		return autoPilot.evalXPathToBoolean();
	}

	private static @NotNull String detectIndentUnit(
		final byte[] doc,
		final @NotNull VTDNav nav
	) throws NavException {
		nav.push();

		try {
			if (nav.toElement(VTDNav.PARENT)) {
				final @NotNull String self = PomProcessor.detectIndent(
					doc,
					nav,
					false
				);
				final @NotNull String child = PomProcessor.detectIndent(
					doc,
					nav,
					true
				);

				return child.startsWith(self) ?
					   child.substring(self.length()) :
					   "\t";
			}
		} finally {
			nav.pop();
		}

		return "\t";
	}

	private static @NotNull String detectIndent(
		final byte[] doc,
		final @NotNull VTDNav nav,
		boolean child
	) throws NavException {
		nav.push();

		try {
			if (child && !(nav.toElement(VTDNav.FIRST_CHILD))) {
				final @NotNull String self = PomProcessor.detectIndent(
					doc,
					nav,
					false
				);
				final @NotNull String unit = PomProcessor.detectIndentUnit(
					doc,
					nav
				);
				return self + unit;
			}

			final int startTok = nav.getCurrentIndex();
			final int startOff = nav.getTokenOffset(startTok);

			int i = startOff - 1;
			while (i >= 0 && doc[i] != '\n' && doc[i] != '\r') {
				i--;
			}
			final int wsStart = i + 1;

			final @NotNull StringBuilder sb = new StringBuilder();
			for (int k = wsStart; k < startOff; k++) {
				final byte b = doc[k];
				if (b == ' ' || b == '\t') {
					sb.append((char)b);
				} else {
					break;
				}
			}

			return sb.toString();
		} finally {
			nav.pop();
		}
	}

	private static @NotNull String indentLines(
		final @Nullable String content,
		final @Nullable String indent
	) {
		if (content == null) {
			return "";
		}
		if (
			content.trim().isEmpty() ||
			indent == null ||
			indent.isEmpty()
		) {
			return content;
		}

		final @NotNull StringBuilder builder = new StringBuilder();

		final @NotNull Matcher matcher =
			PomProcessor.LINE_SEPARATOR_PATTERN.matcher(content);
		int last = 0;
		while (matcher.find()) {
			final @NotNull String line = content.substring(
				last,
				matcher.start()
			);
			final @NotNull String delimiter = matcher.group();

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

	private static byte[] addContent(
		final byte[] doc,
		final @Nullable String content,
		final @NotNull String path
	) throws IOException {
		if (content == null || content.trim().isEmpty()) {
			return doc;
		}

		try {
			byte[] modifiedDoc = doc.clone();

			final @NotNull Deque<String[]> pathElements = new ArrayDeque<>();
			@NotNull String                currentPath  = path;
			while (!(PomProcessor.pathExists(modifiedDoc, currentPath))) {
				final @NotNull String parentPath = PomProcessor.getPathParent(
					currentPath
				);
				final @NotNull String localElement =
					PomProcessor.getLocalElement(currentPath);

				if (parentPath.equals(currentPath)) {
					throw new IOException("Root elements mismatch!");
				}

				pathElements.push(new String[]{parentPath, localElement});
				currentPath = parentPath;
			}

			for (final @NotNull String[] element : pathElements) {
				final @NotNull String parentPath   = element[0];
				final @NotNull String localElement = element[1];

				final @NotNull VTDNav nav = PomProcessor.getNav(modifiedDoc);
				final @NotNull AutoPilot autoPilot = new AutoPilot(nav);
				autoPilot.selectXPath(parentPath);
				final @NotNull XMLModifier modifier = new XMLModifier(nav);

				if (autoPilot.evalXPath() != -1) {
					final @NotNull String selfIndent =
						PomProcessor.detectIndent(modifiedDoc, nav, false);
					final @NotNull String childIndent =
						PomProcessor.detectIndent(modifiedDoc,nav,true);

					final @NotNull String formattedContent =
						PomProcessor.indentLines(
							"<" + localElement + ">\n" +
							"</" + localElement + ">",
							childIndent
						);

					modifier.insertBeforeTail(
						childIndent.substring(selfIndent.length()) +
						formattedContent +
						"\n" + selfIndent
					);

					final @NotNull ByteArrayOutputStream baos =
						new ByteArrayOutputStream();
					modifier.output(baos);
					modifiedDoc = baos.toByteArray();
				}
			}

			final @NotNull VTDNav    nav       = PomProcessor.getNav(
				modifiedDoc
			);
			final @NotNull AutoPilot autoPilot = new AutoPilot(nav);
			autoPilot.selectXPath(path);

			final @NotNull XMLModifier modifier = new XMLModifier(nav);
			boolean                    modified = false;
			if (autoPilot.evalXPath() != -1) {
				final @NotNull String indent = PomProcessor.detectIndent(
					modifiedDoc,
					nav,
					false
				);
				final @NotNull String formattedContent =
					PomProcessor.indentLines(content, indent);

				modifier.insertBeforeElement(formattedContent);
				modifier.remove();

				modified = true;
			}

			if (!modified) {
				return doc;
			}

			final @NotNull ByteArrayOutputStream baos =
				new ByteArrayOutputStream();
			modifier.output(baos);
			return baos.toByteArray();
		} catch (
			final @NotNull
			ParseException |
			XPathParseException |
			XPathEvalException |
			NavException |
			ModifyException |
			TranscodeException e
		) {
			throw new IOException("Couldn't handle XML!", e);
		}
	}
}
