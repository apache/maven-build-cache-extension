/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.buildcache.xml;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import java.io.InputStream;

import org.apache.maven.buildcache.xml.build.Build;
import org.apache.maven.buildcache.xml.config.CacheConfig;
import org.apache.maven.buildcache.xml.diff.Diff;
import org.apache.maven.buildcache.xml.report.CacheReport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

public class XmlServiceTest {

    @Test
    @Disabled("cache-build-1.0.0.xsd not found")
    public void testConfig() throws Exception {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(getClass().getResource("/build-cache-config-1.0.0.xsd"));
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setSchema(schema);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(
                getClass().getResource("build-cache-config-instance.xml").toString());

        InputStream is = getClass().getResourceAsStream("build-cache-config-instance.xml");
        final CacheConfig cache = new XmlService().loadCacheConfig(is);
    }

    @Test
    @Disabled("cache-build-1.0.0.xsd not found")
    public void testReport() throws Exception {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(getClass().getResource("/build-cache-report-1.0.0.xsd"));
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setSchema(schema);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(
                getClass().getResource("build-cache-report-instance.xml").toString());

        InputStream is = getClass().getResourceAsStream("build-cache-report-instance.xml");
        final CacheReport cacheReport = new XmlService().loadCacheReport(is);
    }

    @Test
    @Disabled("cache-build-1.0.0.xsd not found")
    public void testBuild() throws Exception {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(getClass().getResource("/build-cache-build-1.0.0.xsd"));
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setSchema(schema);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(
                getClass().getResource("build-cache-build-instance.xml").toString());

        InputStream is = getClass().getResourceAsStream("build-cache-build-instance.xml");
        final Build build = new XmlService().loadBuild(is);
    }

    @Test
    @Disabled("cache-build-1.0.0.xsd not found")
    public void testDiff() throws Exception {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(getClass().getResource("/build-cache-diff-1.0.0.xsd"));
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setSchema(schema);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc =
                db.parse(getClass().getResource("build-cache-diff-instance.xml").toString());

        InputStream is = getClass().getResourceAsStream("build-cache-diff-instance.xml");
        final Diff buildDiff = new XmlService().loadDiff(is);
    }
}
