package me.chrisswr1.deploypommavenplugin;

import lombok.Getter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import proguard.annotation.Keep;
import proguard.annotation.KeepName;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Set;

@Mojo(
	name = "copy-from-effective",
	defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
	threadSafe = true
)
@Keep
public class CopyFromEffectiveMojo extends AbstractMojo {
	@Parameter(
		defaultValue = "${project.build.directory}/${project.build.finalName}-effective.pom",
		readonly = true
	)
	@KeepName
	@Getter
	private File effectivePom;
	@Parameter(
		defaultValue = "${project.basedir}/pom.xml",
		readonly = true
	)
	@KeepName
	@Getter
	private File outputPom;
	@Parameter(
		defaultValue = "${project.build.sourceEncoding}",
		readonly = true
	)
	@KeepName
	@Getter
	private String charset;
	@Parameter(
		readonly = true
	)
	@KeepName
	@Getter
	private Set<String> nodes = Set.of(
		"url",
		"licenses",
		"developers"
	);

	@Override
	public void execute() throws MojoExecutionException {
		final @Nullable File effectivePom = this.getEffectivePom();
		if (effectivePom == null || !(effectivePom.exists())) {
			this.getLog().warn("Couldn't find effective POM!");
			return;
		}
		final @Nullable File outputPom = this.getOutputPom();
		if (outputPom == null || !(outputPom.exists())) {
			this.getLog().warn("Couldn't find output POM!");
			return;
		}
		@Nullable String charset = this.getCharset();
		if (charset == null) {
			charset = "UTF-8";
		}

		try {
			final @NotNull DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			final @NotNull DocumentBuilder builder = factory.newDocumentBuilder();

			final @NotNull Document effectiveDoc = builder.parse(effectivePom);

			final @NotNull Document outputDoc = builder.parse(outputPom);
			final @NotNull Element outputRoot = outputDoc.getDocumentElement();
			boolean outputChanged = false;

			for (final @Nullable String nodeName : this.nodes) {
				if (nodeName == null || nodeName.isEmpty()) {
					continue;
				}

				final @Nullable Node effectiveNode = effectiveDoc.getDocumentElement().getElementsByTagName(nodeName).item(0);
				final @Nullable Node importedNode = outputDoc.importNode(effectiveNode, true);

				if (outputRoot.getElementsByTagName(nodeName).item(0) == null && importedNode != null) {
					outputRoot.appendChild(importedNode);
					outputChanged = true;
					this.getLog().info("Added " + nodeName + " from effective POM to output POM.");
				}
			}

			if (outputChanged) {
				final @NotNull Transformer transformer = TransformerFactory.newInstance().newTransformer();
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				transformer.setOutputProperty(OutputKeys.ENCODING, charset);
				transformer.transform(new DOMSource(outputDoc), new StreamResult(outputPom));
				this.getLog().info("Output POM formatted.");
			}
		} catch (final @NotNull ParserConfigurationException | SAXException | IOException e) {
			throw new MojoExecutionException("Couldn't parse POM!", e);
		} catch (final @NotNull TransformerException e) {
			throw new MojoExecutionException("Couldn't save output POM!", e);
		}

		try {
			FileProcessor.removeEmptyLines(outputPom, Charset.forName(charset));
		} catch (final @NotNull IOException e) {
			throw new MojoExecutionException("Couldn't remove empty lines from output POM!", e);
		}
	}
}
