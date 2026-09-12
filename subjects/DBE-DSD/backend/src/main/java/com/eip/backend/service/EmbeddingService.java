package com.eip.backend.service;

import com.eip.backend.ml.MiniLmOnnxEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    private final MiniLmOnnxEncoder encoder;

    public EmbeddingService(@Autowired(required = false) MiniLmOnnxEncoder encoder) {
        this.encoder = encoder;
    }

    public List<Float> embedQuery(String query) {
        if (encoder == null || query == null || query.trim().isEmpty()) {
            return null;
        }
        float[] encoded = encoder.encode(query);
        List<Float> list = new ArrayList<>(encoded.length);
        for (float f : encoded) {
            list.add(f);
        }
        return list;
    }
}
