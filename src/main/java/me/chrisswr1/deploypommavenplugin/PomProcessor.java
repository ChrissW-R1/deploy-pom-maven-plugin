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
			if (!replace && PomProcessor.pathExists(doc, path)) {
				return doc;
			}
		} catch (final ParseException | XPathParseException e) {
			throw new IOException("Couldn't check path!", e);
		}

		return PomProcessor.addContent(doc, content, path);
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

	private static String getPathParent(final String path) {
		int lastSlash = path.lastIndexOf('/');
		return lastSlash > 0 ? path.substring(0, lastSlash) : path;
	}

	private static String getLocalElement(final String path) {
		int lastSlash = path.lastIndexOf('/');
		return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
	}

	private static VTDNav getNav(
		final byte[] doc
	) throws ParseException {
		VTDGen gen = new VTDGen();
		gen.setDoc(doc);
		gen.parse(true);
		return gen.getNav();
	}

	private static boolean pathExists(
		final byte[] doc,
		final String path
	) throws ParseException, XPathParseException {
		VTDNav    nav       = PomProcessor.getNav(doc);
		AutoPilot autoPilot = new AutoPilot(nav);

		autoPilot.selectXPath("boolean(" + path + ")");
		return autoPilot.evalXPathToBoolean();
	}

	private static String detectIndentUnit(
		final byte[] doc,
		final VTDNav nav
	) throws NavException {
		nav.push();

		try {
			if (nav.toElement(VTDNav.PARENT)) {
				String self  = PomProcessor.detectIndent(doc, nav, false);
				String child = PomProcessor.detectIndent(doc, nav, true);

				return child.startsWith(self) ?
					   child.substring(self.length()) :
					   "\t";
			}
		} finally {
			nav.pop();
		}

		return "\t";
	}

	private static String detectIndent(
		final byte[] doc,
		final VTDNav nav,
		boolean child
	) throws NavException {
		nav.push();

		try {
			if (child && !(nav.toElement(VTDNav.FIRST_CHILD))) {
				String self = PomProcessor.detectIndent(doc, nav, false);
				String unit = PomProcessor.detectIndentUnit(doc, nav);
				return self + unit;
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

	private static byte[] addContent(
		final byte[] doc,
		final String content,
		final String path
	) throws IOException {
		try {
			byte[] modifiedDoc = doc.clone();

			Deque<String[]> pathElements = new ArrayDeque<>();
			String          currentPath  = path;
			while (!(PomProcessor.pathExists(modifiedDoc, currentPath))) {
				String parentPath   = PomProcessor.getPathParent(currentPath);
				String localElement = PomProcessor.getLocalElement(currentPath);

				if (parentPath.equals(currentPath)) {
					throw new IOException("Root elements mismatch!");
				}

				pathElements.push(new String[]{parentPath, localElement});
				currentPath = parentPath;
			}

			for (String[] element : pathElements) {
				String parentPath   = element[0];
				String localElement = element[1];

				VTDNav    nav       = PomProcessor.getNav(modifiedDoc);
				AutoPilot autoPilot = new AutoPilot(nav);
				autoPilot.selectXPath(parentPath);
				XMLModifier modifier = new XMLModifier(nav);

				if (autoPilot.evalXPath() != -1) {
					String selfIndent = PomProcessor.detectIndent(
						modifiedDoc,
						nav,
						false
					);
					String childIndent = PomProcessor.detectIndent(
						modifiedDoc,
						nav,
						true
					);

					String formattedContent = PomProcessor.indentLines(
						"<" + localElement + ">\n" +
						"</" + localElement + ">",
						childIndent
					);

					modifier.insertBeforeTail(
						childIndent.substring(selfIndent.length()) +
						formattedContent +
						"\n" + selfIndent
					);

					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					modifier.output(baos);
					modifiedDoc = baos.toByteArray();
				}
			}

			VTDNav    nav       = PomProcessor.getNav(modifiedDoc);
			AutoPilot autoPilot = new AutoPilot(nav);
			autoPilot.selectXPath(path);

			XMLModifier modifier = new XMLModifier(nav);
			boolean     modified = false;
			if (autoPilot.evalXPath() != -1) {
				String indent = PomProcessor.detectIndent(
					modifiedDoc,
					nav,
					false
				);
				String formattedContent = PomProcessor.indentLines(
					content,
					indent
				);

				modifier.insertBeforeElement(formattedContent);
				modifier.remove();

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
}
