package com.velo.sentinel.nativelib;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * VeloCoreBridge: The "Hardware-Aware" Native Bridge.
 * 
 * Uses Java 25 Foreign Function &amp; Memory (FFM) API to orchestrate 
 * the Velo-Core Rust engine via zero-copy memory segments.
 */
public class VeloCoreBridge implements AutoCloseable {

    // Function Descriptors
    private static final FunctionDescriptor DESC_NEW = FunctionDescriptor.of(
            ValueLayout.ADDRESS, // Returns engine handle
            ValueLayout.ADDRESS, // model_name
            ValueLayout.JAVA_LONG, // max_slots
            ValueLayout.JAVA_LONG  // max_context_tokens
    );

    private static final FunctionDescriptor DESC_FREE = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS // engine handle
    );

    private static final FunctionDescriptor DESC_GENERATE = FunctionDescriptor.of(
            ValueLayout.ADDRESS, // Returns tokens pointer
            ValueLayout.ADDRESS, // engine handle
            ValueLayout.ADDRESS, // prompt_ptr
            ValueLayout.JAVA_LONG, // prompt_len
            ValueLayout.JAVA_LONG, // max_new_tokens
            ValueLayout.ADDRESS  // output_len_ptr
    );

    private static final FunctionDescriptor DESC_FREE_TOKENS = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, // tokens pointer
            ValueLayout.JAVA_LONG  // len
    );

    private final MethodHandle veloCoreEngineNew;
    private final MethodHandle veloCoreEngineFree;
    private final MethodHandle veloCoreEngineGenerate;
    private final MethodHandle veloCoreFreeTokens;

    private final MemorySegment engineHandle;
    private final Arena arena;

    /**
     * Initializes the bridge and loads the native engine into memory.
     * 
     * @param nativeLib The library loader service.
     * @param modelName The name of the model to load.
     * @param maxSlots Maximum number of concurrent inference slots.
     * @param maxContextTokens Maximum context length per slot.
     */
    public VeloCoreBridge(VeloNativeLibrary nativeLib, String modelName, long maxSlots, long maxContextTokens) {
        this.arena = Arena.ofShared();
        
        SymbolLookup lookup = nativeLib.getLookup();
        Linker linker = nativeLib.getLinker();

        this.veloCoreEngineNew = lookup.find("velo_core_engine_new").map(s -> linker.downcallHandle(s, DESC_NEW)).orElseThrow();
        this.veloCoreEngineFree = lookup.find("velo_core_engine_free").map(s -> linker.downcallHandle(s, DESC_FREE)).orElseThrow();
        this.veloCoreEngineGenerate = lookup.find("velo_core_engine_generate").map(s -> linker.downcallHandle(s, DESC_GENERATE)).orElseThrow();
        this.veloCoreFreeTokens = lookup.find("velo_core_free_tokens").map(s -> linker.downcallHandle(s, DESC_FREE_TOKENS)).orElseThrow();

        try {
            MemorySegment cModelName = arena.allocateFrom(modelName);
            this.engineHandle = (MemorySegment) veloCoreEngineNew.invokeExact(cModelName, maxSlots, maxContextTokens);
            
            if (engineHandle.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to initialize Velo-Core native engine");
            }
        } catch (Throwable t) {
            throw new RuntimeException("Native bridge initialization failed", t);
        }
    }

    /**
     * Executes native inference via zero-copy memory segments.
     * 
     * @param prompt The list of input token IDs.
     * @param maxNewTokens The maximum number of tokens to generate.
     * @return A list of generated token IDs.
     */
    public List<Integer> generate(List<Integer> prompt, int maxNewTokens) {
        try {
            MemorySegment cPrompt = arena.allocateFrom(ValueLayout.JAVA_INT, prompt.stream().mapToInt(i -> i).toArray());
            MemorySegment cOutputLen = arena.allocate(ValueLayout.JAVA_LONG);

            MemorySegment tokensPtr = (MemorySegment) veloCoreEngineGenerate.invokeExact(
                    engineHandle,
                    cPrompt,
                    (long) prompt.size(),
                    (long) maxNewTokens,
                    cOutputLen
            );

            if (tokensPtr.equals(MemorySegment.NULL)) {
                return List.of();
            }

            long outputLen = cOutputLen.get(ValueLayout.JAVA_LONG, 0);
            
            // Map the native memory to a segment for zero-copy access
            MemorySegment resultSegment = tokensPtr.reinterpret(outputLen * 4);
            int[] resultArr = resultSegment.toArray(ValueLayout.JAVA_INT);
            
            List<Integer> resultList = new ArrayList<>();
            for (int val : resultArr) {
                resultList.add(val);
            }

            // Tell Rust to free the buffer
            veloCoreFreeTokens.invokeExact(tokensPtr, outputLen);

            return resultList;
        } catch (Throwable t) {
            throw new RuntimeException("Native generation failed", t);
        }
    }

    @Override
    public void close() {
        try {
            if (!engineHandle.equals(MemorySegment.NULL)) {
                veloCoreEngineFree.invokeExact(engineHandle);
            }
        } catch (Throwable t) {
            // Log error
        } finally {
            arena.close();
        }
    }
}
