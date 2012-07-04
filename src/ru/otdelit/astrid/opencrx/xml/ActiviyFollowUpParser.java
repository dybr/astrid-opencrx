package ru.otdelit.astrid.opencrx.xml;

import java.util.Arrays;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import ru.otdelit.astrid.opencrx.api.OpencrxUtils;

public class ActiviyFollowUpParser extends BaseParser{

    private final static List<String> tags = Arrays.asList("text", "transition");

    private final List<String> result;
	private final String transitionId;
	
	private String currentText;
	private String currentTransition;
	
	public ActiviyFollowUpParser(List<String> dest, String transitionId) {
		result = dest;
		this.transitionId = transitionId;
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		super.startElement(uri, localName, qName, attributes);
		
		if (tags.contains(qName))
		    initBuffer();
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {

		if (qName.equals("text"))
			currentText = buffer.toString();

		if (qName.equals("transition"))
			currentTransition = OpencrxUtils.getBaseXri(buffer.toString());
		
		if (qName.equals("org.opencrx.kernel.activity1.ActivityFollowUp"))
			if (transitionId.equals(currentTransition))
				result.add(currentText);
		
		super.endElement(uri, localName, qName);
	}
	
	

}
