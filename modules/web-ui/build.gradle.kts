/**
 * web-ui — React 19 + Vite frontend placeholder.
 *
 * Placeholder module for the React 19 + Vite frontend. The Node/npm build
 * will be wired in a future task. Currently only provides an empty static
 * resources directory so the Gradle project graph is complete.
 *
 * NOTE: The java plugin is NOT applied here — this is a frontend-only module.
 * The node-gradle plugin will be enabled in a future task once the UI scaffold
 * is ready.
 */

// No plugins applied — placeholder only.
// TODO(T-web-ui-scaffold): apply node-gradle plugin and configure Vite build.

description = "Web-ui: React 19 + Vite frontend (placeholder)"

tasks.register("buildFrontend") {
    group = "build"
    description = "Placeholder task for the React/Vite frontend build (not yet implemented)"
    doLast {
        logger.lifecycle("web-ui: frontend build not yet configured — placeholder task only")
    }
}
