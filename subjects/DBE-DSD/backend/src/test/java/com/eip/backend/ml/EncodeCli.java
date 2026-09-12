package com.eip.backend.ml;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public class EncodeCli {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: EncodeCli <text>");
            System.exit(1);
        }
        
        String text = new String(Base64.getDecoder().decode(args[0]), "UTF-8");
        Path modelPath = Paths.get("../../../models/minilm/onnx/model.onnx");
        Path tokenizerPath = Paths.get("../../../models/minilm/tokenizer.json");
        
        try (MiniLmOnnxEncoder encoder = new MiniLmOnnxEncoder(modelPath, tokenizerPath)) {
            float[] vector = encoder.encode(text);
            
            // Print vector to stdout as comma-separated values
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < vector.length; i++) {
                sb.append(vector[i]);
                if (i < vector.length - 1) {
                    sb.append(",");
                }
            }
            System.out.println(sb.toString());
        }
    }
}
