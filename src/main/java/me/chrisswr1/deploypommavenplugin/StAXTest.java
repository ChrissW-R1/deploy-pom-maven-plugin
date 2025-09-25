package me.chrisswr1.deploypommavenplugin;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

public class StAXTest {
	public static void main(
		String[] args
	 ) throws Exception {
		XMLInputFactory inFactory = XMLInputFactory.newInstance();
		inFactory.setProperty(XMLInputFactory.IS_COALESCING, false);
		inFactory.setProperty(
			"javax.xml.stream.isSupportingExternalEntities",
			false
		);

		XMLOutputFactory outFactory = XMLOutputFactory.newInstance();

		try (
			InputStream inStream = new FileInputStream("pom.xml");
			OutputStream outStream = new FileOutputStream("deploy.pom")
		) {
			XMLEventReader reader = inFactory.createXMLEventReader(
				inStream,
				StandardCharsets.UTF_8.name()
			);
			XMLEventWriter writer = outFactory.createXMLEventWriter(
				outStream,
				StandardCharsets.UTF_8.name()
			);
			XMLEventFactory eventFactory = XMLEventFactory.newFactory();

			Deque<QName> stack          = new ArrayDeque<>();
			boolean      developerAdded = false;

			while (reader.hasNext()) {
				XMLEvent event = reader.nextEvent();

				if (event.isStartElement()) {
					stack.push(event.asStartElement().getName());
					writer.add(event);
				} else if (event.isEndElement()) {
					writer.add(event);

					QName name = stack.pop();

					if (!developerAdded &&
						"build".equals(name.getLocalPart())) {
						writer.add(eventFactory.createStartElement(
							"",
							"",
							"developers"
						));
						writer.add(eventFactory.createEndElement(
							"",
							"",
							"developers"
						));

						developerAdded = true;
					}
				} else {
					writer.add(event);
				}
			}

			writer.flush();
			writer.close();
			reader.close();
		}
	}
}
