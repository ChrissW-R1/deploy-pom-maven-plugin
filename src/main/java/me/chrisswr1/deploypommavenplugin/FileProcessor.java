package me.chrisswr1.deploypommavenplugin;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

public class FileProcessor {
	public void removeEmptyLines(
		final @NotNull File file,
		final @NotNull Charset charset
	) throws IOException {
		final @NotNull FileInputStream fis = new FileInputStream(file);
		final @NotNull List<String> nonEmptyLines;
		try (final @NotNull BufferedReader reader = new BufferedReader(
			new InputStreamReader(fis, charset)
		)) {
			nonEmptyLines = reader.lines().filter(
				line -> !(line.trim().isEmpty())
			).collect(Collectors.toList());
		}

		final @NotNull FileOutputStream fos = new FileOutputStream(file);
		try (BufferedWriter writer = new BufferedWriter(
			new OutputStreamWriter(fos, charset)
		)) {
			for (final @NotNull String line : nonEmptyLines) {
				writer.append(line);
				writer.append("\n");
			}
		}
	}
}
