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
package com.b3meter.engine.service.interpreter;

import com.b3meter.engine.service.plan.PlanNode;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code JUnitSampler} {@link PlanNode} using reflection.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code junitsampler.classname} — fully qualified test class name</li>
 *   <li>{@code junitsampler.constructorstring} — constructor argument (optional)</li>
 *   <li>{@code junitsampler.method} — test method name to invoke</li>
 *   <li>{@code junitsampler.pkg.filter} — package filter (informational)</li>
 *   <li>{@code junitsampler.success} — success message</li>
 *   <li>{@code junitsampler.failure} — failure message</li>
 *   <li>{@code junitsampler.error} — error message</li>
 *   <li>{@code junitsampler.exec_setup} — execute setUp method before test</li>
 *   <li>{@code junitsampler.exec_teardown} — execute tearDown method after test</li>
 *   <li>{@code junitsampler.append_error} — append errors to response</li>
 *   <li>{@code junitsampler.append_exception} — append exceptions to response</li>
 * </ul>
 *
 * <p>Attempts to load the specified class via reflection and invoke the test method.
 * If the class is not on the classpath, returns a 501 (Not Implemented) result.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class JUnitSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(JUnitSamplerExecutor.class.getName());

    private JUnitSamplerExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Executes the JUnit test described by {@code node}.
     *
     * @param node      the JUnitSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String className = resolve(node.getStringProp("junitsampler.classname", ""), variables);
        String constructorString = resolve(node.getStringProp("junitsampler.constructorstring", ""), variables);
        String methodName = resolve(node.getStringProp("junitsampler.method", ""), variables);
        String successMsg = node.getStringProp("junitsampler.success", "Test passed");
        String failureMsg = node.getStringProp("junitsampler.failure", "Test failed");
        String errorMsg = node.getStringProp("junitsampler.error", "Test error");
        boolean execSetup = node.getBoolProp("junitsampler.exec_setup");
        boolean execTeardown = node.getBoolProp("junitsampler.exec_teardown");
        boolean appendError = node.getBoolProp("junitsampler.append_error");
        boolean appendException = node.getBoolProp("junitsampler.append_exception");

        if (className.isBlank()) {
            result.setFailureMessage("JUnitSampler: classname is empty");
            return;
        }

        if (methodName.isBlank()) {
            result.setFailureMessage("JUnitSampler: method is empty");
            return;
        }

        LOG.log(Level.FINE, "JUnitSamplerExecutor: invoking {0}.{1}()",
                new Object[]{className, methodName});

        long start = System.currentTimeMillis();

        try {
            Class<?> clazz = Class.forName(className);
            Object instance;

            // Try constructor with String argument, fall back to no-arg
            if (!constructorString.isBlank()) {
                try {
                    instance = clazz.getDeclaredConstructor(String.class).newInstance(constructorString);
                } catch (NoSuchMethodException e) {
                    instance = clazz.getDeclaredConstructor().newInstance();
                }
            } else {
                instance = clazz.getDeclaredConstructor().newInstance();
            }

            // Execute setUp if requested
            if (execSetup) {
                invokeIfExists(clazz, instance, "setUp");
            }

            // Execute test method
            Method testMethod = clazz.getMethod(methodName);
            testMethod.invoke(instance);

            // Execute tearDown if requested
            if (execTeardown) {
                invokeIfExists(clazz, instance, "tearDown");
            }

            long elapsed = System.currentTimeMillis() - start;
            result.setTotalTimeMs(elapsed);
            result.setStatusCode(200);
            result.setResponseBody(successMsg + "\nClass: " + className + "\nMethod: " + methodName
                    + "\nDuration: " + elapsed + "ms");

        } catch (ClassNotFoundException e) {
            long elapsed = System.currentTimeMillis() - start;
            result.setTotalTimeMs(elapsed);
            result.setStatusCode(501);
            String body = "JUnitSampler: class not found on classpath: " + className + "\n"
                    + "Method: " + methodName + "\n"
                    + "Constructor arg: " + constructorString;
            if (appendException) {
                body += "\nException: " + e.getMessage();
            }
            result.setResponseBody(body);
            result.setFailureMessage("JUnitSampler: class not found: " + className);
            LOG.log(Level.WARNING,
                    "JUnitSamplerExecutor: class not found: {0}", className);

        } catch (AssertionError e) {
            long elapsed = System.currentTimeMillis() - start;
            result.setTotalTimeMs(elapsed);
            result.setStatusCode(200);
            String body = failureMsg + "\nClass: " + className + "\nMethod: " + methodName;
            if (appendError) {
                body += "\nAssertion: " + e.getMessage();
            }
            result.setResponseBody(body);
            result.setFailureMessage("JUnitSampler assertion failed: " + e.getMessage());

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            result.setTotalTimeMs(elapsed);
            result.setStatusCode(500);
            String body = errorMsg + "\nClass: " + className + "\nMethod: " + methodName;
            if (appendException) {
                body += "\nException: " + e.getClass().getName() + ": " + e.getMessage();
            }
            result.setResponseBody(body);
            result.setFailureMessage("JUnitSampler error: " + e.getMessage());
            LOG.log(Level.WARNING,
                    "JUnitSamplerExecutor: error invoking " + className + "." + methodName, e);
        }
    }

    /**
     * Invokes a named no-arg method on the instance if it exists.
     */
    private static void invokeIfExists(Class<?> clazz, Object instance, String methodName) {
        try {
            Method m = clazz.getMethod(methodName);
            m.invoke(instance);
        } catch (NoSuchMethodException ignored) {
            // Method doesn't exist -- skip
        } catch (Exception e) {
            LOG.log(Level.FINE, "JUnitSamplerExecutor: {0}() failed: {1}",
                    new Object[]{methodName, e.getMessage()});
        }
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
