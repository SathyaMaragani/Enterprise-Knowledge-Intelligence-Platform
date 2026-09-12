package com.eip.backend.ml;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates the execution of the all-MiniLM-L6-v2 ONNX model.
 * 
 * Performs:
 * 1. HuggingFace tokenization (producing input_ids, attention_mask)
 * 2. ONNX Inference (producing last_hidden_state)
 * 3. Attention-mask-aware mean pooling
 * 4. L2 Normalization
 */
public class MiniLmOnnxEncoder implements AutoCloseable {

    private final HuggingFaceTokenizer tokenizer;
    private final OrtEnvironment env;
    private final OrtSession session;
    private final int EXPECTED_DIMENSION = 384;

    public MiniLmOnnxEncoder(Path modelPath, Path tokenizerPath) throws OrtException, IOException {
        // Load the tokenizer
        Map<String, String> options = new HashMap<>();
        this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath, options);

        // Load the ONNX model
        this.env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        // Optimize for CPU execution in production
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        this.session = env.createSession(modelPath.toString(), sessionOptions);
    }

    /**
     * Encodes a single text into a 384-dimensional vector.
     * 
     * @param text The input text
     * @return 384-dimensional L2-normalized float array
     */
    public float[] encode(String text) {
        if (text == null) {
            text = "";
        }

        try {
            Encoding encoding = tokenizer.encode(text);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            long[] tokenTypeIds = encoding.getTypeIds(); // Some models use this, MiniLM accepts it

            // The tensors must be 2D: [batch_size, sequence_length]
            long[][] inputIds2D = new long[][]{inputIds};
            long[][] attentionMask2D = new long[][]{attentionMask};
            long[][] tokenTypeIds2D = new long[][]{tokenTypeIds};

            try (
                OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, inputIds2D);
                OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, attentionMask2D);
                OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(env, tokenTypeIds2D)
            ) {
                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put("input_ids", inputIdsTensor);
                inputs.put("attention_mask", attentionMaskTensor);
                inputs.put("token_type_ids", tokenTypeIdsTensor);

                try (OrtSession.Result results = session.run(inputs)) {
                    // Output is typically named "last_hidden_state" or is just the first output.
                    // Shape: [batch_size, sequence_length, dimension]
                    float[][][] hiddenState = (float[][][]) results.get(0).getValue();
                    
                    return meanPoolAndNormalize(hiddenState[0], attentionMask);
                }
            }
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Inference failed", e);
        }
    }

    /**
     * Performs attention-mask-aware mean pooling and L2 normalization.
     * 
     * @param hiddenState   Shape: [sequence_length, dimension]
     * @param attentionMask Shape: [sequence_length]
     * @return Normalized vector of size 384
     */
    private float[] meanPoolAndNormalize(float[][] hiddenState, long[] attentionMask) {
        int seqLength = hiddenState.length;
        int dim = EXPECTED_DIMENSION;
        float[] pooled = new float[dim];
        float maskSum = 0;

        for (int i = 0; i < seqLength; i++) {
            if (attentionMask[i] == 1) {
                for (int j = 0; j < dim; j++) {
                    pooled[j] += hiddenState[i][j];
                }
                maskSum += 1.0f;
            }
        }

        // Mean pool
        if (maskSum > 0) {
            for (int j = 0; j < dim; j++) {
                pooled[j] /= maskSum;
            }
        }

        // L2 Normalization
        float norm = 0.0f;
        for (int j = 0; j < dim; j++) {
            norm += pooled[j] * pooled[j];
        }
        
        // Prevent division by zero
        norm = (float) Math.max(Math.sqrt(norm), 1e-9);

        for (int j = 0; j < dim; j++) {
            pooled[j] /= norm;
        }

        return pooled;
    }

    @Override
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
            if (env != null) {
                env.close();
            }
            if (tokenizer != null) {
                tokenizer.close();
            }
        } catch (OrtException e) {
            // Log and ignore
        }
    }
}
