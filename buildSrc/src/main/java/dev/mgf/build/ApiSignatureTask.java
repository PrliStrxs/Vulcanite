package dev.mgf.build;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Emits and optionally checks the public/protected signature of one API JAR. */
public abstract class ApiSignatureTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getApiJar();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getBaselineFile();

    @OutputFile
    public abstract RegularFileProperty getSignatureFile();

    @Input
    public abstract Property<Boolean> getCompareWithBaseline();

    public ApiSignatureTask() {
        getCompareWithBaseline().convention(false);
    }

    @TaskAction
    public void generateSignature() {
        Path jar = getApiJar().get().getAsFile().toPath();
        String signature = inspect(jar);
        Path output = getSignatureFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, signature, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new GradleException("Failed to write API signature " + output, exception);
        }

        if (!getCompareWithBaseline().get()) {
            return;
        }
        if (!getBaselineFile().isPresent()) {
            throw new GradleException("API signature baseline is not configured");
        }
        Path baseline = getBaselineFile().get().getAsFile().toPath();
        String expected;
        try {
            expected = Files.readString(baseline, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new GradleException("Failed to read API signature baseline " + baseline, exception);
        }
        if (!expected.equals(signature)) {
            throw new GradleException("Public API signature differs from " + baseline
                    + "; run :mgf-api:apiSignature and review the generated report");
        }
    }

    private static String inspect(Path jar) {
        List<String> classNames = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            jarFile.stream()
                    .map(JarEntry::getName)
                    .filter(name -> name.endsWith(".class"))
                    .filter(name -> !name.equals("module-info.class"))
                    .map(name -> name.substring(0, name.length() - 6).replace('/', '.'))
                    .sorted()
                    .forEach(classNames::add);
        } catch (IOException exception) {
            throw new GradleException("Failed to inspect API JAR " + jar, exception);
        }

        List<String> signatures = new ArrayList<>();
        URL jarUrl;
        try {
            jarUrl = jar.toUri().toURL();
        } catch (IOException exception) {
            throw new GradleException("Failed to create API JAR URL " + jar, exception);
        }
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {jarUrl}, ClassLoader.getPlatformClassLoader())) {
            for (String className : classNames) {
                Class<?> type = Class.forName(className, false, loader);
                if (apiVisible(type.getModifiers())) {
                    appendType(signatures, type);
                }
            }
        } catch (ReflectiveOperationException | IOException | LinkageError exception) {
            throw new GradleException("Failed to load API classes from " + jar, exception);
        }
        signatures.sort(Comparator.naturalOrder());
        return String.join("\n", signatures) + "\n";
    }

    private static void appendType(List<String> signatures, Class<?> type) {
        signatures.add("TYPE " + modifiers(type.getModifiers()) + kind(type) + " "
                + type.getName() + typeParameters(type.getTypeParameters()));
        if (type.getGenericSuperclass() != null) {
            signatures.add("SUPER " + type.getName() + " " + name(type.getGenericSuperclass()));
        }
        Arrays.stream(type.getGenericInterfaces())
                .map(ApiSignatureTask::name)
                .sorted()
                .forEach(value -> signatures.add("INTERFACE " + type.getName() + " " + value));

        if (type.isRecord()) {
            RecordComponent[] components = type.getRecordComponents();
            for (int index = 0; index < components.length; index++) {
                RecordComponent component = components[index];
                signatures.add("RECORD " + type.getName() + " " + index + " "
                        + name(component.getGenericType()) + " " + component.getName());
            }
        }
        Arrays.stream(type.getDeclaredConstructors())
                .filter(value -> apiVisible(value.getModifiers()))
                .map(ApiSignatureTask::constructorSignature)
                .forEach(signatures::add);
        Arrays.stream(type.getDeclaredFields())
                .filter(value -> apiVisible(value.getModifiers()))
                .map(ApiSignatureTask::fieldSignature)
                .forEach(signatures::add);
        Arrays.stream(type.getDeclaredMethods())
                .filter(value -> apiVisible(value.getModifiers()))
                .map(ApiSignatureTask::methodSignature)
                .forEach(signatures::add);
    }

    private static String constructorSignature(Constructor<?> value) {
        return "CONSTRUCTOR " + modifiers(value.getModifiers()) + value.getDeclaringClass().getName()
                + typeParameters(value.getTypeParameters())
                + parameters(value.getGenericParameterTypes())
                + exceptions(value.getGenericExceptionTypes());
    }

    private static String fieldSignature(Field value) {
        return "FIELD " + modifiers(value.getModifiers()) + value.getDeclaringClass().getName()
                + "." + value.getName() + " " + name(value.getGenericType());
    }

    private static String methodSignature(Method value) {
        return "METHOD " + modifiers(value.getModifiers()) + value.getDeclaringClass().getName()
                + "." + value.getName() + typeParameters(value.getTypeParameters())
                + parameters(value.getGenericParameterTypes()) + " "
                + name(value.getGenericReturnType()) + exceptions(value.getGenericExceptionTypes());
    }

    private static String modifiers(int value) {
        String modifiers = Modifier.toString(value);
        return modifiers.isEmpty() ? "" : modifiers + " ";
    }

    private static String kind(Class<?> type) {
        if (type.isAnnotation()) {
            return "annotation";
        }
        if (type.isEnum()) {
            return "enum";
        }
        if (type.isRecord()) {
            return "record";
        }
        return type.isInterface() ? "interface" : "class";
    }

    private static String typeParameters(TypeVariable<?>[] variables) {
        if (variables.length == 0) {
            return "";
        }
        return Arrays.stream(variables)
                .map(variable -> variable.getName() + bounds(variable))
                .reduce("<", (left, right) -> left.equals("<") ? left + right : left + "," + right)
                + ">";
    }

    private static String bounds(TypeVariable<?> variable) {
        Type[] bounds = variable.getBounds();
        if (bounds.length == 1 && bounds[0] == Object.class) {
            return "";
        }
        return " extends " + Arrays.stream(bounds)
                .map(ApiSignatureTask::name)
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow();
    }

    private static String parameters(Type[] types) {
        return "(" + Arrays.stream(types)
                .map(ApiSignatureTask::name)
                .reduce((left, right) -> left + "," + right)
                .orElse("") + ")";
    }

    private static String exceptions(Type[] types) {
        if (types.length == 0) {
            return "";
        }
        return " throws " + Arrays.stream(types)
                .map(ApiSignatureTask::name)
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
    }

    private static String name(Type type) {
        return type.getTypeName();
    }

    private static boolean apiVisible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }
}
