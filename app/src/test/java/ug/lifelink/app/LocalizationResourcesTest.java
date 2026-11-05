package ug.lifelink.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Guards the Luganda translation. A wrong format placeholder (for example a %d where the app
 * passes no number) makes the app crash at run time, so every Luganda entry must carry exactly
 * the placeholders of its English original, and must name something that exists.
 */
public class LocalizationResourcesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[sdf]");

    private static File resDir() {
        for (String p : new String[]{"src/main/res", "app/src/main/res"}) {
            File f = new File(p);
            if (new File(f, "values/strings.xml").exists()) {
                return f;
            }
        }
        throw new AssertionError("res folder not found from " + new File(".").getAbsolutePath());
    }

    /** name (or name[quantity] for plurals) to text */
    private static Map<String, String> load(File file) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        Map<String, String> out = new HashMap<>();
        NodeList strings = doc.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Element e = (Element) strings.item(i);
            out.put(e.getAttribute("name"), e.getTextContent());
        }
        NodeList plurals = doc.getElementsByTagName("plurals");
        for (int i = 0; i < plurals.getLength(); i++) {
            Element p = (Element) plurals.item(i);
            NodeList items = p.getElementsByTagName("item");
            for (int j = 0; j < items.getLength(); j++) {
                Element item = (Element) items.item(j);
                out.put(p.getAttribute("name") + "[" + item.getAttribute("quantity") + "]", item.getTextContent());
            }
        }
        return out;
    }

    private static List<String> placeholders(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        Collections.sort(found);
        return found;
    }

    @Test
    public void everyLugandaEntryNamesAnEnglishEntry() throws Exception {
        Map<String, String> en = load(new File(resDir(), "values/strings.xml"));
        Map<String, String> lg = load(new File(resDir(), "values-lg/strings.xml"));
        assertTrue("Luganda file is empty", lg.size() > 100);
        for (String key : lg.keySet()) {
            assertTrue("Luganda entry has no English original: " + key, en.containsKey(key));
        }
    }

    @Test
    public void lugandaPlaceholdersMatchEnglish() throws Exception {
        Map<String, String> en = load(new File(resDir(), "values/strings.xml"));
        Map<String, String> lg = load(new File(resDir(), "values-lg/strings.xml"));
        for (Map.Entry<String, String> e : lg.entrySet()) {
            assertEquals("placeholders differ in " + e.getKey(),
                    placeholders(en.get(e.getKey())), placeholders(e.getValue()));
        }
    }

    @Test
    public void legalConsentTextStaysInEnglishUntilReviewed() throws Exception {
        Map<String, String> lg = load(new File(resDir(), "values-lg/strings.xml"));
        assertTrue(!lg.containsKey("consent_text"));
        assertTrue(!lg.containsKey("app_name"));
    }
}
