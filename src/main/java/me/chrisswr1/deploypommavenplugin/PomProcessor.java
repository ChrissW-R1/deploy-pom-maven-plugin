package me.chrisswr1.deploypommavenplugin;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

@AllArgsConstructor
public class PomProcessor {
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

		project.setModel(model);
		//project.setPomFile(file);

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

		project.setModel(newProject.getModel());
	}
}
