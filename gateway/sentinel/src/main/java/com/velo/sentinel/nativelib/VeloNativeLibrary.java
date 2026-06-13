package com.velo.sentinel.nativelib;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;

/**
 * VeloNativeLibrary: Singleton service for loading the Velo-Core native library.
 */
@Component
public class VeloNativeLibrary {
    private static final Logger log = LoggerFactory.getLogger(VeloNativeLibrary.class);

    private final String libPath;
    private final Linker linker;
    private SymbolLookup lookup;

    /**
     * Initializes the library loader with the path to the Velo-Core shared object.
     *
     * @param libPath Absolute path to the native library file.
     */
    public VeloNativeLibrary(@Value("${velo.core.lib-path}") String libPath) {
        this.libPath = libPath;
        this.linker = Linker.nativeLinker();
    }

    @PostConstruct
    public void init() {
        log.info("NATIVE-BRIDGE: Loading Velo-Core library from: {}", libPath);
        Path path = Path.of(libPath);
        
        if (!path.isAbsolute()) {
            Path resolved = Path.of("").toAbsolutePath().resolve(path).normalize();
            if (!java.nio.file.Files.exists(resolved)) {
                // If running from gateway/sentinel, relative path to velo-core is ../../../velo-core/target/debug/libvelo_core.dylib
                resolved = Path.of("").toAbsolutePath().resolve("../../../velo-core/target/debug/libvelo_core.dylib").normalize();
            }
            if (!java.nio.file.Files.exists(resolved)) {
                // If running from root of velo-sentinel, relative path to velo-core is ../velo-core/target/debug/libvelo_core.dylib
                resolved = Path.of("").toAbsolutePath().resolve("../velo-core/target/debug/libvelo_core.dylib").normalize();
            }
            path = resolved;
        }

        if (!java.nio.file.Files.exists(path)) {
            String errorMsg = "NATIVE-BRIDGE ERROR: Velo-Core library not found at resolved path: " + path.toAbsolutePath();
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        try {
            this.lookup = SymbolLookup.libraryLookup(path, Arena.global());
            log.info("NATIVE-BRIDGE: Library loaded successfully from {}", path.toAbsolutePath());
        } catch (Exception e) {
            log.error("NATIVE-BRIDGE: Failed to load library from {}.", path, e);
            throw new RuntimeException("Could not load Velo-Core native library at " + path.toAbsolutePath(), e);
        }
    }

    /**
     * Returns the native linker used for downcalls.
     *
     * @return The FFM Linker instance.
     */
    public Linker getLinker() {
        return linker;
    }

    /**
     * Returns the symbol lookup for the loaded native library.
     *
     * @return The SymbolLookup containing native functions.
     */
    public SymbolLookup getLookup() {
        return lookup;
    }
}
