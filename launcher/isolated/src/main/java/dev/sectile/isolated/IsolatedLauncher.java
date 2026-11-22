package dev.sectile.isolated;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class IsolatedLauncher {

  public static final Path CACHE_PATH = Paths.get("cache/public/");

  private static final String EMBEDDED_PUBLIC_JAR = "embedded/public.jar";
  private static final String MAIN_FUNCTION = "startServer";
  private static final String ENTRYPOINT_CLASS = "dev.sectile.boostrap.BoostrapKt";

  static void main() throws Throwable {
    final long time = System.currentTimeMillis();
    final Path ownJar = resolveOwnJar();
    final Path publicJar = extractEmbeddedPublicJar(ownJar);

    final URLClassLoader publicLoader =
        new URLClassLoader(
            "public", new URL[] {publicJar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());

    final URLClassLoader internalLoader =
        new URLClassLoader("internal", new URL[] {ownJar.toUri().toURL()}, publicLoader);

    final Thread thread = Thread.currentThread();
    final ClassLoader previous = thread.getContextClassLoader();

    thread.setContextClassLoader(internalLoader);

    try {
      MethodHandles.publicLookup()
          .findStatic(
              Class.forName(ENTRYPOINT_CLASS, true, internalLoader),
              MAIN_FUNCTION,
              MethodType.methodType(Void.TYPE, ClassLoader.class, Long.TYPE))
          .invokeExact((ClassLoader) publicLoader, time);
    } finally {
      thread.setContextClassLoader(previous);
    }
  }

  private static Path resolveOwnJar() throws Exception {
    final Path codeSource =
        Paths.get(
                IsolatedLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toAbsolutePath()
            .normalize();

    if (!Files.isRegularFile(codeSource)) {
      throw new IllegalStateException(
          "IsolatedLauncher must be executed from isolated.jar. Compile in gradle using the command: `./gradlew :launcher:runtime:build` and execute the generated jar in `launcher/runtime/build/runtime/`");
    }

    return codeSource;
  }

  private static Path extractEmbeddedPublicJar(final Path ownJar) throws Exception {
    try (JarFile jarFile = new JarFile(ownJar.toFile())) {
      final Manifest manifest = jarFile.getManifest();
      if (manifest == null) {
        throw new IllegalStateException("No MANIFEST found in jar file. Corrupted jar");
      }

      final String compileID = manifest.getMainAttributes().getValue("compile-id");
      if (compileID == null) {
        throw new IllegalStateException("Missing compile-id in Manifest. Corrupted jar");
      }

      final Path publicJar = CACHE_PATH.resolve(compileID + ".jar");

      if (Files.isRegularFile(publicJar)) {
        return publicJar;
      }

      final JarEntry entry = jarFile.getJarEntry(EMBEDDED_PUBLIC_JAR);
      if (entry == null) {
        throw new IllegalStateException("Missing embedded jar. Corrupted jar");
      }

      Files.createDirectories(CACHE_PATH);
      deleteStaleJarsInCache();

      // Copy to a temp file first, then move atomically, so a crash or a concurrent
      // instance can never leave a truncated jar at the final path.
      final Path tmp = Files.createTempFile(CACHE_PATH, "extract-", ".tmp");
      try {
        try (InputStream input = jarFile.getInputStream(entry)) {
          Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING);
        }

        try {
          Files.move(tmp, publicJar, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
          // Another instance may have finished first (e.g. Windows can't replace an existing file).
          if (!Files.isRegularFile(publicJar)) {
            throw e;
          }
        }
      } finally {
        Files.deleteIfExists(tmp);
      }

      return publicJar;
    }
  }

  private static void deleteStaleJarsInCache() throws IOException {
    if (!Files.isDirectory(CACHE_PATH)) {
      return;
    }

    // Only *.jar: leaves in-progress .tmp files of other instances untouched.
    try (DirectoryStream<Path> paths = Files.newDirectoryStream(CACHE_PATH, "*.jar")) {
      for (final Path path : paths) {
        try {
          Files.deleteIfExists(path);
        } catch (IOException _) {
          // Best effort (e.g. jar still locked by another process on Windows).
        }
      }
    }
  }
}
