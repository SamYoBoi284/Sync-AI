# Native Memory + Context Shift Test Branch

Branch: `test/native-memory-context-tuning`

This branch is experimental and does not change `main` or existing release code.

## Test changes

1. **Batch prefill:** `n_batch = 32`, `n_ubatch = 32`. Prompt tokens are decoded in chunks, rather than sending the full prompt in one batch.
2. **CPU threads:** caps generation and batch threads at 4 (minimum 2).
3. **KV cache:** requests `GGML_TYPE_Q4_0` for both K and V. This is experimental; some model architectures/configurations may reject it or fail context creation.
4. **Model loading:** uses `LLAMA_LOAD_MODE_NONE` to test loading without the mmap/mlock special modes. This is not guaranteed to lower RAM use; compare with `LLAMA_LOAD_MODE_AUTO` if loading becomes worse.
5. **Context shifting:** when generation fills the context, removes the oldest half of the KV sequence and shifts remaining KV positions left. If an overlong submitted prompt already exceeds the window, the runtime keeps its newest `n_ctx - 1` tokens before prefill.

## Important limitations

- Generic prompt truncation can discard system instructions and the beginning of the conversation. This is a basic stability test, not the final conversation-retention policy.
- Context shifting depends on the selected model memory implementation supporting position shifts. The runtime returns a clear error if it cannot shift.
- Q4_0 KV cache support is experimental and model/backend dependent.
- `LLAMA_LOAD_MODE_NONE` may increase or decrease observed memory pressure depending on OS, file cache, and llama.cpp behavior. Do not assume it is better than mmap.
- The llama.cpp dependency is pinned to commit `7894e8b92481a8bb8ffe94f73b656ffe0dd01e10` so this branch's API and defaults are reproducible.
- This source change has not been compiled or exercised on-device by the GitHub edit itself. Build the debug APK and verify compilation before treating the branch as test-ready.

## Suggested device test

For each model, record model name/quantization, GGUF file size, whether loading succeeds, whether the first response completes, any crash/error, rough time to load/first token, and Android memory/LMK behavior. Start with the known-working 0.5B–1B model, then try the larger model. A model's parameter count alone does not determine fit: quantization, tensor size, context, and available RAM matter.
