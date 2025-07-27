package me.chrisswr1.deploypommavenplugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;

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
import java.nio.charset.StandardCharsets;

public class XmlProcessor {
    private static final @NotNull Charset DEFAULT_ENCODING =
        StandardCharsets.UTF_8;

    public @NotNull DocumentBuilder createDocumentBuilder()
    throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder();
    }

    public void format(
        final @Nullable File file,
        final @Nullable Document document,
        final @Nullable Charset charset
    ) throws TransformerException, IOException {
        if (file == null) {
            return;
        }
        final @NotNull Charset fixedCharset =
            charset != null ?
            charset :
            XmlProcessor.DEFAULT_ENCODING;

        final @NotNull Transformer transformer =
            TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, fixedCharset.name());
        transformer.transform(new DOMSource(document), new StreamResult(file));
        (new FileProcessor()).removeEmptyLines(file, fixedCharset);
    }
}
