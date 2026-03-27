package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JDBCConnectionConfigExecutor}.
 */
class JDBCConnectionConfigExecutorTest {

    @Test
    void configure_storesAllPropertiesInVariables() {
        PlanNode node = PlanNode.builder("JDBCDataSource", "JDBC Config")
                .property("dataSource", "myDB")
                .property("dbUrl", "jdbc:mysql://localhost:3306/test")
                .property("driver", "com.mysql.cj.jdbc.Driver")
                .property("username", "root")
                .property("password", "secret")
                .property("checkQuery", "SELECT 1")
                .property("autocommit", "true")
                .property("maxActive", "20")
                .property("maxWait", "5000")
                .property("poolMax", "20")
                .property("timeout", "15000")
                .property("transactionIsolation", "READ_COMMITTED")
                .property("keepAlive", "true")
                .property("connectionAge", "3000")
                .property("connectionProperties", "useSSL=false")
                .property("initQuery", "SET NAMES utf8")
                .property("preinit", true)
                .build();

        Map<String, String> variables = new HashMap<>();
        JDBCConnectionConfigExecutor.configure(node, variables);

        String prefix = "__jmn_jdbc_myDB_";
        assertEquals("jdbc:mysql://localhost:3306/test", variables.get(prefix + "dbUrl"));
        assertEquals("com.mysql.cj.jdbc.Driver", variables.get(prefix + "driver"));
        assertEquals("root", variables.get(prefix + "username"));
        assertEquals("secret", variables.get(prefix + "password"));
        assertEquals("SELECT 1", variables.get(prefix + "checkQuery"));
        assertEquals("true", variables.get(prefix + "autocommit"));
        assertEquals("20", variables.get(prefix + "maxActive"));
        assertEquals("5000", variables.get(prefix + "maxWait"));
        assertEquals("20", variables.get(prefix + "poolMax"));
        assertEquals("15000", variables.get(prefix + "timeout"));
        assertEquals("READ_COMMITTED", variables.get(prefix + "transactionIsolation"));
        assertEquals("true", variables.get(prefix + "keepAlive"));
        assertEquals("3000", variables.get(prefix + "connectionAge"));
        assertEquals("useSSL=false", variables.get(prefix + "connectionProperties"));
        assertEquals("SET NAMES utf8", variables.get(prefix + "initQuery"));
        assertEquals("true", variables.get(prefix + "preinit"));
        assertEquals("true", variables.get(prefix + "configured"));
    }

    @Test
    void isConfigured_returnsTrueWhenConfigured() {
        PlanNode node = PlanNode.builder("JDBCDataSource", "JDBC")
                .property("dataSource", "testDS")
                .property("dbUrl", "jdbc:h2:mem:test")
                .build();

        Map<String, String> variables = new HashMap<>();
        JDBCConnectionConfigExecutor.configure(node, variables);

        assertTrue(JDBCConnectionConfigExecutor.isConfigured("testDS", variables));
        assertFalse(JDBCConnectionConfigExecutor.isConfigured("otherDS", variables));
    }

    @Test
    void getProperty_returnsStoredValue() {
        PlanNode node = PlanNode.builder("JDBCDataSource", "JDBC")
                .property("dataSource", "ds1")
                .property("dbUrl", "jdbc:postgresql://localhost/mydb")
                .property("driver", "org.postgresql.Driver")
                .build();

        Map<String, String> variables = new HashMap<>();
        JDBCConnectionConfigExecutor.configure(node, variables);

        assertEquals("jdbc:postgresql://localhost/mydb",
                JDBCConnectionConfigExecutor.getProperty("ds1", "dbUrl", variables));
        assertEquals("org.postgresql.Driver",
                JDBCConnectionConfigExecutor.getProperty("ds1", "driver", variables));
    }

    @Test
    void getProperty_returnsNull_whenNotConfigured() {
        Map<String, String> variables = new HashMap<>();
        assertNull(JDBCConnectionConfigExecutor.getProperty("missing", "dbUrl", variables));
    }

    @Test
    void configure_emptyDataSource_skips() {
        PlanNode node = PlanNode.builder("JDBCDataSource", "No DS Name")
                .property("dataSource", "")
                .property("dbUrl", "jdbc:h2:mem:test")
                .build();

        Map<String, String> variables = new HashMap<>();
        JDBCConnectionConfigExecutor.configure(node, variables);

        // Nothing should be stored
        assertTrue(variables.isEmpty());
    }

    @Test
    void configure_defaultValues() {
        PlanNode node = PlanNode.builder("JDBCDataSource", "JDBC")
                .property("dataSource", "defaults")
                .build();

        Map<String, String> variables = new HashMap<>();
        JDBCConnectionConfigExecutor.configure(node, variables);

        String prefix = "__jmn_jdbc_defaults_";
        assertEquals("", variables.get(prefix + "dbUrl"));
        assertEquals("", variables.get(prefix + "driver"));
        assertEquals("SELECT 1", variables.get(prefix + "checkQuery"));
        assertEquals("true", variables.get(prefix + "autocommit"));
        assertEquals("10", variables.get(prefix + "maxActive"));
        assertEquals("10000", variables.get(prefix + "maxWait"));
        assertEquals("DEFAULT", variables.get(prefix + "transactionIsolation"));
        assertEquals("false", variables.get(prefix + "preinit"));
    }

    @Test
    void configure_multipleDataSources_dontConflict() {
        PlanNode node1 = PlanNode.builder("JDBCDataSource", "DB1")
                .property("dataSource", "ds1")
                .property("dbUrl", "jdbc:mysql://host1/db1")
                .build();

        PlanNode node2 = PlanNode.builder("JDBCDataSource", "DB2")
                .property("dataSource", "ds2")
                .property("dbUrl", "jdbc:mysql://host2/db2")
                .build();

        Map<String, String> variables = new HashMap<>();
        JDBCConnectionConfigExecutor.configure(node1, variables);
        JDBCConnectionConfigExecutor.configure(node2, variables);

        assertEquals("jdbc:mysql://host1/db1",
                JDBCConnectionConfigExecutor.getProperty("ds1", "dbUrl", variables));
        assertEquals("jdbc:mysql://host2/db2",
                JDBCConnectionConfigExecutor.getProperty("ds2", "dbUrl", variables));
    }

    @Test
    void configure_nullNode_throws() {
        assertThrows(NullPointerException.class,
                () -> JDBCConnectionConfigExecutor.configure(null, new HashMap<>()));
    }

    @Test
    void configure_nullVariables_throws() {
        PlanNode node = PlanNode.builder("JDBCDataSource", "test")
                .property("dataSource", "ds")
                .build();
        assertThrows(NullPointerException.class,
                () -> JDBCConnectionConfigExecutor.configure(node, null));
    }
}
