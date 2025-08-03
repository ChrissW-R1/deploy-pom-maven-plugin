package me.chrisswr1.deploypommavenplugin;

import lombok.AllArgsConstructor;
import org.apache.maven.model.Model;
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

		try {
			model = pomReader.read(new FileInputStream(file));
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
	) throws IOException {
		if (file == null) {
			throw new IOException("File cannot be null!");
		}

		final @NotNull MavenXpp3Writer pomWriter = new MavenXpp3Writer();
		pomWriter.write(new FileOutputStream(file), model);

		project.setPomFile(file);
		project.setModel(model);
	}
}
