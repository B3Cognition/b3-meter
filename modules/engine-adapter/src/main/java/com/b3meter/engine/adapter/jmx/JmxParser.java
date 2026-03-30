/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.engine.adapter.jmx;

import com.b3meter.engine.adapter.security.XStreamSecurityPolicy;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.security.ForbiddenClassException;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses JMX files with XStream security policy applied.
 * All deserialization goes through the deny-all + explicit allowlist.
 */
public final class JmxParser {

    private final XStream xstream;

    public JmxParser() {
        xstream = new XStream();
        XStreamSecurityPolicy.apply(xstream);
        xstream.allowTypesByWildcard(new String[]{"java.util.*"});
    }

    /**
     * Parse a JMX input stream into a tree structure.
     *
     * @throws SecurityException if the JMX contains blocked class types (gadget chains)
     * @throws JmxParseException if the XML is malformed or cannot be parsed
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parse(InputStream jmxContent) {
        if (jmxContent == null) throw new IllegalArgumentException("jmxContent must not be null");
        try {
            Object result = xstream.fromXML(jmxContent);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
            // Wrap non-map results
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("root", result);
            return wrapper;
        } catch (ForbiddenClassException e) {
            throw new SecurityException("JMX contains blocked class type: " + e.getMessage(), e);
        } catch (XStreamException e) {
            throw new JmxParseException("Failed to parse JMX: " + e.getMessage(), e);
        }
    }

    /**
     * Parse a JMX string into a tree structure.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parse(String jmxXml) {
        if (jmxXml == null || jmxXml.isBlank()) throw new IllegalArgumentException("jmxXml must not be null or blank");
        try {
            Object result = xstream.fromXML(jmxXml);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("root", result);
            return wrapper;
        } catch (ForbiddenClassException e) {
            throw new SecurityException("JMX contains blocked class type: " + e.getMessage(), e);
        } catch (XStreamException e) {
            throw new JmxParseException("Failed to parse JMX: " + e.getMessage(), e);
        }
    }

    /**
     * Serialize a tree structure back to JMX XML format.
     */
    public String serialize(Map<String, Object> treeData) {
        if (treeData == null) throw new IllegalArgumentException("treeData must not be null");
        return xstream.toXML(treeData);
    }
}
