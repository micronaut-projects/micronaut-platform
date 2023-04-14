/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.internal;

import groovy.xml.XmlSlurper;
import groovy.xml.slurpersupport.GPathResult;
import groovy.xml.slurpersupport.NodeChild;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.NodeIterator;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
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
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

@CacheableTask
public abstract class RewritePom extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getTemplate();

    @Input
    public abstract MapProperty<String, String> getTokenReplacements();

    @Input
    public abstract MapProperty<String, String> getPomProperties();

    @Input
    public abstract ListProperty<String> getIncludeBomPropertiesStringListProperty();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void rewritePom() throws ParserConfigurationException, IOException, SAXException, TransformerException {
        File template = getTemplate().get().getAsFile();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder dBuilder = factory.newDocumentBuilder();
        Document doc = dBuilder.parse(template);
        doc.getDocumentElement().normalize();
        DocumentTraversal traversal = (DocumentTraversal) doc;

        NodeIterator iterator = traversal.createNodeIterator(
            doc.getDocumentElement(), NodeFilter.SHOW_ALL, null, true);
        Map<String, String> replacements = getTokenReplacements().get();
        for (Node n = iterator.nextNode(); n != null; n = iterator.nextNode()) {
            String text = n.getTextContent().trim();
            if (text.startsWith("%%") && text.endsWith("%%")) {
                String token = text.substring(2, text.length() - 2);
                String replacement = replacements.get(token);
                if (replacement != null) {
                    n.setTextContent(replacement);
                }
            } else if ("properties".equals(n.getNodeName()) && "project".equals(n.getParentNode().getNodeName())) {
                for (Map.Entry<String, String> entry : getPomProperties().get().entrySet()) {
                    Node propertyNode = doc.createElement(entry.getKey() + ".version");
                    propertyNode.setTextContent(entry.getValue());
                    n.appendChild(propertyNode);
                }
                for (Map.Entry<String, String> entry : fetchBomProperties().entrySet()) {
                    Node propertyNode = doc.createElement(entry.getKey());
                    propertyNode.setTextContent(entry.getValue());
                    n.appendChild(propertyNode);
                }
            }
        }

        writeOutputFile(doc);
    }

    private void writeOutputFile(Document doc) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transf = transformerFactory.newTransformer();

        transf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        DOMSource source = new DOMSource(doc);
        StreamResult output = new StreamResult(getOutputFile().get().getAsFile());
        transf.transform(source, output);
    }

    private Map<String, String> fetchBomProperties() {
        Map<String, String> props = new TreeMap<>();
        for (String dependency : getIncludeBomPropertiesStringListProperty().get()) {
            String[] groups = dependency.split(":", 3);
            getLogger().lifecycle("Scanning {}:{}:{}", groups[0], groups[1], groups[2]);
            Map<String, String> bomProperties = bomProperties(groups[0], groups[1], groups[2]);
            for (Map.Entry<String, String> entry : bomProperties.entrySet()) {
                if (entry.getKey().startsWith("micronaut.")) {
                    getLogger().lifecycle("Skipping {} from {}", entry.getKey(), dependency);
                } else {
                    if (props.containsKey(entry.getKey())) {
                        getLogger().warn("Property {} from {} already exists ({}). Replacing with {}", entry.getKey(), dependency, props.get(entry.getKey()), entry.getValue());
                    }
                    props.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return Collections.unmodifiableMap(props);
    }

    private Map<String, String> bomProperties(String groupId, String artifactId, String version) {
        ArtifactResolutionResult result = getProject().getDependencies().createArtifactResolutionQuery()
            .forModule(groupId, artifactId, version)
            .withArtifacts(MavenModule.class, MavenPomArtifact.class)
            .execute();
        Map<String, String> props = new TreeMap<>();
        for (ComponentArtifactsResult component : result.getResolvedComponents()) {
            component.getArtifacts(MavenPomArtifact.class).forEach(artifact -> {
                if (artifact instanceof ResolvedArtifactResult) {
                    ResolvedArtifactResult resolved = (ResolvedArtifactResult) artifact;
                    GPathResult pom = null;
                    try {
                        pom = new XmlSlurper().parse(resolved.getFile());
                    } catch (IOException | SAXException | ParserConfigurationException e) {
                        // ignore
                    }
                    ((GPathResult) pom.getProperty("properties")).children().forEach(child -> {
                        NodeChild node = (NodeChild) child;
                        props.put(node.name(), node.text());
                    });
                }
            });
        }
        return props;
    }
}
