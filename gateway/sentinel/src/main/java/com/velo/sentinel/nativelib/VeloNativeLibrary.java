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
        try {
            this.lookup = SymbolLookup.libraryLookup(Path.of(libPath), Arena.global());
            log.info("NATIVE-BRIDGE: Library loaded successfully.");
        } catch (Exception e) {
            log.error("NATIVE-BRIDGE: Failed to load library from {}. Ensure the path is correct and the library exists.", libPath, e);
            throw new RuntimeException("Could not load Velo-Core native library", e);
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
