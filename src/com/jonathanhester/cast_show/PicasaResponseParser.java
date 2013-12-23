package com.jonathanhester.cast_show;

import java.io.StringReader;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import android.util.Log;

public class PicasaResponseParser {
	public static ArrayList<CastMediaAlbum> parseAlbums(String response) {
		ArrayList<CastMediaAlbum> albums = new ArrayList<CastMediaAlbum>();
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(new InputSource(new StringReader(response)));

			NodeList nList = doc.getElementsByTagName("entry");
			for (int temp = 0; temp < nList.getLength(); temp++) {
				Node nNode = nList.item(temp);
				Element element = (Element) nNode;
				String id = getValue("gphoto:name", element);
				String title = getValue("title", element);
				String userId = getValue("gphoto:user", element);
				albums.add(new CastMediaAlbum(title, userId + "/" + id));
			}
		} catch (Exception e) {
			Log.d("asdf", "asdf");
		}
		return albums;
	}

	private static String getValue(String tag, Element element) {
		NodeList nodes = element.getElementsByTagName(tag).item(0)
				.getChildNodes();
		Node node = (Node) nodes.item(0);
		return node.getNodeValue();
	}

}
