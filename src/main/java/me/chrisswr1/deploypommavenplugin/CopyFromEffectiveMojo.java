package me.chrisswr1.deploypommavenplugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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

@Mojo(name = "copy-from-effective", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
@Keep
public class CopyFromEffectiveMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}-effective.pom", readonly = true)
    @KeepName
    private File effectivePom;
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.pom", readonly = true)
    @KeepName
    private File outputPom;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            final @NotNull DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            final @NotNull DocumentBuilder builder = factory.newDocumentBuilder();
            final @NotNull Document effectiveDoc = builder.parse(this.effectivePom);
            final @NotNull Document outputDoc = builder.parse(this.outputPom);

            final @NotNull Element outputRoot = outputDoc.getDocumentElement();
            final @Nullable Element developers = effectiveDoc.getElementById("developers");

            if (outputRoot.getElementsByTagName("developers").item(0) == null) {
                outputRoot.appendChild(developers);
            }

            final @NotNull Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(outputDoc), new StreamResult(outputPom));
        } catch (final @NotNull ParserConfigurationException | SAXException | IOException e) {
            throw new MojoExecutionException("Couldn't parse POM!", e);
        } catch (final @NotNull TransformerException e) {
            throw new MojoExecutionException("Couldn't save output POM!", e);
        }
    }
}
