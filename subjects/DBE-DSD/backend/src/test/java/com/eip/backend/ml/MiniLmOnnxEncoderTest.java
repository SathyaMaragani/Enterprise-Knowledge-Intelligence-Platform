package com.eip.backend.ml;

import ai.onnxruntime.OrtException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MiniLmOnnxEncoderTest {

    private static MiniLmOnnxEncoder encoder;

    @BeforeAll
    static void setUp() throws OrtException, IOException {
        // Adjust paths to resolve correctly from the backend module directory
        Path modelPath = Paths.get("../../../models/minilm/onnx/model.onnx");
        Path tokenizerPath = Paths.get("../../../models/minilm/tokenizer.json");
        encoder = new MiniLmOnnxEncoder(modelPath, tokenizerPath);
    }

    @AfterAll
    static void tearDown() {
        if (encoder != null) {
            encoder.close();
        }
    }

    @Test
    void testOutputDimension() {
        float[] vector = encoder.encode("test string");
        assertNotNull(vector);
        assertEquals(384, vector.length, "Output dimension must be exactly 384");
    }

    @Test
    void testAllValuesAreFinite() {
        float[] vector = encoder.encode("another test string");
        for (float val : vector) {
            assertTrue(Float.isFinite(val), "Vector value must be finite");
        }
    }

    @Test
    void testL2NormIsApproximatelyOne() {
        float[] vector = encoder.encode("a long string that we encode");
        float norm = 0.0f;
        for (float v : vector) {
            norm += v * v;
        }
        // Precision might not be perfectly 1.0f due to float math, so use delta
        assertEquals(1.0f, norm, 1e-4, "L2 norm must be approximately 1.0");
    }

    @Test
    void testDeterminism() {
        String input = "The quick brown fox jumps over the lazy dog.";
        float[] vector1 = encoder.encode(input);
        float[] vector2 = encoder.encode(input);
        assertArrayEquals(vector1, vector2, 1e-6f, "Repeated encoding of identical input must be deterministic");
    }

    @Test
    void testEmptyAndNullInput() {
        float[] emptyVec = encoder.encode("");
        float[] nullVec = encoder.encode(null);
        
        assertEquals(384, emptyVec.length);
        assertEquals(384, nullVec.length);
        
        // Empty and null should produce identical vectors
        assertArrayEquals(emptyVec, nullVec, 1e-6f);
    }
    
    @Test
    void testLongInputTruncation() {
        // Create an input longer than typical 256 or 512 max length
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("word ");
        }
        // This should not throw an exception, the tokenizer or model should handle/truncate it.
        float[] vector = encoder.encode(sb.toString());
        assertEquals(384, vector.length);
    }
}
