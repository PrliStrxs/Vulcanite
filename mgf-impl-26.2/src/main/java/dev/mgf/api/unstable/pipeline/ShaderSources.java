package dev.mgf.api.unstable.pipeline;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * Shader-source composition and compatibility helpers for Minecraft 26.2.
 *
 * <p>Sources authored through this class use the 26.3 convention:
 * {@code #include <namespace:path>} and explicit locations on every stage
 * input/output. MGF expands includes for the 26.2 compilers, keeps the highest
 * GLSL version found in the include tree, and installs the RenderPearl define
 * aliases needed by sources shared across the 26.2/26.3 package transition.
 */
public final class ShaderSources {

    private static final String INCLUDE_PREFIX = "shaders/include/";
    private static final Pattern INCLUDE_PATTERN = Pattern.compile(
            "(?m)^[\\t ]*#(?:include|moj_import)[\\t ]+<([^>\\r\\n]+)>[\\t ]*(?://[^\\r\\n]*)?$");
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "(?m)^[\\t ]*#version[\\t ]+(\\d+)[^\\r\\n]*$");
    private static final Pattern INTERFACE_PATTERN = Pattern.compile(
            "(?m)^[\\t ]*(?<layouts>(?:layout\\s*\\([^)]*\\)\\s*)*)"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample|invariant|precise|patch)\\s+)*"
                    + "(?:in|out)\\s+(?<declaration>[^;{]+)[;{]");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("\\blocation\\s*=");
    private static final Pattern DECLARED_NAME_PATTERN = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[[^]]*]\\s*)*(?=,|$)");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("//[^\\r\\n]*|/\\*.*?\\*/", Pattern.DOTALL);
    private static final String SEPARATE_SHADER_OBJECTS =
            "#extension GL_ARB_separate_shader_objects : require\n";
    private static final String DEFINE_ALIASES = """
            #if defined(B3D_DEPTH_IS_ZERO_TO_ONE) && !defined(RENDERPEARL_DEPTH_IS_ZERO_TO_ONE)
            #define RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
            #endif
            #if defined(RENDERPEARL_DEPTH_IS_ZERO_TO_ONE) && !defined(B3D_DEPTH_IS_ZERO_TO_ONE)
            #define B3D_DEPTH_IS_ZERO_TO_ONE
            #endif
            #if defined(B3D_INSTANCE_INDEX_INCLUDES_BASE_INSTANCE) && !defined(RENDERPEARL_INSTANCE_INDEX_INCLUDES_BASE_INSTANCE)
            #define RENDERPEARL_INSTANCE_INDEX_INCLUDES_BASE_INSTANCE
            #endif
            #if defined(RENDERPEARL_INSTANCE_INDEX_INCLUDES_BASE_INSTANCE) && !defined(B3D_INSTANCE_INDEX_INCLUDES_BASE_INSTANCE)
            #define B3D_INSTANCE_INDEX_INCLUDES_BASE_INSTANCE
            #endif
            """;

    private ShaderSources() {
    }

    /** @return a generated-source builder with no fallback */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Loads raw GLSL from the current resource manager and applies the same
     * forward-compatible preprocessing as generated sources.
     */
    public static ShaderSource resourcePack() {
        return forwardCompatible(ShaderSources::loadResource);
    }

    /**
     * Wraps an arbitrary source with the 26.3-to-26.2 compatibility pass.
     * Include lookups are delegated with a {@code null} shader type, matching
     * the 26.3 shaderc callback contract.
     */
    public static ShaderSource forwardCompatible(ShaderSource source) {
        Objects.requireNonNull(source, "source");
        return (id, type) -> {
            String raw = source.get(id, type);
            if (raw == null || type == null || !VERSION_PATTERN.matcher(raw).find()) {
                return raw;
            }
            return preprocess(id, type, raw, source);
        };
    }

    private static String preprocess(Identifier id, ShaderType type, String source, ShaderSource resolver) {
        Expansion expansion = new Expansion(resolver);
        String body = expansion.expand(source, new ArrayDeque<>());
        boolean hasStageInterface = validateExplicitLocations(id, type, body);

        StringBuilder result = new StringBuilder(body.length() + 512);
        result.append("#version ").append(expansion.maxVersion()).append('\n');
        if (hasStageInterface && !body.contains("GL_ARB_separate_shader_objects")) {
            result.append(SEPARATE_SHADER_OBJECTS);
        }
        result.append(DEFINE_ALIASES);
        result.append(body.stripLeading());
        return result.toString();
    }

    private static boolean validateExplicitLocations(Identifier id, ShaderType type, String source) {
        String uncommented = replaceCommentsWithWhitespace(source);
        Matcher declaration = INTERFACE_PATTERN.matcher(uncommented);
        boolean found = false;
        while (declaration.find()) {
            found = true;
            if (isBuiltinInterface(declaration.group("declaration"))) {
                continue;
            }
            if (!LOCATION_PATTERN.matcher(declaration.group("layouts")).find()) {
                int line = 1 + (int) uncommented.substring(0, declaration.start()).lines().count();
                throw new IllegalArgumentException("Shader " + id + " (" + type.getName()
                        + ") must use explicit layout(location = ...) for stage I/O at line " + line);
            }
        }
        return found;
    }

    private static boolean isBuiltinInterface(String declaration) {
        Matcher names = DECLARED_NAME_PATTERN.matcher(declaration.strip());
        boolean found = false;
        while (names.find()) {
            found = true;
            if (!names.group(1).startsWith("gl_")) {
                return false;
            }
        }
        return found;
    }

    private static String replaceCommentsWithWhitespace(String source) {
        Matcher comments = COMMENT_PATTERN.matcher(source);
        StringBuilder result = new StringBuilder(source);
        while (comments.find()) {
            for (int index = comments.start(); index < comments.end(); index++) {
                char value = result.charAt(index);
                if (value != '\r' && value != '\n') {
                    result.setCharAt(index, ' ');
                }
            }
        }
        return result.toString();
    }

    private static String loadResource(Identifier id, ShaderType type) {
        Identifier resourceId = type == null ? id : type.idConverter().idToFile(id);
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(resourceId).orElse(null);
        if (resource == null) {
            return null;
        }
        try (Reader reader = resource.openAsReader()) {
            StringWriter output = new StringWriter();
            reader.transferTo(output);
            return output.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read shader resource " + resourceId, e);
        }
    }

    private record ShaderKey(Identifier id, ShaderType type) {

        private ShaderKey {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
        }
    }

    /** Builder for generated roots/includes with an optional fallback source. */
    public static final class Builder {

        private final Map<ShaderKey, String> shaders = new LinkedHashMap<>();
        private final Map<Identifier, String> includes = new LinkedHashMap<>();
        private ShaderSource fallback;

        private Builder() {
        }

        public Builder put(Identifier id, ShaderType type, String source) {
            shaders.put(new ShaderKey(id, type), Objects.requireNonNull(source, "source"));
            return this;
        }

        /**
         * Adds an include under {@code assets/<namespace>/shaders/include/}.
         * The supplied id is the logical path used inside angle brackets.
         */
        public Builder include(Identifier id, String source) {
            Identifier resourceId = id.getPath().startsWith(INCLUDE_PREFIX)
                    ? id
                    : id.withPrefix(INCLUDE_PREFIX);
            includes.put(resourceId, Objects.requireNonNull(source, "source"));
            return this;
        }

        public Builder withFallback(ShaderSource source) {
            fallback = Objects.requireNonNull(source, "source");
            return this;
        }

        public Builder withResourcePackFallback() {
            return withFallback(ShaderSources::loadResource);
        }

        public ShaderSource build() {
            Map<ShaderKey, String> shaderSnapshot = Map.copyOf(shaders);
            Map<Identifier, String> includeSnapshot = Map.copyOf(includes);
            ShaderSource fallbackSnapshot = fallback;
            ShaderSource combined = (id, type) -> {
                String value = type == null
                        ? includeSnapshot.get(id)
                        : shaderSnapshot.get(new ShaderKey(id, type));
                if (value != null || fallbackSnapshot == null) {
                    return value;
                }
                return fallbackSnapshot.get(id, type);
            };
            return forwardCompatible(combined);
        }
    }

    private static final class Expansion {

        private final ShaderSource resolver;
        private int maxVersion;

        private Expansion(ShaderSource resolver) {
            this.resolver = resolver;
        }

        private String expand(String source, Deque<Identifier> includeStack) {
            Matcher versionMatcher = VERSION_PATTERN.matcher(source);
            while (versionMatcher.find()) {
                maxVersion = Math.max(maxVersion, Integer.parseInt(versionMatcher.group(1)));
            }
            String withoutVersions = versionMatcher.replaceAll("");

            Matcher includes = INCLUDE_PATTERN.matcher(withoutVersions);
            StringBuffer output = new StringBuffer();
            while (includes.find()) {
                Identifier logicalId = Identifier.parse(includes.group(1));
                Identifier resourceId = logicalId.withPrefix(INCLUDE_PREFIX);
                if (includeStack.contains(resourceId)) {
                    throw new IllegalArgumentException("Include cycle: " + formatCycle(includeStack, resourceId));
                }
                String included = resolver.get(resourceId, null);
                if (included == null) {
                    throw new IllegalArgumentException("Could not resolve shader include <" + logicalId + ">");
                }
                includeStack.addLast(resourceId);
                String expanded = expand(included, includeStack);
                includeStack.removeLast();
                includes.appendReplacement(output, Matcher.quoteReplacement(expanded));
            }
            includes.appendTail(output);
            return output.toString();
        }

        private int maxVersion() {
            return maxVersion;
        }

        private static String formatCycle(Deque<Identifier> stack, Identifier repeated) {
            StringBuilder result = new StringBuilder();
            for (Identifier id : stack) {
                if (!result.isEmpty()) {
                    result.append(" -> ");
                }
                result.append(id);
            }
            if (!result.isEmpty()) {
                result.append(" -> ");
            }
            return result.append(repeated).toString();
        }
    }
}
