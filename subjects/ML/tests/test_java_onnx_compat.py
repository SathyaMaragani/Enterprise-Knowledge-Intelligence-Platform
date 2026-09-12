import os
import sys
import subprocess
import numpy as np
from src.embeddings.encoder import Encoder, MINILM

def get_java_vector(text: str) -> np.ndarray:
    # Run maven command from subjects/DBE-DSD/backend
    # We will encode text as base64 to avoid CLI escaping issues
    import base64
    b64_text = base64.b64encode(text.encode('utf-8')).decode('utf-8')
    
    backend_dir = os.path.join(os.path.dirname(__file__), '..', '..', '..', 'subjects', 'DBE-DSD', 'backend')
    cmd = [
        "mvnw.cmd", "test-compile", "exec:java",
        "-q",
        "-Dexec.mainClass=com.eip.backend.ml.EncodeCli",
        "-Dexec.classpathScope=test",
        f"-Dexec.args={b64_text}"
    ]
    
    try:
        result = subprocess.run(cmd, cwd=backend_dir, capture_output=True, text=True, check=True, shell=True)
    except subprocess.CalledProcessError as e:
        print("Java execution failed:", e.stderr)
        raise e
        
    output = result.stdout.strip()
    
    # Extract the last line which should be the comma-separated vector
    # because Maven might output some other INFO logs even with -q
    lines = output.split('\n')
    vector_str = ""
    for line in reversed(lines):
        if ',' in line:
            vector_str = line
            break
            
    if not vector_str:
        raise ValueError(f"Could not find vector in Java output:\n{output}")
        
    vector = np.array([float(x) for x in vector_str.split(',')], dtype=np.float32)
    return vector

def test_compatibility():
    print("Initializing Python Encoder...")
    encoder = Encoder(MINILM)
    
    test_cases = [
        "hello world",  # short query
        "This is a multi-sentence query. It has more than one sentence.",  # multi-sentence
        "short",  # requires padding
        "enterprise " * 100,  # long query approaching limit
        "What are the integration steps for PostgreSQL and MongoDB in the Enterprise Knowledge platform?"  # enterprise-style
    ]
    
    for i, text in enumerate(test_cases):
        print(f"\n--- Test Case {i+1} ---")
        print(f"Text (trunc): {text[:50]}...")
        
        py_vector, _ = encoder.encode_queries([text])
        py_vector = py_vector[0]
        java_vector = get_java_vector(text)
        
        if py_vector.shape != (384,) or java_vector.shape != (384,):
            print(f"Shape mismatch: Py {py_vector.shape} vs Java {java_vector.shape}")
            sys.exit(1)
            
        # Cosine similarity
        dot = np.dot(py_vector, java_vector)
        norm_py = np.linalg.norm(py_vector)
        norm_java = np.linalg.norm(java_vector)
        cos_sim = dot / (norm_py * norm_java)
        
        # Absolute difference
        diff = np.abs(py_vector - java_vector)
        max_diff = np.max(diff)
        mean_diff = np.mean(diff)
        
        print(f"Cosine Similarity: {cos_sim:.6f}")
        print(f"Max Abs Diff: {max_diff:.8f}")
        print(f"Mean Abs Diff: {mean_diff:.8f}")
        
        # We expect extreme similarity. 1e-5 is a reasonable tolerance due to ONNX vs PyTorch FP32 math differences
        if cos_sim < 0.9999 or max_diff > 1e-4:
            print("FAILED! Compatibility check failed! Tolerance exceeded.")
            sys.exit(1)
            
        print("PASSED! Compatibility check passed.")

if __name__ == "__main__":
    test_compatibility()
