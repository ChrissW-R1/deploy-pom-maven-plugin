package me.chrisswr1.deploypommavenplugin;

import lombok.AllArgsConstructor;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
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
		final @NotNull MavenProject project
	) throws IOException, ModelBuildingException {
		if (file == null) {
			throw new IOException("File cannot be null!");
		}

		final @NotNull File directory = file.getParentFile();
		if (directory != null) {
			if (!(directory.mkdirs())) {
				throw new IOException(
					"Cannot create directory of output POM: " +
					directory.getAbsolutePath()
				);
			}
		}
		if (!(file.createNewFile())) {
			throw new IOException(
				"File of output POM already exists or could not be created: " +
				file.getAbsolutePath()
			);
		}

		final @NotNull MavenXpp3Writer pomWriter = new MavenXpp3Writer();
		try (final @NotNull FileOutputStream fos = new FileOutputStream(file)) {
			pomWriter.write(fos, model);
		}

		final @NotNull Properties projectProperties = project.getProperties();

		project.setModel(model);
		project.setPomFile(file);

		final @NotNull ModelBuildingRequest request =
			new DefaultModelBuildingRequest();
		request.setProcessPlugins(true);
		request.setPomFile(file);
		request.setValidationLevel(
			ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_3_1
		);

		projectProperties.putAll(project.getProperties());
		request.setSystemProperties(System.getProperties());
		request.setUserProperties(projectProperties);

		final @NotNull ModelBuilder modelBuilder =
			new DefaultModelBuilderFactory().newInstance();
		final @NotNull ModelBuildingResult result = modelBuilder.build(request);

		final @NotNull Model effectiveModel = result.getEffectiveModel();
		project.setModel(effectiveModel);
	}
}
