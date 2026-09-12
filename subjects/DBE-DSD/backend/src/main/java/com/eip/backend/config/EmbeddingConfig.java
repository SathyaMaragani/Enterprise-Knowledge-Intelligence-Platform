package com.eip.backend.config;

import com.eip.backend.ml.MiniLmOnnxEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class EmbeddingConfig {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingConfig.class);

    @Value("${embedding.model-path:models/minilm/onnx/model.onnx}")
    private String modelPath;

    @Value("${embedding.tokenizer-path:models/minilm/tokenizer.json}")
    private String tokenizerPath;

    @Value("${embedding.enabled:false}")
    private boolean enabled;

    @Bean
    public MiniLmOnnxEncoder miniLmOnnxEncoder() throws Exception {
        if (!enabled) {
            logger.info("Embedding is disabled. MiniLmOnnxEncoder will not be loaded.");
            return null;
        }
        Path model = Paths.get(modelPath);
        Path tokenizer = Paths.get(tokenizerPath);
        logger.info("Loading MiniLmOnnxEncoder from {} and {}", modelPath, tokenizerPath);
        return new MiniLmOnnxEncoder(model, tokenizer);
    }
}
