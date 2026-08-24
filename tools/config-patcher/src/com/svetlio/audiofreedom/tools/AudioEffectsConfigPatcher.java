package com.svetlio.audiofreedom.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class AudioEffectsConfigPatcher {
    private static final String IMPLEMENTATION_UUID =
            "2f6e8c10-8d44-4b42-b110-16f3a729ef01";
    private static final String TYPE_UUID =
            "a7e03c90-7c3d-4f48-9c8d-497c8f1b1201";
    private static final String ELEMENT_NAME = "audiofreedom";

    private AudioEffectsConfigPatcher() {}

    public static void main(String[] args) {
        if (args.length != 4) {
            fail("usage: AudioEffectsConfigPatcher <input> <output> <aidl|legacy> <library-path>");
        }
        try {
            patch(new File(args[0]), new File(args[1]), args[2], args[3]);
            System.out.println("patched=" + args[1]);
        } catch (Exception error) {
            fail(error.getMessage() == null ? error.getClass().getName() : error.getMessage());
        }
    }

    static void patch(File input, File output, String backend, String libraryPath)
            throws Exception {
        boolean aidl;
        if ("aidl".equals(backend)) {
            aidl = true;
        } else if ("legacy".equals(backend)) {
            aidl = false;
        } else {
            throw new IllegalArgumentException("unsupported backend: " + backend);
        }
        if (!input.isFile() || libraryPath.isEmpty() || libraryPath.contains("/")) {
            throw new IllegalArgumentException("invalid input or library path");
        }

        Document document = newDocumentBuilder().parse(input);
        Element root = document.getDocumentElement();
        if (root == null || !"audio_effects_conf".equals(localName(root))) {
            throw new IllegalArgumentException("not an Android audio effects configuration");
        }
        Element libraries = directChild(root, "libraries");
        Element effects = directChild(root, "effects");
        if (libraries == null || effects == null) {
            throw new IllegalArgumentException("configuration has no libraries/effects sections");
        }

        Element library = findByAttribute(document, "library", "name", ELEMENT_NAME);
        if (library == null) {
            library = createElement(document, root, "library");
            library.setAttribute("name", ELEMENT_NAME);
            libraries.appendChild(library);
        }
        library.setAttribute("path", libraryPath);

        Element effectByName = findByAttribute(document, "effect", "name", ELEMENT_NAME);
        Element effectByUuid = findByAttribute(document, "effect", "uuid", IMPLEMENTATION_UUID);
        if (effectByName != null && effectByUuid != null && effectByName != effectByUuid) {
            throw new IllegalArgumentException("AudioFreedom name and UUID belong to different effects");
        }
        Element effect = effectByUuid != null ? effectByUuid : effectByName;
        if (effect == null) {
            effect = createElement(document, root, "effect");
            effects.appendChild(effect);
        }
        effect.setAttribute("name", ELEMENT_NAME);
        effect.setAttribute("library", ELEMENT_NAME);
        effect.setAttribute("uuid", IMPLEMENTATION_UUID);
        if (aidl) {
            effect.setAttribute("type", TYPE_UUID);
        } else {
            effect.removeAttribute("type");
        }

        removeAutomaticAttachments(document);
        if (aidl) {
            addMusicAttachment(document, root);
        }
        write(document, output);
        validate(output, aidl, libraryPath);
    }

    private static void removeAutomaticAttachments(Document document) {
        NodeList nodes = document.getElementsByTagNameNS("*", "apply");
        List<Node> removals = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            Element apply = (Element) nodes.item(index);
            if (ELEMENT_NAME.equals(apply.getAttribute("effect"))) {
                removals.add(apply);
            }
        }
        for (Node removal : removals) {
            removal.getParentNode().removeChild(removal);
        }
    }

    private static void addMusicAttachment(Document document, Element root) {
        Element postprocess = directChild(root, "postprocess");
        if (postprocess == null) {
            postprocess = createElement(document, root, "postprocess");
            Element deviceEffects = directChild(root, "deviceEffects");
            if (deviceEffects == null) {
                root.appendChild(postprocess);
            } else {
                root.insertBefore(postprocess, deviceEffects);
            }
        }

        Element music = directChildByAttribute(postprocess, "stream", "type", "music");
        if (music == null) {
            music = createElement(document, root, "stream");
            music.setAttribute("type", "music");
            postprocess.appendChild(music);
        }
        Element apply = createElement(document, root, "apply");
        apply.setAttribute("effect", ELEMENT_NAME);
        music.appendChild(apply);
    }

    private static void validate(File output, boolean aidl, String libraryPath) throws Exception {
        Document result = newDocumentBuilder().parse(output);
        Element library = findByAttribute(result, "library", "name", ELEMENT_NAME);
        Element effect = findByAttribute(result, "effect", "uuid", IMPLEMENTATION_UUID);
        Element apply = findByAttribute(result, "apply", "effect", ELEMENT_NAME);
        if (library == null || !libraryPath.equals(library.getAttribute("path")) ||
                effect == null || !ELEMENT_NAME.equals(effect.getAttribute("library")) ||
                (aidl != (apply != null))) {
            throw new IllegalStateException("generated configuration failed validation");
        }
        if (aidl != TYPE_UUID.equals(effect.getAttribute("type"))) {
            throw new IllegalStateException("generated effect type does not match backend");
        }
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        try {
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (UnsupportedOperationException ignored) {
            // Android's compact parser does not implement every desktop DOM option.
        }
        return factory.newDocumentBuilder();
    }

    private static void setFeature(DocumentBuilderFactory factory, String name, boolean value) {
        try {
            factory.setFeature(name, value);
        } catch (ParserConfigurationException ignored) {
            // Vendor XML is local and trusted; structural validation still runs after writing.
        }
    }

    private static Element directChild(Element parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && name.equals(localName(child))) {
                return (Element) child;
            }
        }
        return null;
    }

    private static Element directChildByAttribute(
            Element parent, String name, String attribute, String value) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && name.equals(localName(child))) {
                Element element = (Element) child;
                if (value.equals(element.getAttribute(attribute))) {
                    return element;
                }
            }
        }
        return null;
    }

    private static Element findByAttribute(
            Document document, String elementName, String attribute, String value) {
        NodeList nodes = document.getElementsByTagNameNS("*", elementName);
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (value.equals(element.getAttribute(attribute))) {
                return element;
            }
        }
        return null;
    }

    private static Element createElement(Document document, Element root, String name) {
        String namespace = root.getNamespaceURI();
        return namespace == null || namespace.isEmpty()
                ? document.createElement(name)
                : document.createElementNS(namespace, name);
    }

    private static String localName(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
    }

    private static void write(Document document, File output) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (Exception ignored) {
            // Android implementations differ; parsing already rejects external entities.
        }
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(document), new StreamResult(output));
    }

    private static void fail(String message) {
        System.err.println("AudioFreedom config patcher: " + message);
        System.exit(2);
    }
}
